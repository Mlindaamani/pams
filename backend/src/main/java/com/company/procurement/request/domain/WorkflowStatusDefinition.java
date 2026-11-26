package com.company.procurement.request.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Configurable workflow status for an entity type. */
@Entity
@Table(name = "workflow_status")
public class WorkflowStatusDefinition {

  @EmbeddedId private WorkflowStatusId id;

  @Column(name = "display_name", nullable = false, length = 100)
  private String displayName;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Column(nullable = false)
  private boolean terminal;

  protected WorkflowStatusDefinition() {
    // for JPA
  }

  public WorkflowStatusId getId() {
    return id;
  }

  public String getDisplayName() {
    return displayName;
  }

  public int getSortOrder() {
    return sortOrder;
  }

  public boolean isTerminal() {
    return terminal;
  }
}
