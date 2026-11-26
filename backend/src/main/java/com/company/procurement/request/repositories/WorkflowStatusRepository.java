package com.company.procurement.request.repositories;

import com.company.procurement.request.domain.WorkflowStatusDefinition;
import java.util.List;
import java.util.Set;

/** Persistence port for workflow status definitions. */
public interface WorkflowStatusRepository {

  List<WorkflowStatusDefinition> findByEntityType(String entityType);

  Set<String> findStatusCodesByEntityType(String entityType);
}
