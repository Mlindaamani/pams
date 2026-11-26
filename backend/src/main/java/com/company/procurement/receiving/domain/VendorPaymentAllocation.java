package com.company.procurement.receiving.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Amount of a vendor payment allocated to an invoice. */
@Entity
@Table(name = "vendor_payment_allocation")
public class VendorPaymentAllocation {

  @Id @GeneratedValue private UUID id;

  @Column(name = "organization_id", nullable = false, updatable = false)
  private UUID organizationId;

  @Column(name = "payment_id", nullable = false, updatable = false)
  private UUID paymentId;

  @Column(name = "invoice_id", nullable = false, updatable = false)
  private UUID invoiceId;

  @Column(nullable = false, precision = 19, scale = 4)
  private BigDecimal amount;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected VendorPaymentAllocation() {
    // for JPA
  }

  public VendorPaymentAllocation(
      UUID organizationId, UUID paymentId, UUID invoiceId, BigDecimal amount, Instant createdAt) {
    this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
    this.paymentId = Objects.requireNonNull(paymentId, "paymentId");
    this.invoiceId = Objects.requireNonNull(invoiceId, "invoiceId");
    this.amount = Objects.requireNonNull(amount, "amount");
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
  }

  public UUID getId() {
    return id;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public UUID getPaymentId() {
    return paymentId;
  }

  public UUID getInvoiceId() {
    return invoiceId;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
