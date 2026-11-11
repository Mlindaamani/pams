package com.company.procurement.organization.domain;

import com.company.procurement.shared.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/** Tenant root aggregate. Organizations are not themselves tenant-scoped. */
@Entity
@Table(name = "organization")
public class Organization extends AuditableEntity {

  @Column(name = "name", nullable = false, length = 200)
  private String name;

  @Column(name = "slug", nullable = false, length = 100)
  private String slug;

  @Column(name = "invoice_tolerance_percent", nullable = false, precision = 5, scale = 2)
  private BigDecimal invoiceTolerancePercent = new BigDecimal("2.00");

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private OrganizationStatus status = OrganizationStatus.ACTIVE;

  @Column(name = "suspended_at")
  private Instant suspendedAt;

  @Column(name = "suspended_by", length = 200)
  private String suspendedBy;

  protected Organization() {
    // for JPA
  }

  public Organization(String name, String slug) {
    this.name = requireText(name, "name");
    this.slug = requireText(slug, "slug");
  }

  public void suspend(Instant now, String actor) {
    this.status = OrganizationStatus.SUSPENDED;
    this.suspendedAt = Objects.requireNonNull(now, "now");
    this.suspendedBy = Objects.requireNonNull(actor, "actor");
  }

  public void activate() {
    this.status = OrganizationStatus.ACTIVE;
    this.suspendedAt = null;
    this.suspendedBy = null;
  }

  public void changeInvoiceTolerance(BigDecimal tolerancePercent) {
    Objects.requireNonNull(tolerancePercent, "invoiceTolerancePercent");
    if (tolerancePercent.signum() < 0 || tolerancePercent.compareTo(new BigDecimal("100")) > 0) {
      throw new IllegalArgumentException("invoice tolerance must be between 0 and 100 percent");
    }
    this.invoiceTolerancePercent = tolerancePercent;
  }

  public void rename(String newName) {
    this.name = requireText(newName, "name");
  }

  public void changeSlug(String newSlug) {
    this.slug = requireText(newSlug, "slug");
  }

  public String getName() {
    return name;
  }

  public String getSlug() {
    return slug;
  }

  public BigDecimal getInvoiceTolerancePercent() {
    return invoiceTolerancePercent;
  }

  public OrganizationStatus getStatus() {
    return status;
  }

  public Instant getSuspendedAt() {
    return suspendedAt;
  }

  public String getSuspendedBy() {
    return suspendedBy;
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
