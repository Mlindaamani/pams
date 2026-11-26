package com.company.procurement.request.repositories;

import com.company.procurement.request.domain.WorkflowActionRule;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class WorkflowActionRuleRepositoryAdapter implements WorkflowActionRuleRepository {

  private final JpaWorkflowActionRuleRepository jpa;

  public WorkflowActionRuleRepositoryAdapter(JpaWorkflowActionRuleRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public List<WorkflowActionRule> findByEntityType(String entityType) {
    return jpa.findByIdEntityTypeOrderByIdFromStatusAscIdActionCodeAsc(entityType);
  }

  @Override
  public List<WorkflowActionRule> findByEntityTypeAndFromStatus(
      String entityType, String fromStatus) {
    return jpa.findByIdEntityTypeAndIdFromStatusOrderByIdActionCodeAsc(entityType, fromStatus);
  }

  @Override
  public List<WorkflowActionRule> saveAll(List<WorkflowActionRule> rules) {
    return jpa.saveAll(rules);
  }

  @Override
  public void deleteByEntityType(String entityType) {
    jpa.deleteByEntityType(entityType);
  }
}
