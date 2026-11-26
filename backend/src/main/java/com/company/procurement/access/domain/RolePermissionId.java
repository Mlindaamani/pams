package com.company.procurement.access.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class RolePermissionId implements Serializable {

  @Column(name = "role_name", nullable = false, length = 50)
  private String roleName;

  @Column(name = "permission_code", nullable = false, length = 100)
  private String permissionCode;

  protected RolePermissionId() {
    // for JPA
  }

  public RolePermissionId(String roleName, String permissionCode) {
    this.roleName = Objects.requireNonNull(roleName, "roleName");
    this.permissionCode = Objects.requireNonNull(permissionCode, "permissionCode");
  }

  public String getRoleName() {
    return roleName;
  }

  public String getPermissionCode() {
    return permissionCode;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof RolePermissionId that)) return false;
    return roleName.equals(that.roleName) && permissionCode.equals(that.permissionCode);
  }

  @Override
  public int hashCode() {
    return Objects.hash(roleName, permissionCode);
  }
}
