package com.company.procurement.request.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class WorkflowActionRuleId implements Serializable {

  @Column(name = "entity_type", nullable = false, length = 50)
  private String entityType;

  @Column(name = "from_status", nullable = false, length = 50)
  private String fromStatus;

  @Column(name = "action_code", nullable = false, length = 50)
  private String actionCode;

  protected WorkflowActionRuleId() {
    // for JPA
  }

  public WorkflowActionRuleId(String entityType, String fromStatus, String actionCode) {
    this.entityType = Objects.requireNonNull(entityType, "entityType");
    this.fromStatus = Objects.requireNonNull(fromStatus, "fromStatus");
    this.actionCode = Objects.requireNonNull(actionCode, "actionCode");
  }

  public String getEntityType() {
    return entityType;
  }

  public String getFromStatus() {
    return fromStatus;
  }

  public String getActionCode() {
    return actionCode;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof WorkflowActionRuleId that)) return false;
    return entityType.equals(that.entityType)
        && fromStatus.equals(that.fromStatus)
        && actionCode.equals(that.actionCode);
  }

  @Override
  public int hashCode() {
    return Objects.hash(entityType, fromStatus, actionCode);
  }
}
