package com.company.procurement.purchaseorder.services;

import com.company.procurement.approval.domain.ApprovalPolicy;
import com.company.procurement.approval.domain.ApproverType;
import com.company.procurement.approval.repositories.ApprovalPolicyRepository;
import com.company.procurement.approval.services.ApprovalChainResolver;
import com.company.procurement.approval.services.ResolvedChain;
import com.company.procurement.audit.services.AuditEventService;
import com.company.procurement.organization.domain.UserProfile;
import com.company.procurement.organization.services.UserProfileLookupService;
import com.company.procurement.purchaseorder.domain.PurchaseOrder;
import com.company.procurement.purchaseorder.domain.PurchaseOrderApproval;
import com.company.procurement.purchaseorder.domain.PurchaseOrderApprovalStep;
import com.company.procurement.purchaseorder.domain.PurchaseOrderStatus;
import com.company.procurement.purchaseorder.repositories.PurchaseOrderApprovalRepository;
import com.company.procurement.purchaseorder.repositories.PurchaseOrderApprovalStepRepository;
import com.company.procurement.purchaseorder.repositories.PurchaseOrderRepository;
import com.company.procurement.request.services.PurchaseRequestService;
import com.company.procurement.shared.error.BusinessRuleException;
import com.company.procurement.shared.error.ConflictException;
import com.company.procurement.shared.error.ResourceNotFoundException;
import com.company.procurement.shared.outbox.OutboxEventTypes;
import com.company.procurement.shared.security.CurrentUser;
import com.company.procurement.shared.services.OutboxService;
import com.company.procurement.shared.services.PermissionService;
import com.company.procurement.shared.tenant.TenantContext;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Coordinates purchase-order approval steps and decisions. */
@Service
public class PurchaseOrderApprovalService {

  private static final String PENDING = "PENDING";
  private static final String APPROVED = "APPROVED";
  private static final String SKIPPED = "SKIPPED";

  private final PurchaseOrderApprovalRepository approvals;
  private final PurchaseOrderApprovalStepRepository steps;
  private final PurchaseOrderRepository orders;
  private final ApprovalPolicyRepository policies;
  private final ApprovalChainResolver resolver;
  private final UserProfileLookupService userProfiles;
  private final PurchaseRequestService requests;
  private final CurrentUser currentUser;
  private final PermissionService permissions;
  private final AuditEventService audit;
  private final OutboxService outbox;
  private final Clock clock;

  public PurchaseOrderApprovalService(
      PurchaseOrderApprovalRepository approvals,
      PurchaseOrderApprovalStepRepository steps,
      PurchaseOrderRepository orders,
      ApprovalPolicyRepository policies,
      ApprovalChainResolver resolver,
      UserProfileLookupService userProfiles,
      PurchaseRequestService requests,
      CurrentUser currentUser,
      PermissionService permissions,
      AuditEventService audit,
      OutboxService outbox,
      Clock clock) {
    this.approvals = approvals;
    this.steps = steps;
    this.orders = orders;
    this.policies = policies;
    this.resolver = resolver;
    this.userProfiles = userProfiles;
    this.requests = requests;
    this.currentUser = currentUser;
    this.permissions = permissions;
    this.audit = audit;
    this.outbox = outbox;
    this.clock = clock;
  }

  @Transactional
  public PurchaseOrder startIfConfigured(PurchaseOrder order) {
    UUID organizationId = TenantContext.require();
    if (policies
        .findActiveByAmount(organizationId, "PURCHASE_ORDER", order.getTotalAmount())
        .isEmpty()) {
      return order;
    }
    start(organizationId, order);
    order.submitForApproval();
    PurchaseOrder saved = orders.save(order);
    audit.recordCurrentActor(
        "purchase_order", saved.getId(), "purchase_order_submitted_for_approval", Map.of());
    return saved;
  }

  @Transactional
  public ApprovalView submit(UUID purchaseOrderId) {
    UUID organizationId = TenantContext.require();
    PurchaseOrder order = requireOrder(organizationId, purchaseOrderId);
    if (order.getStatus() != PurchaseOrderStatus.DRAFT) {
      throw new BusinessRuleException("Only draft purchase orders can be submitted for approval");
    }
    Optional<ApprovalPolicy> policy =
        policies.findActiveByAmount(organizationId, "PURCHASE_ORDER", order.getTotalAmount());
    if (policy.isEmpty()) {
      throw new BusinessRuleException(
          "No active purchase-order approval policy covers this amount");
    }
    resetExistingApproval(organizationId, purchaseOrderId);
    start(organizationId, order);
    order.submitForApproval();
    orders.save(order);
    audit.recordCurrentActor(
        "purchase_order", purchaseOrderId, "purchase_order_submitted_for_approval", Map.of());
    return view(organizationId, purchaseOrderId);
  }

  @Transactional
  public ApprovalView approve(UUID purchaseOrderId, String comment) {
    UUID organizationId = TenantContext.require();
    PurchaseOrder order = requireOrder(organizationId, purchaseOrderId);
    ApprovalStage stage = requirePendingApproval(organizationId, purchaseOrderId);
    PurchaseOrderApprovalStep step = currentStep(stage, order);
    Instant now = clock.instant();
    step.approve(blankToNull(comment), currentUser.subject(), now);

    List<PurchaseOrderApprovalStep> currentSteps =
        stage.steps().stream().filter(value -> value.getSequence() == step.getSequence()).toList();
    int required =
        currentSteps.stream().mapToInt(PurchaseOrderApprovalStep::getMinApprovals).max().orElse(1);
    int eligible =
        (int) currentSteps.stream().filter(value -> !SKIPPED.equals(value.getStatus())).count();
    int approved =
        (int) currentSteps.stream().filter(value -> APPROVED.equals(value.getStatus())).count();
    if (approved >= Math.min(required, eligible)) {
      currentSteps.stream()
          .filter(value -> PENDING.equals(value.getStatus()))
          .forEach(value -> value.skip("Quorum met", "system", now));
    }
    steps.saveAll(currentSteps);

    boolean pending = currentSteps.stream().anyMatch(value -> PENDING.equals(value.getStatus()));
    if (pending) {
      audit.recordCurrentActor(
          "purchase_order",
          purchaseOrderId,
          "purchase_order_approval_step_approved",
          Map.of("sequence", step.getSequence(), "comment", comment == null ? "" : comment));
    } else {
      Integer next =
          stage.steps().stream()
              .filter(value -> PENDING.equals(value.getStatus()))
              .filter(value -> value.getSequence() > step.getSequence())
              .map(PurchaseOrderApprovalStep::getSequence)
              .min(Integer::compareTo)
              .orElse(null);
      if (next == null) {
        PurchaseOrderApproval approval = stage.approval();
        approval.approve();
        approvals.save(approval);
        order.approveForIssue();
        orders.save(order);
        audit.recordCurrentActor(
            "purchase_order", purchaseOrderId, "purchase_order_approval_approved", Map.of());
        outbox.enqueue(
            OutboxEventTypes.PURCHASE_ORDER_APPROVED,
            "purchase_order",
            purchaseOrderId,
            Map.of(
                "poId", purchaseOrderId.toString(),
                "poNumber", order.getPoNumber()));
      } else {
        PurchaseOrderApproval approval = stage.approval();
        approval.moveToSequence(next);
        approvals.save(approval);
        audit.recordCurrentActor(
            "purchase_order",
            purchaseOrderId,
            "purchase_order_approval_step_approved",
            Map.of("sequence", step.getSequence(), "comment", comment == null ? "" : comment));
      }
    }
    return view(organizationId, purchaseOrderId);
  }

  @Transactional
  public ApprovalView reject(UUID purchaseOrderId, String comment) {
    UUID organizationId = TenantContext.require();
    PurchaseOrder order = requireOrder(organizationId, purchaseOrderId);
    ApprovalStage stage = requirePendingApproval(organizationId, purchaseOrderId);
    PurchaseOrderApprovalStep step = currentStep(stage, order);
    Instant now = clock.instant();
    step.reject(blankToNull(comment), currentUser.subject(), now);
    steps.save(step);
    PurchaseOrderApproval approval = stage.approval();
    approval.reject();
    approvals.save(approval);
    order.rejectApproval();
    orders.save(order);
    requests.reopenAfterPurchaseOrderCancellation(order.getPurchaseRequestId());
    audit.recordCurrentActor(
        "purchase_order",
        purchaseOrderId,
        "purchase_order_approval_rejected",
        Map.of("comment", comment == null ? "" : comment));
    return view(organizationId, purchaseOrderId);
  }

  @Transactional
  public ApprovalView requestChanges(UUID purchaseOrderId, String comment) {
    UUID organizationId = TenantContext.require();
    PurchaseOrder order = requireOrder(organizationId, purchaseOrderId);
    ApprovalStage stage = requirePendingApproval(organizationId, purchaseOrderId);
    PurchaseOrderApprovalStep step = currentStep(stage, order);
    Instant now = clock.instant();
    step.skip(blankToNull(comment), currentUser.subject(), now);
    steps.save(step);
    PurchaseOrderApproval approval = stage.approval();
    approval.requestChanges();
    approvals.save(approval);
    order.requestApprovalChanges();
    orders.save(order);
    audit.recordCurrentActor(
        "purchase_order",
        purchaseOrderId,
        "purchase_order_changes_requested",
        Map.of("comment", comment == null ? "" : comment));
    return view(organizationId, purchaseOrderId);
  }

  @Transactional(readOnly = true)
  public ApprovalView current(UUID purchaseOrderId) {
    return view(TenantContext.require(), purchaseOrderId);
  }

  @Transactional
  public void cancelForPurchaseOrder(UUID purchaseOrderId) {
    UUID organizationId = TenantContext.require();
    approvals
        .findByOrganizationIdAndPurchaseOrderId(organizationId, purchaseOrderId)
        .filter(approval -> PENDING.equals(approval.getStatus()))
        .ifPresent(
            approval -> {
              approval.cancel();
              approvals.save(approval);
            });
  }

  @Transactional(readOnly = true)
  public void requireIssueAllowed(UUID purchaseOrderId, PurchaseOrder order) {
    UUID organizationId = TenantContext.require();
    Optional<PurchaseOrderApproval> approval =
        approvals.findByOrganizationIdAndPurchaseOrderId(organizationId, purchaseOrderId);
    if (approval.isPresent()) {
      if (!APPROVED.equals(approval.get().getStatus())
          || order.getStatus() != PurchaseOrderStatus.APPROVED) {
        throw new BusinessRuleException(
            "Purchase order must complete approval before it can be issued");
      }
      return;
    }
    if (policies
        .findActiveByAmount(organizationId, "PURCHASE_ORDER", order.getTotalAmount())
        .isPresent()) {
      throw new BusinessRuleException(
          "Purchase order requires approval; submit it for approval before issuing");
    }
  }

  private void start(UUID organizationId, PurchaseOrder order) {
    UserProfile requester = userProfiles.findBySubject(order.getRequesterSubject()).orElse(null);
    ResolvedChain chain =
        resolver.resolve(
            organizationId,
            "PURCHASE_ORDER",
            order.getTotalAmount(),
            requester == null ? null : requester.getId(),
            requester == null ? null : requester.getManagerId(),
            false,
            requests.departmentIdForPurchaseRequest(order.getPurchaseRequestId()));
    Instant now = clock.instant();
    PurchaseOrderApproval approval =
        approvals.save(
            new PurchaseOrderApproval(
                organizationId,
                order.getId(),
                chain.policyId(),
                chain.policyVersion(),
                chain.policyName(),
                chain.steps().get(0).stage()));
    steps.saveAll(
        chain.steps().stream()
            .map(
                step ->
                    new PurchaseOrderApprovalStep(
                        organizationId,
                        approval.getId(),
                        step.stage(),
                        step.name(),
                        step.approverType().name(),
                        step.approverRole(),
                        step.assigneeUserId(),
                        now.plus(Duration.ofHours(step.slaHours())),
                        step.minApprovals()))
            .toList());
  }

  private void resetExistingApproval(UUID organizationId, UUID purchaseOrderId) {
    approvals
        .findByOrganizationIdAndPurchaseOrderId(organizationId, purchaseOrderId)
        .ifPresent(
            approval -> {
              if (PENDING.equals(approval.getStatus())) {
                throw new ConflictException("A purchase-order approval is already pending");
              }
              steps.deleteByApprovalId(approval.getId());
              approvals.deleteByPurchaseOrder(organizationId, purchaseOrderId);
            });
  }

  private boolean isEligible(PurchaseOrder order, PurchaseOrderApprovalStep step) {
    if (order.getRequesterSubject().equals(currentUser.subject())) {
      return false;
    }
    UserProfile actor = userProfiles.findBySubject(currentUser.subject()).orElse(null);
    if (actor != null
        && actor.getApprovalLimitAmount() != null
        && order.getTotalAmount().compareTo(actor.getApprovalLimitAmount()) > 0) {
      return false;
    }
    if (ApproverType.ROLE.name().equals(step.getApproverType())) {
      return step.getApproverRole() != null
          && permissions.has("approval:act:" + step.getApproverRole());
    }
    if (ApproverType.USER.name().equals(step.getApproverType())
        || ApproverType.REQUESTER_MANAGER.name().equals(step.getApproverType())) {
      return actor != null && actor.getId().equals(step.getAssigneeUserId());
    }
    return false;
  }

  private PurchaseOrder requireOrder(UUID organizationId, UUID purchaseOrderId) {
    return orders
        .findByIdAndOrganizationId(purchaseOrderId, organizationId)
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    "Purchase order %s not found".formatted(purchaseOrderId)));
  }

  private ApprovalStage requirePendingApproval(UUID organizationId, UUID purchaseOrderId) {
    PurchaseOrderApproval approval =
        approvals
            .findByOrganizationIdAndPurchaseOrderId(organizationId, purchaseOrderId)
            .orElseThrow(
                () -> new BusinessRuleException("Purchase order has no approval workflow"));
    if (!PENDING.equals(approval.getStatus())) {
      throw new BusinessRuleException("Purchase-order approval is not pending");
    }
    return new ApprovalStage(approval, steps.findByApprovalId(approval.getId()));
  }

  private PurchaseOrderApprovalStep currentStep(ApprovalStage stage, PurchaseOrder order) {
    return stage.steps().stream()
        .filter(step -> step.getSequence() == stage.approval().getCurrentSequence())
        .filter(step -> PENDING.equals(step.getStatus()))
        .filter(step -> isEligible(order, step))
        .findFirst()
        .orElseThrow(
            () ->
                new BusinessRuleException(
                    "You are not an eligible approver for the current purchase-order stage"));
  }

  private ApprovalView view(UUID organizationId, UUID purchaseOrderId) {
    PurchaseOrderApproval approval =
        approvals
            .findByOrganizationIdAndPurchaseOrderId(organizationId, purchaseOrderId)
            .orElseThrow(() -> new ResourceNotFoundException("Approval not found"));
    List<StepView> stepViews =
        steps.findByApprovalId(approval.getId()).stream()
            .map(
                step ->
                    new StepView(
                        step.getId(),
                        step.getSequence(),
                        step.getName(),
                        step.getApproverType(),
                        step.getApproverRole(),
                        step.getAssigneeUserId(),
                        step.getStatus(),
                        step.getComment(),
                        step.getActedBy(),
                        step.getActedAt(),
                        step.getSlaDueAt()))
            .toList();
    return new ApprovalView(
        approval.getId(), approval.getStatus(), approval.getCurrentSequence(), stepViews);
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private record ApprovalStage(
      PurchaseOrderApproval approval, List<PurchaseOrderApprovalStep> steps) {}

  public record ApprovalView(UUID id, String status, int currentSequence, List<StepView> steps) {}

  public record StepView(
      UUID id,
      int sequence,
      String name,
      String approverType,
      String approverRole,
      UUID assigneeUserId,
      String status,
      String comment,
      String actedBy,
      Instant actedAt,
      Instant slaDueAt) {}
}
