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
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "approval_policy_step")
@Filter(name = TenantFilter.NAME, condition = "organization_id = :organizationId")
public class ApprovalPolicyStep extends TenantEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "policy_id", nullable = false, updatable = false)
  private ApprovalPolicy policy;

  @Column(name = "sequence", nullable = false)
  private int sequence;

  @Column(name = "name", nullable = false, length = 200)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(name = "approver_type", nullable = false, length = 30)
  private ApproverType approverType;

  @Column(name = "approver_role", length = 50)
  private String approverRole;

  @Column(name = "approver_user_id")
  private UUID approverUserId;

  @Column(name = "sla_hours", nullable = false)
  private int slaHours;

  @Enumerated(EnumType.STRING)
  @Column(name = "condition_type", nullable = false, length = 30)
  private ApprovalConditionType conditionType;

  @Column(name = "condition_department_id")
  private UUID conditionDepartmentId;

  protected ApprovalPolicyStep() {
    // for JPA
  }

  ApprovalPolicyStep(
      UUID organizationId,
      ApprovalPolicy policy,
      int sequence,
      String name,
      ApproverType approverType,
      String role,
      UUID userId,
      int slaHours,
      ApprovalConditionType conditionType,
      UUID conditionDepartmentId) {
    super(organizationId);
    this.policy = Objects.requireNonNull(policy, "policy");
    this.sequence = sequence;
    this.name = requireText(name, "name");
    this.approverType = Objects.requireNonNull(approverType, "approverType");
    this.slaHours = slaHours;
    this.conditionType = conditionType == null ? ApprovalConditionType.ALWAYS : conditionType;
    this.conditionDepartmentId = conditionDepartmentId;
    if (this.conditionType == ApprovalConditionType.DEPARTMENT && conditionDepartmentId == null) {
      throw new IllegalArgumentException("conditionDepartmentId is required for DEPARTMENT");
    }
    if (slaHours <= 0) {
      throw new IllegalArgumentException("slaHours must be positive");
    }
    switch (approverType) {
      case ROLE -> this.approverRole = requireText(role, "approverRole");
      case USER -> this.approverUserId = Objects.requireNonNull(userId, "approverUserId");
      case REQUESTER_MANAGER -> {
        // resolved to a user at submission time
      }
    }
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

  public String getApproverRole() {
    return approverRole;
  }

  public UUID getApproverUserId() {
    return approverUserId;
  }

  public int getSlaHours() {
    return slaHours;
  }

  public ApprovalConditionType getConditionType() {
    return conditionType;
  }

  public UUID getConditionDepartmentId() {
    return conditionDepartmentId;
  }

  private static String requireText(String value, String field) {
    Objects.requireNonNull(value, field);
    String trimmed = value.trim();
    if (trimmed.isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return trimmed;
  }
}
