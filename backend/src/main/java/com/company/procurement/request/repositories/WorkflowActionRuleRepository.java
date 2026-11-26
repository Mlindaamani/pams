package com.company.procurement.request.repositories;

import com.company.procurement.request.domain.WorkflowActionRule;
import java.util.List;

/** Persistence port for workflow action rules. */
public interface WorkflowActionRuleRepository {

  List<WorkflowActionRule> findByEntityType(String entityType);

  List<WorkflowActionRule> findByEntityTypeAndFromStatus(String entityType, String fromStatus);

  List<WorkflowActionRule> saveAll(List<WorkflowActionRule> rules);

  void deleteByEntityType(String entityType);
}
