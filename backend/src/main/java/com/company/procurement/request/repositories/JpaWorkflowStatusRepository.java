package com.company.procurement.request.repositories;

import com.company.procurement.request.domain.WorkflowStatusDefinition;
import com.company.procurement.request.domain.WorkflowStatusId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaWorkflowStatusRepository
    extends JpaRepository<WorkflowStatusDefinition, WorkflowStatusId> {

  List<WorkflowStatusDefinition> findByIdEntityTypeOrderBySortOrderAsc(String entityType);

  @Query(
      "select s.id.statusCode from WorkflowStatusDefinition s where s.id.entityType = :entityType")
  List<String> findStatusCodesByEntityType(@Param("entityType") String entityType);
}
