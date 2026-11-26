package com.company.procurement.purchaseorder.domain;

import com.company.procurement.shared.domain.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;

/** Approval process attached to a purchase order. */
@Entity
@Table(name = "purchase_order_approval")
public class PurchaseOrderApproval extends TenantEntity {

  @Column(name = "purchase_order_id", nullable = false, updatable = false)
  private UUID purchaseOrderId;

  @Column(name = "policy_id", nullable = false, updatable = false)
  private UUID policyId;

  @Column(name = "policy_version", nullable = false, updatable = false)
  private int policyVersion;

  @Column(name = "policy_name", nullable = false, updatable = false, length = 200)
  private String policyName;

  @Column(nullable = false, length = 30)
  private String status;

  @Column(name = "current_sequence", nullable = false)
  private int currentSequence;

  protected PurchaseOrderApproval() {
    // for JPA
  }

  public PurchaseOrderApproval(
      UUID organizationId,
      UUID purchaseOrderId,
      UUID policyId,
      int policyVersion,
      String policyName,
      int currentSequence) {
    super(organizationId);
    this.purchaseOrderId = Objects.requireNonNull(purchaseOrderId, "purchaseOrderId");
    this.policyId = Objects.requireNonNull(policyId, "policyId");
    this.policyVersion = policyVersion;
    this.policyName = Objects.requireNonNull(policyName, "policyName");
    this.status = "PENDING";
    this.currentSequence = currentSequence;
  }

  public void approve() {
    this.status = "APPROVED";
  }

  public void reject() {
    this.status = "REJECTED";
  }

  public void requestChanges() {
    this.status = "CHANGES_REQUESTED";
  }

  public void cancel() {
    this.status = "CANCELED";
  }

  public void moveToSequence(int sequence) {
    this.currentSequence = sequence;
  }

  public UUID getPurchaseOrderId() {
    return purchaseOrderId;
  }

  public UUID getPolicyId() {
    return policyId;
  }

  public int getPolicyVersion() {
    return policyVersion;
  }

  public String getPolicyName() {
    return policyName;
  }

  public String getStatus() {
    return status;
  }

  public int getCurrentSequence() {
    return currentSequence;
  }
}
