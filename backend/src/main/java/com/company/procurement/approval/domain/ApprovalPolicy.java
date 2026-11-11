package com.company.procurement.approval.domain;

import com.company.procurement.shared.domain.SoftDeletableEntity;
import com.company.procurement.shared.domain.TenantFilter;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "approval_policy")
@SQLRestriction("deleted_at IS NULL")
@Filter(name = TenantFilter.NAME, condition = "organization_id = :organizationId")
public class ApprovalPolicy extends SoftDeletableEntity {

  @Column(name = "name", nullable = false, length = 200)
  private String name;

  @Column(name = "amount_min", nullable = false, precision = 19, scale = 4)
  private BigDecimal amountMin;

  @Column(name = "amount_max", precision = 19, scale = 4)
  private BigDecimal amountMax;

  @Column(name = "policy_version", nullable = false)
  private int policyVersion;

  @Column(name = "entity_type", nullable = false, length = 30)
  private String entityType = "PURCHASE_REQUEST";

  @Column(name = "active", nullable = false)
  private boolean active;

  @BatchSize(size = 50)
  @OneToMany(
      mappedBy = "policy",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  @OrderBy("sequence ASC")
  private List<ApprovalPolicyStep> steps = new ArrayList<>();

  protected ApprovalPolicy() {
    // for JPA
  }

  public ApprovalPolicy(
      UUID organizationId,
      String name,
      BigDecimal amountMin,
      BigDecimal amountMax,
      int policyVersion) {
    this(organizationId, "PURCHASE_REQUEST", name, amountMin, amountMax, policyVersion);
  }

  public ApprovalPolicy(
      UUID organizationId,
      String entityType,
      String name,
      BigDecimal amountMin,
      BigDecimal amountMax,
      int policyVersion) {
    super(organizationId);
    this.entityType = requireText(entityType, "entityType").toUpperCase(java.util.Locale.ROOT);
    this.name = requireText(name, "name");
    this.amountMin = Objects.requireNonNull(amountMin, "amountMin");
    if (amountMin.signum() < 0) {
      throw new IllegalArgumentException("amountMin must not be negative");
    }
    if (amountMax != null && amountMax.compareTo(amountMin) <= 0) {
      throw new IllegalArgumentException("amountMax must be greater than amountMin");
    }
    this.amountMax = amountMax;
    this.policyVersion = policyVersion;
    this.active = true;
  }

  public void addStep(String stepName, ApproverType type, String role, UUID userId, int slaHours) {
    int sequence = steps.size() + 1;
    steps.add(
        new ApprovalPolicyStep(
            getOrganizationId(), this, sequence, stepName, type, role, userId, slaHours));
  }

  public void deactivate() {
    this.active = false;
  }

  public boolean covers(BigDecimal amount) {
    return amount.compareTo(amountMin) >= 0
        && (amountMax == null || amount.compareTo(amountMax) < 0);
  }

  public String getEntityType() {
    return entityType;
  }

  public String getName() {
    return name;
  }

  public BigDecimal getAmountMin() {
    return amountMin;
  }

  public BigDecimal getAmountMax() {
    return amountMax;
  }

  public int getPolicyVersion() {
    return policyVersion;
  }

  public boolean isActive() {
    return active;
  }

  public List<ApprovalPolicyStep> getSteps() {
    return steps;
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
