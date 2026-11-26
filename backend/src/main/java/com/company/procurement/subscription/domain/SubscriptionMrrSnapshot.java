package com.company.procurement.subscription.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/** Daily platform MRR snapshot. */
@Entity
@Table(name = "subscription_mrr_snapshot")
public class SubscriptionMrrSnapshot {

  @Id @GeneratedValue private java.util.UUID id;

  @Column(name = "snapshot_date", nullable = false, unique = true)
  private LocalDate snapshotDate;

  @Column(nullable = false, precision = 19, scale = 4)
  private BigDecimal mrr;

  @Column(nullable = false, precision = 19, scale = 4)
  private BigDecimal arr;

  @Column(name = "paying_tenants", nullable = false)
  private int payingTenants;

  @Column(name = "trialing_tenants", nullable = false)
  private int trialingTenants;

  @Column(name = "past_due_tenants", nullable = false)
  private int pastDueTenants;

  @Column(name = "canceled_tenants", nullable = false)
  private int canceledTenants;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "created_by", nullable = false, updatable = false, length = 100)
  private String createdBy;

  protected SubscriptionMrrSnapshot() {
    // for JPA
  }

  public SubscriptionMrrSnapshot(
      LocalDate snapshotDate,
      BigDecimal mrr,
      BigDecimal arr,
      int payingTenants,
      int trialingTenants,
      int pastDueTenants,
      int canceledTenants,
      Instant createdAt) {
    this.snapshotDate = Objects.requireNonNull(snapshotDate, "snapshotDate");
    this.mrr = Objects.requireNonNull(mrr, "mrr");
    this.arr = Objects.requireNonNull(arr, "arr");
    this.payingTenants = payingTenants;
    this.trialingTenants = trialingTenants;
    this.pastDueTenants = pastDueTenants;
    this.canceledTenants = canceledTenants;
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    this.createdBy = "system";
  }

  public void update(
      BigDecimal mrr,
      BigDecimal arr,
      int payingTenants,
      int trialingTenants,
      int pastDueTenants,
      int canceledTenants) {
    this.mrr = mrr;
    this.arr = arr;
    this.payingTenants = payingTenants;
    this.trialingTenants = trialingTenants;
    this.pastDueTenants = pastDueTenants;
    this.canceledTenants = canceledTenants;
  }

  public java.util.UUID getId() {
    return id;
  }

  public LocalDate getSnapshotDate() {
    return snapshotDate;
  }

  public BigDecimal getMrr() {
    return mrr;
  }

  public BigDecimal getArr() {
    return arr;
  }

  public int getPayingTenants() {
    return payingTenants;
  }

  public int getTrialingTenants() {
    return trialingTenants;
  }

  public int getPastDueTenants() {
    return pastDueTenants;
  }

  public int getCanceledTenants() {
    return canceledTenants;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
