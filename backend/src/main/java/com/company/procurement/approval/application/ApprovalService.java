package com.company.procurement.approval.application;

import com.company.procurement.approval.domain.ApprovalEvents.ChangesRequested;
import com.company.procurement.approval.domain.ApprovalEvents.ReminderSent;
import com.company.procurement.approval.domain.ApprovalEvents.RequestApproved;
import com.company.procurement.approval.domain.ApprovalEvents.RequestRejected;
import com.company.procurement.approval.domain.ApprovalEvents.StepApproved;
import com.company.procurement.approval.domain.ApprovalEvents.StepEscalated;
import com.company.procurement.approval.domain.ApprovalInstance;
import com.company.procurement.approval.domain.ApprovalInstanceRepository;
import com.company.procurement.approval.domain.ApprovalInstanceStatus;
import com.company.procurement.approval.domain.ApprovalPolicy;
import com.company.procurement.approval.domain.ApprovalPolicyRepository;
import com.company.procurement.approval.domain.ApprovalStep;
import com.company.procurement.approval.domain.ApprovalStepRepository;
import com.company.procurement.approval.domain.Delegation;
import com.company.procurement.approval.domain.DelegationRepository;
import com.company.procurement.audit.application.AuditEventService;
import com.company.procurement.organization.application.UserProfileLookupService;
import com.company.procurement.organization.domain.UserProfile;
import com.company.procurement.shared.actor.CurrentActorProvider;
import com.company.procurement.shared.authorization.PermissionService;
import com.company.procurement.shared.error.BusinessRuleException;
import com.company.procurement.shared.error.ConflictException;
import com.company.procurement.shared.error.ResourceNotFoundException;
import com.company.procurement.shared.security.CurrentUser;
import com.company.procurement.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApprovalService {

  private final ApprovalPolicyRepository policies;
  private final ApprovalInstanceRepository instances;
  private final ApprovalStepRepository steps;
  private final DelegationRepository delegations;
  private final ApprovalChainResolver chainResolver;
  private final UserProfileLookupService userProfiles;
  private final CurrentUser currentUser;
  private final CurrentActorProvider actorProvider;
  private final AuditEventService audit;
  private final PermissionService permissions;
  private final ApplicationEventPublisher publisher;
  private final Clock clock;

  public ApprovalService(
      ApprovalPolicyRepository policies,
      ApprovalInstanceRepository instances,
      ApprovalStepRepository steps,
      DelegationRepository delegations,
      ApprovalChainResolver chainResolver,
      UserProfileLookupService userProfiles,
      CurrentUser currentUser,
      CurrentActorProvider actorProvider,
      AuditEventService audit,
      PermissionService permissions,
      ApplicationEventPublisher publisher,
      Clock clock) {
    this.policies = policies;
    this.instances = instances;
    this.steps = steps;
    this.delegations = delegations;
    this.chainResolver = chainResolver;
    this.userProfiles = userProfiles;
    this.currentUser = currentUser;
    this.actorProvider = actorProvider;
    this.audit = audit;
    this.permissions = permissions;
    this.publisher = publisher;
    this.clock = clock;
  }

  @Transactional
  public ApprovalPolicy createPolicy(CreatePolicyCommand command) {
    UUID organizationId = TenantContext.require();
    ApprovalPolicy policy =
        new ApprovalPolicy(
            organizationId,
            command.entityType() == null ? "PURCHASE_REQUEST" : command.entityType(),
            command.name(),
            command.amountMin(),
            command.amountMax(),
            1);
    addSteps(policy, command.steps());
    return policies.save(policy);
  }

  @Transactional
  public ApprovalPolicy updatePolicy(UUID id, UpdatePolicyCommand command) {
    ApprovalPolicy current = getPolicy(id);
    current.deactivate();
    policies.save(current);

    ApprovalPolicy next =
        new ApprovalPolicy(
            current.getOrganizationId(),
            command.entityType() == null ? current.getEntityType() : command.entityType(),
            command.name(),
            command.amountMin(),
            command.amountMax(),
            current.getPolicyVersion() + 1);
    addSteps(next, command.steps());
    return policies.save(next);
  }

  @Transactional(readOnly = true)
  public ApprovalPolicy getPolicy(UUID id) {
    UUID organizationId = TenantContext.require();
    return policies
        .findByIdAndOrganizationId(id, organizationId)
        .orElseThrow(
            () -> new ResourceNotFoundException("Approval policy %s not found".formatted(id)));
  }

  @Transactional
  public void deletePolicy(UUID id) {
    ApprovalPolicy policy = getPolicy(id);
    policy.deactivate();
    policy.softDelete(clock.instant(), actorProvider.currentActor());
    policies.save(policy);
    audit.recordCurrentActor(
        "approval_policy",
        id,
        "approval_policy_deleted",
        Map.of("name", policy.getName(), "policyVersion", policy.getPolicyVersion()));
  }

  @Transactional(readOnly = true)
  public Page<ApprovalPolicy> searchPolicies(Boolean active, Pageable pageable) {
    Page<ApprovalPolicy> page = policies.search(TenantContext.require(), active, pageable);
    // Initialise the batch-loaded steps inside the read transaction so API mapping is safe.
    page.getContent().forEach(policy -> policy.getSteps().size());
    return page;
  }

  @Transactional
  public ApprovalStartResult startApproval(
      UUID requestId,
      String requesterSubject,
      String currency,
      BigDecimal totalAmount,
      boolean budgetException) {
    UUID organizationId = TenantContext.require();
    Optional<UserProfile> requester = userProfiles.findBySubject(requesterSubject);
    UUID requesterUserId = requester.map(UserProfile::getId).orElse(null);
    UUID managerUserId = requester.map(UserProfile::getManagerId).orElse(null);

    ResolvedChain chain =
        chainResolver.resolve(
            organizationId,
            "PURCHASE_REQUEST",
            totalAmount,
            requesterUserId,
            managerUserId,
            budgetException);

    instances.deleteByOrganizationIdAndPurchaseRequestId(organizationId, requestId);

    ApprovalInstance instance =
        new ApprovalInstance(
            organizationId,
            requestId,
            requesterSubject,
            requesterUserId,
            chain.policyId(),
            chain.policyVersion(),
            currency,
            totalAmount,
            chain.steps(),
            clock.instant());
    instances.save(instance);

    audit.record(
        "purchase_request",
        requestId,
        "approval_started",
        currentUser.subject(),
        currentUser.username(),
        Map.of(
            "policyId", chain.policyId().toString(),
            "policyVersion", chain.policyVersion(),
            "instanceId", instance.getId().toString()));

    return new ApprovalStartResult(instance.getId(), chain.policyId(), chain.policyVersion());
  }

  @Transactional
  public ApprovalInstance approve(UUID stepId, String comment) {
    ApprovalInstance instance = instanceForStep(stepId);
    ApprovalStep step = currentStep(instance, stepId);
    UserProfile actor = verifyEligibility(instance, step);
    instance.approveCurrent(
        currentUser.subject(), currentUser.username(), comment, clock.instant());
    instances.save(instance);

    audit.record(
        "purchase_request",
        instance.getPurchaseRequestId(),
        "approval_step_approved",
        currentUser.subject(),
        currentUser.username(),
        Map.of("stepId", stepId.toString(), "sequence", step.getSequence()));
    publisher.publishEvent(
        new StepApproved(instance.getPurchaseRequestId(), stepId, currentUser.subject()));

    if (instance.getStatus() == ApprovalInstanceStatus.APPROVED) {
      audit.record(
          "purchase_request",
          instance.getPurchaseRequestId(),
          "request_approved",
          currentUser.subject(),
          currentUser.username(),
          Map.of("instanceId", instance.getId().toString()));
      publisher.publishEvent(new RequestApproved(instance.getPurchaseRequestId()));
    }
    return instance;
  }

  @Transactional
  public int bulkApprove(List<UUID> stepIds, String comment) {
    stepIds.forEach(stepId -> approve(stepId, comment));
    return stepIds.size();
  }

  @Transactional
  public ApprovalInstance reject(UUID stepId, String comment) {
    if (comment == null || comment.isBlank()) {
      throw new BusinessRuleException("A comment is required when rejecting");
    }
    ApprovalInstance instance = instanceForStep(stepId);
    ApprovalStep step = currentStep(instance, stepId);
    verifyEligibility(instance, step);
    instance.rejectCurrent(currentUser.subject(), currentUser.username(), comment, clock.instant());
    instances.save(instance);

    audit.record(
        "purchase_request",
        instance.getPurchaseRequestId(),
        "request_rejected",
        currentUser.subject(),
        currentUser.username(),
        Map.of("stepId", stepId.toString(), "comment", comment));
    publisher.publishEvent(new RequestRejected(instance.getPurchaseRequestId(), comment));
    return instance;
  }

  @Transactional
  public ApprovalInstance requestChanges(UUID stepId, String comment) {
    if (comment == null || comment.isBlank()) {
      throw new BusinessRuleException("A comment is required when requesting changes");
    }
    ApprovalInstance instance = instanceForStep(stepId);
    ApprovalStep step = currentStep(instance, stepId);
    verifyEligibility(instance, step);
    instance.requestChanges(comment);
    instances.save(instance);

    audit.record(
        "purchase_request",
        instance.getPurchaseRequestId(),
        "approval_changes_requested",
        currentUser.subject(),
        currentUser.username(),
        Map.of("stepId", stepId.toString(), "comment", comment));
    publisher.publishEvent(new ChangesRequested(instance.getPurchaseRequestId(), comment));
    return instance;
  }

  @Transactional(readOnly = true)
  public ApprovalInstance getForRequest(UUID requestId) {
    UUID organizationId = TenantContext.require();
    ApprovalInstance instance =
        instances
            .findDetailByOrganizationIdAndPurchaseRequestId(organizationId, requestId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Approval instance for request %s not found".formatted(requestId)));
    if (!instance.getRequesterSubject().equals(currentUser.subject())
        && !permissions.has("purchase_request:view_all")) {
      throw new ResourceNotFoundException(
          "Approval instance for request %s not found".formatted(requestId));
    }
    return instance;
  }

  @Transactional(readOnly = true)
  public Optional<ApprovalStep> currentActionableStep(UUID requestId) {
    UUID organizationId = TenantContext.require();
    Optional<ApprovalInstance> instance =
        instances.findDetailByOrganizationIdAndPurchaseRequestId(organizationId, requestId);
    if (instance.isEmpty()
        || instance.get().getStatus() != ApprovalInstanceStatus.IN_PROGRESS
        || instance.get().getRequesterSubject().equals(currentUser.subject())) {
      return Optional.empty();
    }
    Optional<ApprovalStep> step = instance.get().currentStep();
    if (step.isEmpty()) {
      return Optional.empty();
    }
    Optional<UserProfile> profile = userProfiles.findBySubject(currentUser.subject());
    return isEligible(step.get(), profile, clock.instant()) ? step : Optional.empty();
  }

  @Transactional(readOnly = true)
  public List<ApprovalStep> inbox() {
    UUID organizationId = TenantContext.require();
    Optional<UserProfile> profile = userProfiles.findBySubject(currentUser.subject());
    Instant now = clock.instant();
    return steps.findCurrentPendingSteps(organizationId).stream()
        .filter(step -> !step.getInstance().getRequesterSubject().equals(currentUser.subject()))
        .filter(step -> isEligible(step, profile, now))
        .toList();
  }

  @Transactional
  public void cancelForRequest(UUID requestId) {
    UUID organizationId = TenantContext.require();
    instances
        .findByOrganizationIdAndPurchaseRequestId(organizationId, requestId)
        .ifPresent(
            instance -> {
              if (instance.getStatus() == ApprovalInstanceStatus.IN_PROGRESS
                  || instance.getStatus() == ApprovalInstanceStatus.CHANGES_REQUESTED) {
                instance.cancel();
                instances.save(instance);
                audit.record(
                    "purchase_request",
                    requestId,
                    "approval_cancelled",
                    actorProvider.currentActor(),
                    currentUser.username(),
                    Map.of("instanceId", instance.getId().toString()));
              }
            });
  }

  @Transactional
  public Delegation createDelegation(CreateDelegationCommand command) {
    UUID organizationId = TenantContext.require();
    UserProfile delegator =
        userProfiles
            .findBySubject(currentUser.subject())
            .orElseThrow(
                () ->
                    new BusinessRuleException("Create a user profile before delegating approvals"));
    UserProfile delegate =
        userProfiles
            .findById(command.delegateUserId())
            .orElseThrow(
                () ->
                    new BusinessRuleException(
                        "Delegate %s does not exist".formatted(command.delegateUserId())));
    if (command.endsAt() == null || command.startsAt() == null) {
      throw new BusinessRuleException("Delegation start and end are required");
    }
    Delegation delegation =
        new Delegation(
            organizationId,
            delegator.getId(),
            delegate.getId(),
            command.startsAt(),
            command.endsAt());
    Delegation saved = delegations.save(delegation);
    audit.recordCurrentActor(
        "delegation",
        saved.getId(),
        "delegation_created",
        Map.of("delegateUserId", delegate.getId().toString()));
    return saved;
  }

  @Transactional(readOnly = true)
  public List<Delegation> myDelegations() {
    UUID organizationId = TenantContext.require();
    Optional<UserProfile> profile = userProfiles.findBySubject(currentUser.subject());
    return profile
        .map(
            value ->
                delegations.findByOrganizationIdAndDelegatorUserId(organizationId, value.getId()))
        .orElseGet(List::of);
  }

  @Transactional
  public void deactivateDelegation(UUID id) {
    UUID organizationId = TenantContext.require();
    Delegation delegation =
        delegations
            .findByIdAndOrganizationId(id, organizationId)
            .orElseThrow(
                () -> new ResourceNotFoundException("Delegation %s not found".formatted(id)));
    Optional<UserProfile> profile = userProfiles.findBySubject(currentUser.subject());
    if (profile.isEmpty() || !delegation.getDelegatorUserId().equals(profile.get().getId())) {
      throw new AccessDeniedException("Only the delegator can revoke a delegation");
    }
    delegation.deactivate();
    delegations.save(delegation);
  }

  @Transactional
  public void runEscalation(Instant now) {
    UUID organizationId = TenantContext.require();
    for (ApprovalStep step : steps.findDueSteps(organizationId, now)) {
      ApprovalInstance instance =
          instances
              .findDetailByIdAndOrganizationId(step.getInstance().getId(), organizationId)
              .orElse(null);
      if (instance == null
          || instance.getStatus() != ApprovalInstanceStatus.IN_PROGRESS
          || instance.getCurrentSequence() == null
          || instance.getCurrentSequence() != step.getSequence()) {
        continue;
      }
      if (step.getReminderSentAt() == null) {
        instance.markReminderOnCurrent(now);
        instances.save(instance);
        audit.recordCurrentActor(
            "purchase_request",
            instance.getPurchaseRequestId(),
            "approval_reminder",
            Map.of("stepId", step.getId().toString()));
        publisher.publishEvent(new ReminderSent(instance.getPurchaseRequestId(), step.getId()));
      } else if (step.getEscalatedAt() == null) {
        instance.escalateCurrent(now);
        instances.save(instance);
        audit.recordCurrentActor(
            "purchase_request",
            instance.getPurchaseRequestId(),
            "approval_escalated",
            Map.of("stepId", step.getId().toString()));
        publisher.publishEvent(new StepEscalated(instance.getPurchaseRequestId(), step.getId()));
        if (instance.getStatus() == ApprovalInstanceStatus.APPROVED) {
          publisher.publishEvent(new RequestApproved(instance.getPurchaseRequestId()));
        }
      }
    }
  }

  private ApprovalInstance instanceForStep(UUID stepId) {
    UUID organizationId = TenantContext.require();
    ApprovalStep step =
        steps
            .findByIdAndOrganizationId(stepId, organizationId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException("Approval step %s not found".formatted(stepId)));
    return instances
        .findDetailByIdAndOrganizationId(step.getInstance().getId(), organizationId)
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    "Approval instance for step %s not found".formatted(stepId)));
  }

  private ApprovalStep currentStep(ApprovalInstance instance, UUID stepId) {
    return instance.getSteps().stream()
        .filter(step -> step.getId().equals(stepId))
        .filter(step -> instance.isCurrentStep(step))
        .findFirst()
        .orElseThrow(() -> new ConflictException("Approval step is not the active step"));
  }

  private UserProfile verifyEligibility(ApprovalInstance instance, ApprovalStep step) {
    String subject = currentUser.subject();
    if (instance.getRequesterSubject().equals(subject)) {
      throw new AccessDeniedException("Requesters cannot approve their own requests");
    }
    Optional<UserProfile> profile = userProfiles.findBySubject(subject);
    if (!isEligible(step, profile, clock.instant())) {
      throw new AccessDeniedException("You are not the assigned approver for this step");
    }
    profile.ifPresent(
        actor -> {
          if (actor.getApprovalLimitAmount() != null
              && instance.getTotalAmount().compareTo(actor.getApprovalLimitAmount()) > 0) {
            throw new BusinessRuleException(
                "Request total exceeds your approval limit of %s %s"
                    .formatted(actor.getApprovalLimitAmount(), actor.getApprovalLimitCurrency()));
          }
        });
    return profile.orElse(null);
  }

  private boolean isEligible(ApprovalStep step, Optional<UserProfile> profile, Instant now) {
    if (step.getAssigneeRole() != null) {
      return permissions.has("approval:act:" + step.getAssigneeRole());
    }
    if (profile.isEmpty()) {
      return false;
    }
    UUID actorId = profile.get().getId();
    if (step.getAssigneeUserId().equals(actorId)) {
      return true;
    }
    return delegations.existsActiveDelegation(
        step.getOrganizationId(), step.getAssigneeUserId(), actorId, now);
  }

  private static void addSteps(ApprovalPolicy policy, List<PolicyStepCommand> commands) {
    if (commands == null || commands.isEmpty()) {
      throw new BusinessRuleException("An approval policy needs at least one step");
    }
    for (PolicyStepCommand command : commands) {
      policy.addStep(
          command.name(),
          command.approverType(),
          command.approverRole(),
          command.approverUserId(),
          command.slaHours());
    }
  }

  public record ApprovalStartResult(UUID instanceId, UUID policyId, int policyVersion) {}
}
