package com.company.procurement.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class CatalogItemDepartmentId implements Serializable {

  @Column(name = "catalog_item_id", nullable = false)
  private UUID catalogItemId;

  @Column(name = "department_id", nullable = false)
  private UUID departmentId;

  protected CatalogItemDepartmentId() {
    // for JPA
  }

  public CatalogItemDepartmentId(UUID catalogItemId, UUID departmentId) {
    this.catalogItemId = Objects.requireNonNull(catalogItemId, "catalogItemId");
    this.departmentId = Objects.requireNonNull(departmentId, "departmentId");
  }

  public UUID getCatalogItemId() {
    return catalogItemId;
  }

  public UUID getDepartmentId() {
    return departmentId;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof CatalogItemDepartmentId that)) return false;
    return catalogItemId.equals(that.catalogItemId) && departmentId.equals(that.departmentId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(catalogItemId, departmentId);
  }
}
