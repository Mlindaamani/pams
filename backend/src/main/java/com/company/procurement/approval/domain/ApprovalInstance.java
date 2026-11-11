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
    int sequence = 1;
    for (ApprovalStepDraft draft : drafts) {
      steps.add(
          new ApprovalStep(
              organizationId,
              this,
              sequence,
              draft.name(),
              draft.approverType(),
              draft.approverRole(),
              draft.assigneeUserId(),
              now.plusSeconds(draft.slaHours() * 3600L)));
      sequence++;
    }
    this.currentSequence = firstStepSequence(now);
  }

  public void approveCurrent(String actorSubject, String actorName, String comment, Instant now) {
    ApprovalStep step = requireCurrentStep();
    step.approve(actorSubject, actorName, comment, now);
    advance(now);
  }

  public void rejectCurrent(String actorSubject, String actorName, String comment, Instant now) {
    ApprovalStep step = requireCurrentStep();
    step.reject(actorSubject, actorName, comment, now);
    this.status = ApprovalInstanceStatus.REJECTED;
    this.lastComment = comment;
    this.completedAt = now;
  }

  public void requestChanges(String comment) {
    if (comment == null || comment.isBlank()) {
      throw new BusinessRuleException("A comment is required when requesting changes");
    }
    requireCurrentStep();
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

  public void markReminderOnCurrent(Instant now) {
    requireCurrentStep().markReminder(now);
  }

  public void escalateCurrent(Instant now) {
    ApprovalStep step = requireCurrentStep();
    step.markEscalated(now);
    step.skip("Escalated after SLA breach", now);
    advance(now);
  }

  public Optional<ApprovalStep> currentStep() {
    if (currentSequence == null) {
      return Optional.empty();
    }
    return steps.stream()
        .filter(step -> step.getSequence() == currentSequence)
        .filter(ApprovalStep::isPending)
        .findFirst();
  }

  public boolean isCurrentStep(ApprovalStep step) {
    return currentSequence != null && currentSequence == step.getSequence() && step.isPending();
  }

  private ApprovalStep requireCurrentStep() {
    return currentStep()
        .orElseThrow(() -> new IllegalStateTransitionException("There is no active approval step"));
  }

  private void advance(Instant now) {
    Optional<ApprovalStep> next = nextPendingAfter(currentSequence);
    while (next.isPresent()
        && next.get().getApproverType() == ApproverType.USER
        && Objects.equals(next.get().getAssigneeUserId(), requesterUserId)) {
      next.get().skip("Requester cannot approve their own request", now);
      next = nextPendingAfter(currentSequence);
    }
    if (next.isPresent()) {
      this.currentSequence = next.get().getSequence();
    } else {
      this.currentSequence = null;
      this.status = ApprovalInstanceStatus.APPROVED;
      this.completedAt = now;
    }
  }

  private Integer firstStepSequence(Instant now) {
    Optional<ApprovalStep> first = steps.stream().filter(ApprovalStep::isPending).findFirst();
    while (first.isPresent()
        && first.get().getApproverType() == ApproverType.USER
        && Objects.equals(first.get().getAssigneeUserId(), requesterUserId)) {
      first.get().skip("Requester cannot approve their own request", now);
      first = steps.stream().filter(ApprovalStep::isPending).findFirst();
    }
    if (first.isEmpty()) {
      throw new BusinessRuleException(
          "Approval chain has no eligible approver (requester cannot approve their own request)");
    }
    return first.get().getSequence();
  }

  private Optional<ApprovalStep> nextPendingAfter(Integer sequence) {
    return steps.stream()
        .filter(step -> sequence == null || step.getSequence() > sequence)
        .filter(ApprovalStep::isPending)
        .findFirst();
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
