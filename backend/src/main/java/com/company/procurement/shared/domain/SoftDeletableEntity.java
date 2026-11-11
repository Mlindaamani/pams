package com.company.procurement.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Tenant-owned aggregate that is hidden (never hard-deleted) once soft-deleted. */
@MappedSuperclass
public abstract class SoftDeletableEntity extends TenantEntity {

  @Column(name = "deleted_at")
  private Instant deletedAt;

  @Column(name = "deleted_by", length = 100)
  private String deletedBy;

  protected SoftDeletableEntity() {
    // for JPA
  }

  protected SoftDeletableEntity(UUID organizationId) {
    super(organizationId);
  }

  public void softDelete(Instant when, String by) {
    if (deletedAt != null) {
      return;
    }
    this.deletedAt = Objects.requireNonNull(when, "when");
    this.deletedBy = Objects.requireNonNull(by, "by");
  }

  public boolean isDeleted() {
    return deletedAt != null;
  }

  public Instant getDeletedAt() {
    return deletedAt;
  }

  public String getDeletedBy() {
    return deletedBy;
  }
}
