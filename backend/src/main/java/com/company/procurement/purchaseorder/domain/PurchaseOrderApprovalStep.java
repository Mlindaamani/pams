package com.company.procurement.purchaseorder.domain;

import com.company.procurement.shared.domain.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** One approver assignment in a purchase-order approval stage. */
@Entity
@Table(name = "purchase_order_approval_step")
public class PurchaseOrderApprovalStep extends TenantEntity {

  @Column(name = "approval_id", nullable = false, updatable = false)
  private UUID approvalId;

  @Column(nullable = false, updatable = false)
  private int sequence;

  @Column(nullable = false, updatable = false, length = 200)
  private String name;

  @Column(name = "approver_type", nullable = false, updatable = false, length = 30)
  private String approverType;

  @Column(name = "approver_role", updatable = false, length = 50)
  private String approverRole;

  @Column(name = "assignee_user_id", updatable = false)
  private UUID assigneeUserId;

  @Column(nullable = false, length = 30)
  private String status;

  @Column(length = 1000)
  private String comment;

  @Column(name = "acted_by", length = 200)
  private String actedBy;

  @Column(name = "acted_at")
  private Instant actedAt;

  @Column(name = "sla_due_at", nullable = false, updatable = false)
  private Instant slaDueAt;

  @Column(name = "min_approvals", nullable = false, updatable = false)
  private int minApprovals;

  protected PurchaseOrderApprovalStep() {
    // for JPA
  }

  public PurchaseOrderApprovalStep(
      UUID organizationId,
      UUID approvalId,
      int sequence,
      String name,
      String approverType,
      String approverRole,
      UUID assigneeUserId,
      Instant slaDueAt,
      int minApprovals) {
    super(organizationId);
    this.approvalId = Objects.requireNonNull(approvalId, "approvalId");
    this.sequence = sequence;
    this.name = Objects.requireNonNull(name, "name");
    this.approverType = Objects.requireNonNull(approverType, "approverType");
    this.approverRole = approverRole;
    this.assigneeUserId = assigneeUserId;
    this.status = "PENDING";
    this.slaDueAt = Objects.requireNonNull(slaDueAt, "slaDueAt");
    this.minApprovals = minApprovals;
  }

  public void approve(String comment, String subject, Instant now) {
    this.status = "APPROVED";
    this.comment = comment;
    this.actedBy = subject;
    this.actedAt = now;
  }

  public void reject(String comment, String subject, Instant now) {
    this.status = "REJECTED";
    this.comment = comment;
    this.actedBy = subject;
    this.actedAt = now;
  }

  public void skip(String comment, String actor, Instant now) {
    this.status = "SKIPPED";
    this.comment = comment;
    this.actedBy = actor;
    this.actedAt = now;
  }

  public UUID getApprovalId() {
    return approvalId;
  }

  public int getSequence() {
    return sequence;
  }

  public String getName() {
    return name;
  }

  public String getApproverType() {
    return approverType;
  }

  public String getApproverRole() {
    return approverRole;
  }

  public UUID getAssigneeUserId() {
    return assigneeUserId;
  }

  public String getStatus() {
    return status;
  }

  public String getComment() {
    return comment;
  }

  public String getActedBy() {
    return actedBy;
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
}
