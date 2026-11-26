package com.company.procurement.access.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Permission catalogue entry. */
@Entity
@Table(name = "permission")
public class Permission {

  @Id
  @Column(nullable = false, length = 100)
  private String code;

  @Column(nullable = false, length = 300)
  private String description;

  protected Permission() {
    // for JPA
  }

  public String getCode() {
    return code;
  }

  public String getDescription() {
    return description;
  }
}
