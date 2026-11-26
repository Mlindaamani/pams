package com.company.procurement.request.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Permission required to perform a workflow action from a status. */
@Entity
@Table(name = "workflow_action_rule")
public class WorkflowActionRule {

  @EmbeddedId private WorkflowActionRuleId id;

  @Column(name = "required_permission", nullable = false, length = 100)
  private String requiredPermission;

  protected WorkflowActionRule() {
    // for JPA
  }

  public WorkflowActionRule(
      String entityType, String fromStatus, String actionCode, String requiredPermission) {
    this.id = new WorkflowActionRuleId(entityType, fromStatus, actionCode);
    this.requiredPermission = requiredPermission;
  }

  public WorkflowActionRuleId getId() {
    return id;
  }

  public String getRequiredPermission() {
    return requiredPermission;
  }
}
