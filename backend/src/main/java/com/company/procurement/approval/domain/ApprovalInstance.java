package com.company.procurement.approval.domain;

import com.company.procurement.shared.domain.TenantEntity;
import com.company.procurement.shared.domain.TenantFilter;
import com.company.procurement.shared.error.BusinessRuleException;
import com.company.procurement.shared.error.IllegalStateTransitionException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "approval_instance")
@Filter(name = TenantFilter.NAME, condition = "organization_id = :organizationId")
public class ApprovalInstance extends TenantEntity {

  @Column(name = "purchase_request_id", nullable = false, updatable = false)
  private UUID purchaseRequestId;

  @Column(name = "requester_subject", nullable = false, updatable = false, length = 200)
  private String requesterSubject;

  @Column(name = "requester_user_id")
  private UUID requesterUserId;

  @Column(name = "policy_id", nullable = false, updatable = false)
  private UUID policyId;

  @Column(name = "policy_version", nullable = false, updatable = false)
  private int policyVersion;

  @Column(name = "currency", nullable = false, updatable = false, length = 3)
  private String currency;

  @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
  private BigDecimal totalAmount;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 30)
  private ApprovalInstanceStatus status;

  @Column(name = "current_sequence")
  private Integer currentSequence;

  @Column(name = "last_comment", length = 2000)
  private String lastComment;

  @Column(name = "completed_at")
  private Instant completedAt;

  @OneToMany(
      mappedBy = "instance",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  @OrderBy("sequence ASC")
  private List<ApprovalStep> steps = new ArrayList<>();

  protected ApprovalInstance() {
    // for JPA
  }

  public ApprovalInstance(
      UUID organizationId,
      UUID purchaseRequestId,
      String requesterSubject,
      UUID requesterUserId,
      UUID policyId,
      int policyVersion,
      String currency,
      BigDecimal totalAmount,
      List<ApprovalStepDraft> drafts,
      Instant now) {
    super(organizationId);
    this.purchaseRequestId = Objects.requireNonNull(purchaseRequestId, "purchaseRequestId");
    this.requesterSubject = Objects.requireNonNull(requesterSubject, "requesterSubject");
    this.requesterUserId = requesterUserId;
    this.policyId = Objects.requireNonNull(policyId, "policyId");
    this.policyVersion = policyVersion;
    this.currency = Objects.requireNonNull(currency, "currency");
    this.totalAmount = Objects.requireNonNull(totalAmount, "totalAmount");
    this.status = ApprovalInstanceStatus.IN_PROGRESS;
    if (drafts.isEmpty()) {
      throw new BusinessRuleException("Approval chain resolved to no steps");
    }
    for (ApprovalStepDraft draft : drafts) {
      steps.add(
          new ApprovalStep(
              organizationId,
              this,
              draft.stage(),
              draft.name(),
              draft.approverType(),
              draft.approverRole(),
              draft.assigneeUserId(),
              now.plusSeconds(draft.slaHours() * 3600L)));
    }
    this.currentSequence = firstStage(now);
  }

  public void approveStep(
      UUID stepId, String actorSubject, String actorName, String comment, Instant now) {
    ApprovalStep step = requireCurrentStep(stepId);
    step.approve(actorSubject, actorName, comment, now);
    if (currentSteps().isEmpty()) {
      advance(now);
    }
  }

  public void rejectStep(
      UUID stepId, String actorSubject, String actorName, String comment, Instant now) {
    ApprovalStep step = requireCurrentStep(stepId);
    step.reject(actorSubject, actorName, comment, now);
    this.status = ApprovalInstanceStatus.REJECTED;
    this.lastComment = comment;
    this.completedAt = now;
  }

  public void requestChanges(UUID stepId, String comment) {
    if (comment == null || comment.isBlank()) {
      throw new BusinessRuleException("A comment is required when requesting changes");
    }
    requireCurrentStep(stepId);
    this.status = ApprovalInstanceStatus.CHANGES_REQUESTED;
    this.lastComment = comment;
  }

  public void cancel() {
    if (status == ApprovalInstanceStatus.APPROVED || status == ApprovalInstanceStatus.REJECTED) {
      throw new IllegalStateTransitionException(
          "An approval instance in status %s cannot be cancelled".formatted(status));
    }
    this.status = ApprovalInstanceStatus.CANCELLED;
  }

  public void markReminder(UUID stepId, Instant now) {
    requireCurrentStep(stepId).markReminder(now);
  }

  public void escalateStep(UUID stepId, Instant now) {
    ApprovalStep step = requireCurrentStep(stepId);
    step.markEscalated(now);
    step.skip("Escalated after SLA breach", now);
    if (currentSteps().isEmpty()) {
      advance(now);
    }
  }

  public List<ApprovalStep> currentSteps() {
    if (currentSequence == null) {
      return List.of();
    }
    return steps.stream()
        .filter(step -> step.getSequence() == currentSequence)
        .filter(ApprovalStep::isPending)
        .toList();
  }

  public Optional<ApprovalStep> currentStep() {
    return currentSteps().stream().findFirst();
  }

  public boolean isCurrentStep(ApprovalStep step) {
    return currentSequence != null && currentSequence == step.getSequence() && step.isPending();
  }

  private ApprovalStep requireCurrentStep(UUID stepId) {
    return steps.stream()
        .filter(step -> step.getId().equals(stepId))
        .filter(this::isCurrentStep)
        .findFirst()
        .orElseThrow(
            () -> new IllegalStateTransitionException("Approval step is not the active stage"));
  }

  private void advance(Instant now) {
    Integer next = nextStageAfter(currentSequence);
    while (next != null) {
      skipSelfApprovals(next, now);
      if (hasPendingAt(next)) {
        this.currentSequence = next;
        return;
      }
      next = nextStageAfter(next);
    }
    this.currentSequence = null;
    this.status = ApprovalInstanceStatus.APPROVED;
    this.completedAt = now;
  }

  private Integer firstStage(Instant now) {
    Integer stage =
        steps.stream().map(ApprovalStep::getSequence).min(Integer::compareTo).orElse(null);
    while (stage != null) {
      skipSelfApprovals(stage, now);
      if (hasPendingAt(stage)) {
        return stage;
      }
      stage = nextStageAfter(stage);
    }
    throw new BusinessRuleException(
        "Approval chain has no eligible approver (requester cannot approve their own request)");
  }

  private void skipSelfApprovals(int stage, Instant now) {
    steps.stream()
        .filter(step -> step.getSequence() == stage)
        .filter(ApprovalStep::isPending)
        .filter(this::isSelfApproval)
        .forEach(step -> step.skip("Requester cannot approve their own request", now));
  }

  private boolean isSelfApproval(ApprovalStep step) {
    return step.getAssigneeUserId() != null
        && Objects.equals(step.getAssigneeUserId(), requesterUserId);
  }

  private boolean hasPendingAt(int stage) {
    return steps.stream()
        .filter(step -> step.getSequence() == stage)
        .anyMatch(ApprovalStep::isPending);
  }

  private Integer nextStageAfter(Integer stage) {
    return steps.stream()
        .map(ApprovalStep::getSequence)
        .filter(candidate -> stage == null || candidate > stage)
        .min(Integer::compareTo)
        .orElse(null);
  }

  public UUID getPurchaseRequestId() {
    return purchaseRequestId;
  }

  public String getRequesterSubject() {
    return requesterSubject;
  }

  public UUID getRequesterUserId() {
    return requesterUserId;
  }

  public UUID getPolicyId() {
    return policyId;
  }

  public int getPolicyVersion() {
    return policyVersion;
  }

  public BigDecimal getTotalAmount() {
    return totalAmount;
  }

  public String getCurrency() {
    return currency;
  }

  public ApprovalInstanceStatus getStatus() {
    return status;
  }

  public Integer getCurrentSequence() {
    return currentSequence;
  }

  public String getLastComment() {
    return lastComment;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }

  public List<ApprovalStep> getSteps() {
    return steps;
  }
}
