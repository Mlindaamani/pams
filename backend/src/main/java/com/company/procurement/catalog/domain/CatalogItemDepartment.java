package com.company.procurement.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** Department that can see a selected-audience catalog item. */
@Entity
@Table(name = "catalog_item_department")
@EntityListeners(AuditingEntityListener.class)
public class CatalogItemDepartment {

  @EmbeddedId private CatalogItemDepartmentId id;

  @Column(name = "organization_id", nullable = false, updatable = false)
  private UUID organizationId;

  @Column(name = "created_at", nullable = false, updatable = false)
  @CreatedDate
  private Instant createdAt;

  @Column(name = "created_by", nullable = false, updatable = false, length = 100)
  @CreatedBy
  private String createdBy;

  protected CatalogItemDepartment() {
    // for JPA
  }

  public CatalogItemDepartment(UUID organizationId, UUID catalogItemId, UUID departmentId) {
    this.organizationId = organizationId;
    this.id = new CatalogItemDepartmentId(catalogItemId, departmentId);
  }

  public CatalogItemDepartmentId getId() {
    return id;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }
}
