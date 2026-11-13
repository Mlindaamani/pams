package com.company.procurement.approval.domain;

import com.company.procurement.shared.domain.TenantEntity;
import com.company.procurement.shared.domain.TenantFilter;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "approval_step")
@Filter(name = TenantFilter.NAME, condition = "organization_id = :organizationId")
/** One approval assignment and decision within an approval instance. */
public class ApprovalStep extends TenantEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "instance_id", nullable = false, updatable = false)
  private ApprovalInstance instance;

  @Column(name = "sequence", nullable = false)
  private int sequence;

  @Column(name = "name", nullable = false, length = 200)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(name = "approver_type", nullable = false, length = 30)
  private ApproverType approverType;

  @Column(name = "assignee_user_id")
  private UUID assigneeUserId;

  @Column(name = "assignee_role", length = 50)
  private String assigneeRole;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private ApprovalStepStatus status;

  @Column(name = "comment", length = 2000)
  private String comment;

  @Column(name = "acted_by_subject", length = 200)
  private String actedBySubject;

  @Column(name = "acted_by_name", length = 200)
  private String actedByName;

  @Column(name = "acted_at")
  private Instant actedAt;

  @Column(name = "sla_due_at", nullable = false)
  private Instant slaDueAt;

  @Column(name = "min_approvals", nullable = false)
  private int minApprovals;

  @Column(name = "reminder_sent_at")
  private Instant reminderSentAt;

  @Column(name = "escalated_at")
  private Instant escalatedAt;

  protected ApprovalStep() {
    // for JPA
  }

  ApprovalStep(
      UUID organizationId,
      ApprovalInstance instance,
      int sequence,
      String name,
      ApproverType approverType,
      String assigneeRole,
      UUID assigneeUserId,
      Instant slaDueAt,
      int minApprovals) {
    super(organizationId);
    this.instance = Objects.requireNonNull(instance, "instance");
    this.sequence = sequence;
    this.name = Objects.requireNonNull(name, "name");
    this.approverType = Objects.requireNonNull(approverType, "approverType");
    this.assigneeRole = assigneeRole;
    this.assigneeUserId = assigneeUserId;
    this.slaDueAt = Objects.requireNonNull(slaDueAt, "slaDueAt");
    this.minApprovals = minApprovals;
    this.status = ApprovalStepStatus.PENDING;
  }

  public void approve(String actorSubject, String actorName, String comment, Instant now) {
    assertPending();
    this.status = ApprovalStepStatus.APPROVED;
    this.actedBySubject = actorSubject;
    this.actedByName = actorName;
    this.actedAt = now;
    this.comment = comment;
  }

  public void reject(String actorSubject, String actorName, String comment, Instant now) {
    assertPending();
    this.status = ApprovalStepStatus.REJECTED;
    this.actedBySubject = actorSubject;
    this.actedByName = actorName;
    this.actedAt = now;
    this.comment = comment;
  }

  public void skip(String reason, Instant now) {
    assertPending();
    this.status = ApprovalStepStatus.SKIPPED;
    this.actedBySubject = "system";
    this.actedByName = "system";
    this.actedAt = now;
    this.comment = reason;
  }

  public void markReminder(Instant now) {
    this.reminderSentAt = now;
  }

  public void markEscalated(Instant now) {
    this.escalatedAt = now;
  }

  public boolean isPending() {
    return status == ApprovalStepStatus.PENDING;
  }

  private void assertPending() {
    if (status != ApprovalStepStatus.PENDING) {
      throw new IllegalStateException("Step is not pending");
    }
  }

  public ApprovalInstance getInstance() {
    return instance;
  }

  public int getSequence() {
    return sequence;
  }

  public String getName() {
    return name;
  }

  public ApproverType getApproverType() {
    return approverType;
  }

  public UUID getAssigneeUserId() {
    return assigneeUserId;
  }

  public String getAssigneeRole() {
    return assigneeRole;
  }

  public ApprovalStepStatus getStatus() {
    return status;
  }

  public String getComment() {
    return comment;
  }

  public String getActedBySubject() {
    return actedBySubject;
  }

  public String getActedByName() {
    return actedByName;
  }

  public Instant getActedAt() {
    return actedAt;
  }

  public Instant getSlaDueAt() {
    return slaDueAt;
  }

  public int getMinApprovals() {
    return minApprovals;
  }

  public Instant getReminderSentAt() {
    return reminderSentAt;
  }

  public Instant getEscalatedAt() {
    return escalatedAt;
  }
}
