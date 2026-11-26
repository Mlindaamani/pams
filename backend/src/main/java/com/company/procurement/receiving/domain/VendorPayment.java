package com.company.procurement.receiving.domain;

import com.company.procurement.shared.domain.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** Scheduled or settled payment to a vendor. */
@Entity
@Table(name = "vendor_payment")
public class VendorPayment extends TenantEntity {

  @Column(name = "payment_number", nullable = false, updatable = false, length = 40)
  private String paymentNumber;

  @Column(name = "vendor_id", nullable = false, updatable = false)
  private UUID vendorId;

  @Column(nullable = false, length = 30)
  private String status;

  @Column(name = "payment_date", nullable = false)
  private LocalDate paymentDate;

  @Column(nullable = false, length = 30)
  private String method;

  @Column(length = 200)
  private String reference;

  @Column(nullable = false, length = 3)
  private String currency;

  @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
  private BigDecimal totalAmount;

  @Column(length = 1000)
  private String notes;

  @Column(name = "paid_at")
  private Instant paidAt;

  protected VendorPayment() {
    // for JPA
  }

  public VendorPayment(
      UUID organizationId,
      String paymentNumber,
      UUID vendorId,
      LocalDate paymentDate,
      String method,
      String reference,
      String currency,
      BigDecimal totalAmount,
      String notes) {
    super(organizationId);
    this.paymentNumber = Objects.requireNonNull(paymentNumber, "paymentNumber");
    this.vendorId = Objects.requireNonNull(vendorId, "vendorId");
    this.status = "SCHEDULED";
    this.paymentDate = Objects.requireNonNull(paymentDate, "paymentDate");
    this.method = Objects.requireNonNull(method, "method");
    this.reference = reference;
    this.currency = Objects.requireNonNull(currency, "currency");
    this.totalAmount = Objects.requireNonNull(totalAmount, "totalAmount");
    this.notes = notes;
  }

  public void approve() {
    this.status = "APPROVED";
  }

  public void markPaid(Instant now) {
    this.status = "PAID";
    this.paidAt = Objects.requireNonNull(now, "now");
  }

  public void cancel() {
    this.status = "CANCELED";
  }

  public String getPaymentNumber() {
    return paymentNumber;
  }

  public UUID getVendorId() {
    return vendorId;
  }

  public String getStatus() {
    return status;
  }

  public LocalDate getPaymentDate() {
    return paymentDate;
  }

  public String getMethod() {
    return method;
  }

  public String getReference() {
    return reference;
  }

  public String getCurrency() {
    return currency;
  }

  public BigDecimal getTotalAmount() {
    return totalAmount;
  }

  public String getNotes() {
    return notes;
  }

  public Instant getPaidAt() {
    return paidAt;
  }
}
