package com.company.procurement.access.domain;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Permission assigned to a tenant role. */
@Entity
@Table(name = "role_permission")
public class RolePermission {

  @EmbeddedId private RolePermissionId id;

  protected RolePermission() {
    // for JPA
  }

  public RolePermission(String roleName, String permissionCode) {
    this.id = new RolePermissionId(roleName, permissionCode);
  }

  public RolePermissionId getId() {
    return id;
  }
}
