package com.company.procurement.request.infrastructure;

import com.company.procurement.request.repositories.WorkflowActionRuleRepository;
import com.company.procurement.request.services.WorkflowActionRules;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class WorkflowActionRulesAdapter implements WorkflowActionRules {

  private final WorkflowActionRuleRepository rules;

  public WorkflowActionRulesAdapter(WorkflowActionRuleRepository rules) {
    this.rules = rules;
  }

  @Override
  public List<Rule> find(String entityType, String fromStatus) {
    return rules.findByEntityTypeAndFromStatus(entityType, fromStatus).stream()
        .map(rule -> new Rule(rule.getId().getActionCode(), rule.getRequiredPermission()))
        .toList();
  }
}
