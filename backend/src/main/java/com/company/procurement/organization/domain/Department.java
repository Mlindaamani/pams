package com.company.procurement.organization.domain;

import com.company.procurement.shared.domain.SoftDeletableEntity;
import com.company.procurement.shared.domain.TenantFilter;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "department")
@SQLRestriction("deleted_at IS NULL")
@Filter(name = TenantFilter.NAME, condition = "organization_id = :organizationId")
public class Department extends SoftDeletableEntity {

  @Column(name = "name", nullable = false, length = 200)
  private String name;

  protected Department() {
    // for JPA
  }

  public Department(UUID organizationId, String name) {
    super(organizationId);
    this.name = requireText(name);
  }

  public void rename(String newName) {
    this.name = requireText(newName);
  }

  public String getName() {
    return name;
  }

  private static String requireText(String value) {
    Objects.requireNonNull(value, "name");
    String trimmed = value.trim();
    if (trimmed.isEmpty()) {
      throw new IllegalArgumentException("name must not be blank");
    }
    return trimmed;
  }
}
