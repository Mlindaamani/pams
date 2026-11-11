package com.company.procurement.request.application;

import java.util.List;

/** Read port for database-driven workflow action rules. */
public interface WorkflowActionRules {

  List<Rule> find(String entityType, String fromStatus);

  record Rule(String actionCode, String requiredPermission) {}
}
