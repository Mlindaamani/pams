package com.company.procurement.purchaseorder.application;

import com.company.procurement.approval.application.ApprovalChainResolver;
import com.company.procurement.approval.application.ResolvedChain;
import com.company.procurement.approval.domain.ApprovalPolicy;
import com.company.procurement.approval.domain.ApprovalPolicyRepository;
import com.company.procurement.approval.domain.ApprovalStepDraft;
import com.company.procurement.approval.domain.ApproverType;
import com.company.procurement.audit.application.AuditEventService;
import com.company.procurement.organization.application.UserProfileLookupService;
import com.company.procurement.organization.domain.UserProfile;
import com.company.procurement.purchaseorder.domain.PurchaseOrder;
import com.company.procurement.purchaseorder.domain.PurchaseOrderRepository;
import com.company.procurement.purchaseorder.domain.PurchaseOrderStatus;
import com.company.procurement.request.application.PurchaseRequestService;
import com.company.procurement.shared.authorization.PermissionService;
import com.company.procurement.shared.error.BusinessRuleException;
import com.company.procurement.shared.error.ConflictException;
import com.company.procurement.shared.error.ResourceNotFoundException;
import com.company.procurement.shared.security.CurrentUser;
import com.company.procurement.shared.tenant.TenantContext;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PurchaseOrderApprovalService {

  private static final String PENDING = "PENDING";
  private static final String APPROVED = "APPROVED";
  private static final String REJECTED = "REJECTED";
  private static final String CHANGES_REQUESTED = "CHANGES_REQUESTED";
  private static final String CANCELED = "CANCELED";

  private final JdbcTemplate jdbc;
  private final PurchaseOrderRepository orders;
  private final ApprovalPolicyRepository policies;
  private final ApprovalChainResolver resolver;
  private final UserProfileLookupService userProfiles;
  private final PurchaseRequestService requests;
  private final CurrentUser currentUser;
  private final PermissionService permissions;
  private final AuditEventService audit;
  private final Clock clock;

  public PurchaseOrderApprovalService(
      JdbcTemplate jdbc,
      PurchaseOrderRepository orders,
      ApprovalPolicyRepository policies,
      ApprovalChainResolver resolver,
      UserProfileLookupService userProfiles,
      PurchaseRequestService requests,
      CurrentUser currentUser,
      PermissionService permissions,
      AuditEventService audit,
      Clock clock) {
    this.jdbc = jdbc;
    this.orders = orders;
    this.policies = policies;
    this.resolver = resolver;
    this.userProfiles = userProfiles;
    this.requests = requests;
    this.currentUser = currentUser;
    this.permissions = permissions;
    this.audit = audit;
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
    ApprovalRow approval = requirePendingApproval(organizationId, purchaseOrderId);
    StepRow step = currentStep(approval, order);
    Instant now = clock.instant();
    jdbc.update(
        "update purchase_order_approval_step set status = 'APPROVED', comment = ?, acted_by = ?, acted_at = ?, updated_at = now(), updated_by = ? where id = ?",
        blankToNull(comment),
        currentUser.subject(),
        Timestamp.from(now),
        currentUser.username(),
        step.id());
    if (hasPendingAt(approval.id(), step.sequence())) {
      audit.recordCurrentActor(
          "purchase_order",
          purchaseOrderId,
          "purchase_order_approval_step_approved",
          Map.of("sequence", step.sequence()));
    } else {
      Integer next = nextPendingSequence(approval.id(), step.sequence());
      if (next == null) {
        jdbc.update(
            "update purchase_order_approval set status = 'APPROVED', updated_at = now(), updated_by = ?, version = version + 1 where id = ?",
            currentUser.username(),
            approval.id());
        order.approveForIssue();
        orders.save(order);
        audit.recordCurrentActor(
            "purchase_order", purchaseOrderId, "purchase_order_approval_approved", Map.of());
      } else {
        jdbc.update(
            "update purchase_order_approval set current_sequence = ?, updated_at = now(), updated_by = ?, version = version + 1 where id = ?",
            next,
            currentUser.username(),
            approval.id());
        audit.recordCurrentActor(
            "purchase_order",
            purchaseOrderId,
            "purchase_order_approval_step_approved",
            Map.of("sequence", step.sequence()));
      }
    }
    return view(organizationId, purchaseOrderId);
  }

  @Transactional
  public ApprovalView reject(UUID purchaseOrderId, String comment) {
    UUID organizationId = TenantContext.require();
    PurchaseOrder order = requireOrder(organizationId, purchaseOrderId);
    ApprovalRow approval = requirePendingApproval(organizationId, purchaseOrderId);
    StepRow step = currentStep(approval, order);
    Instant now = clock.instant();
    jdbc.update(
        "update purchase_order_approval_step set status = 'REJECTED', comment = ?, acted_by = ?, acted_at = ?, updated_at = now(), updated_by = ? where id = ?",
        blankToNull(comment),
        currentUser.subject(),
        Timestamp.from(now),
        currentUser.username(),
        step.id());
    jdbc.update(
        "update purchase_order_approval set status = 'REJECTED', updated_at = now(), updated_by = ?, version = version + 1 where id = ?",
        currentUser.username(),
        approval.id());
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
    ApprovalRow approval = requirePendingApproval(organizationId, purchaseOrderId);
    StepRow step = currentStep(approval, order);
    Instant now = clock.instant();
    jdbc.update(
        "update purchase_order_approval_step set status = 'SKIPPED', comment = ?, acted_by = ?, acted_at = ?, updated_at = now(), updated_by = ? where id = ?",
        blankToNull(comment),
        currentUser.subject(),
        Timestamp.from(now),
        currentUser.username(),
        step.id());
    jdbc.update(
        "update purchase_order_approval set status = 'CHANGES_REQUESTED', updated_at = now(), updated_by = ?, version = version + 1 where id = ?",
        currentUser.username(),
        approval.id());
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
    jdbc.update(
        "update purchase_order_approval set status = 'CANCELED', updated_at = now(), updated_by = ?, version = version + 1 where organization_id = ? and purchase_order_id = ? and status = 'PENDING'",
        currentUser.username(),
        organizationId,
        purchaseOrderId);
  }

  @Transactional(readOnly = true)
  public void requireIssueAllowed(UUID purchaseOrderId, PurchaseOrder order) {
    UUID organizationId = TenantContext.require();
    Optional<ApprovalRow> approval = approvalRow(organizationId, purchaseOrderId);
    if (approval.isPresent()) {
      if (!APPROVED.equals(approval.get().status())
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
    UUID approvalId = UUID.randomUUID();
    jdbc.update(
        """
        insert into purchase_order_approval
            (id, organization_id, purchase_order_id, policy_id, policy_version, policy_name,
             status, current_sequence, created_at, created_by, updated_at, updated_by)
        values (?, ?, ?, ?, ?, ?, 'PENDING', ?, ?, ?, ?, ?)
        """,
        approvalId,
        organizationId,
        order.getId(),
        chain.policyId(),
        chain.policyVersion(),
        chain.policyName(),
        chain.steps().get(0).stage(),
        Timestamp.from(now),
        currentUser.username(),
        Timestamp.from(now),
        currentUser.username());
    for (ApprovalStepDraft step : chain.steps()) {
      jdbc.update(
          """
          insert into purchase_order_approval_step
              (id, organization_id, approval_id, sequence, name, approver_type, approver_role,
               assignee_user_id, status, sla_due_at, created_at, created_by, updated_at, updated_by)
          values (?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', ?, ?, ?, ?, ?)
          """,
          UUID.randomUUID(),
          organizationId,
          approvalId,
          step.stage(),
          step.name(),
          step.approverType().name(),
          step.approverRole(),
          step.assigneeUserId(),
          Timestamp.from(now.plus(Duration.ofHours(step.slaHours()))),
          Timestamp.from(now),
          currentUser.username(),
          Timestamp.from(now),
          currentUser.username());
    }
  }

  private void resetExistingApproval(UUID organizationId, UUID purchaseOrderId) {
    Integer count =
        jdbc.queryForObject(
            "select count(*) from purchase_order_approval where organization_id = ? and purchase_order_id = ? and status = 'PENDING'",
            Integer.class,
            organizationId,
            purchaseOrderId);
    if (count != null && count > 0) {
      throw new ConflictException("A purchase-order approval is already pending");
    }
    jdbc.update(
        "delete from purchase_order_approval_step where organization_id = ? and approval_id in (select id from purchase_order_approval where organization_id = ? and purchase_order_id = ?)",
        organizationId,
        organizationId,
        purchaseOrderId);
    jdbc.update(
        "delete from purchase_order_approval where organization_id = ? and purchase_order_id = ?",
        organizationId,
        purchaseOrderId);
  }

  private boolean isEligible(PurchaseOrder order, StepRow step) {
    if (order.getRequesterSubject().equals(currentUser.subject())) {
      return false;
    }
    UserProfile actor = userProfiles.findBySubject(currentUser.subject()).orElse(null);
    if (actor != null
        && actor.getApprovalLimitAmount() != null
        && order.getTotalAmount().compareTo(actor.getApprovalLimitAmount()) > 0) {
      return false;
    }
    if (ApproverType.ROLE.name().equals(step.approverType())) {
      return step.approverRole() != null && permissions.has("approval:act:" + step.approverRole());
    }
    if (ApproverType.USER.name().equals(step.approverType())
        || ApproverType.REQUESTER_MANAGER.name().equals(step.approverType())) {
      return actor != null && actor.getId().equals(step.assigneeUserId());
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

  private ApprovalRow requirePendingApproval(UUID organizationId, UUID purchaseOrderId) {
    ApprovalRow approval =
        approvalRow(organizationId, purchaseOrderId)
            .orElseThrow(
                () -> new BusinessRuleException("Purchase order has no approval workflow"));
    if (!PENDING.equals(approval.status())) {
      throw new BusinessRuleException("Purchase-order approval is not pending");
    }
    return approval;
  }

  private Optional<ApprovalRow> approvalRow(UUID organizationId, UUID purchaseOrderId) {
    return jdbc
        .query(
            "select id, status, current_sequence from purchase_order_approval where organization_id = ? and purchase_order_id = ?",
            (rs, row) ->
                new ApprovalRow(
                    rs.getObject("id", UUID.class),
                    rs.getString("status"),
                    rs.getInt("current_sequence")),
            organizationId,
            purchaseOrderId)
        .stream()
        .findFirst();
  }

  private StepRow currentStep(ApprovalRow approval, PurchaseOrder order) {
    return jdbc
        .query(
            "select id, sequence, name, approver_type, approver_role, assignee_user_id from purchase_order_approval_step where approval_id = ? and sequence = ? and status = 'PENDING' order by id",
            (rs, row) ->
                new StepRow(
                    rs.getObject("id", UUID.class),
                    rs.getInt("sequence"),
                    rs.getString("name"),
                    rs.getString("approver_type"),
                    rs.getString("approver_role"),
                    rs.getObject("assignee_user_id", UUID.class)),
            approval.id(),
            approval.currentSequence())
        .stream()
        .filter(step -> isEligible(order, step))
        .findFirst()
        .orElseThrow(
            () ->
                new BusinessRuleException(
                    "You are not an eligible approver for the current purchase-order stage"));
  }

  private boolean hasPendingAt(UUID approvalId, int sequence) {
    Integer count =
        jdbc.queryForObject(
            "select count(*) from purchase_order_approval_step where approval_id = ? and sequence = ? and status = 'PENDING'",
            Integer.class,
            approvalId,
            sequence);
    return count != null && count > 0;
  }

  private Integer nextPendingSequence(UUID approvalId, int currentSequence) {
    return jdbc
        .query(
            "select sequence from purchase_order_approval_step where approval_id = ? and status = 'PENDING' and sequence > ? order by sequence limit 1",
            (rs, row) -> rs.getInt("sequence"),
            approvalId,
            currentSequence)
        .stream()
        .findFirst()
        .orElse(null);
  }

  private ApprovalView view(UUID organizationId, UUID purchaseOrderId) {
    ApprovalRow approval =
        approvalRow(organizationId, purchaseOrderId)
            .orElseThrow(() -> new ResourceNotFoundException("Approval not found"));
    List<StepView> steps =
        jdbc.query(
            "select id, sequence, name, approver_type, approver_role, assignee_user_id, status, comment, acted_by, acted_at, sla_due_at from purchase_order_approval_step where approval_id = ? order by sequence",
            (rs, row) ->
                new StepView(
                    rs.getObject("id", UUID.class),
                    rs.getInt("sequence"),
                    rs.getString("name"),
                    rs.getString("approver_type"),
                    rs.getString("approver_role"),
                    rs.getObject("assignee_user_id", UUID.class),
                    rs.getString("status"),
                    rs.getString("comment"),
                    rs.getString("acted_by"),
                    rs.getTimestamp("acted_at") == null
                        ? null
                        : rs.getTimestamp("acted_at").toInstant(),
                    rs.getTimestamp("sla_due_at").toInstant()),
            approval.id());
    return new ApprovalView(approval.id(), approval.status(), approval.currentSequence(), steps);
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private record ApprovalRow(UUID id, String status, int currentSequence) {}

  private record StepRow(
      UUID id,
      int sequence,
      String name,
      String approverType,
      String approverRole,
      UUID assigneeUserId) {}

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
