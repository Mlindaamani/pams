package com.company.procurement.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.util.Objects;
import java.util.UUID;

/** Base class for aggregates owned by a tenant. The tenant id is immutable after creation. */
@MappedSuperclass
public abstract class TenantEntity extends AuditableEntity {

  @Column(name = "organization_id", nullable = false, updatable = false)
  private UUID organizationId;

  protected TenantEntity() {
    // for JPA
  }

  protected TenantEntity(UUID organizationId) {
    this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
  }

  public UUID getOrganizationId() {
    return organizationId;
  }
}
