package com.company.procurement.request.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class WorkflowStatusId implements Serializable {

  @Column(name = "entity_type", nullable = false, length = 50)
  private String entityType;

  @Column(name = "status_code", nullable = false, length = 50)
  private String statusCode;

  protected WorkflowStatusId() {
    // for JPA
  }

  public WorkflowStatusId(String entityType, String statusCode) {
    this.entityType = Objects.requireNonNull(entityType, "entityType");
    this.statusCode = Objects.requireNonNull(statusCode, "statusCode");
  }

  public String getEntityType() {
    return entityType;
  }

  public String getStatusCode() {
    return statusCode;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof WorkflowStatusId that)) return false;
    return entityType.equals(that.entityType) && statusCode.equals(that.statusCode);
  }

  @Override
  public int hashCode() {
    return Objects.hash(entityType, statusCode);
  }
}
