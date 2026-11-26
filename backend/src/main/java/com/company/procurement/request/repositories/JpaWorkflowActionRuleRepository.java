package com.company.procurement.request.repositories;

import com.company.procurement.request.domain.WorkflowActionRule;
import com.company.procurement.request.domain.WorkflowActionRuleId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaWorkflowActionRuleRepository
    extends JpaRepository<WorkflowActionRule, WorkflowActionRuleId> {

  List<WorkflowActionRule> findByIdEntityTypeOrderByIdFromStatusAscIdActionCodeAsc(
      String entityType);

  List<WorkflowActionRule> findByIdEntityTypeAndIdFromStatusOrderByIdActionCodeAsc(
      String entityType, String fromStatus);

  @Modifying
  @Query("delete from WorkflowActionRule r where r.id.entityType = :entityType")
  int deleteByEntityType(@Param("entityType") String entityType);
}
