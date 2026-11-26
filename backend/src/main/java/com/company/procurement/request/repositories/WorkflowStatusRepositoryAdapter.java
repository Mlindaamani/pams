package com.company.procurement.request.repositories;

import com.company.procurement.request.domain.WorkflowStatusDefinition;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Repository;

@Repository
public class WorkflowStatusRepositoryAdapter implements WorkflowStatusRepository {

  private final JpaWorkflowStatusRepository jpa;

  public WorkflowStatusRepositoryAdapter(JpaWorkflowStatusRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public List<WorkflowStatusDefinition> findByEntityType(String entityType) {
    return jpa.findByIdEntityTypeOrderBySortOrderAsc(entityType);
  }

  @Override
  public Set<String> findStatusCodesByEntityType(String entityType) {
    return new LinkedHashSet<>(jpa.findStatusCodesByEntityType(entityType));
  }
}
