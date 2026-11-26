package com.company.procurement.access.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Tenant role catalogue entry. */
@Entity
@Table(name = "role_model")
public class RoleDefinition {

  @Id
  @Column(name = "role_name", nullable = false, length = 50)
  private String roleName;

  @Column(name = "display_name", nullable = false, length = 100)
  private String displayName;

  @Column(nullable = false, length = 500)
  private String description;

  protected RoleDefinition() {
    // for JPA
  }

  public String getRoleName() {
    return roleName;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getDescription() {
    return description;
  }
}
