package com.company.procurement.approval.infrastructure;

import com.company.procurement.approval.domain.ApprovalStep;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaApprovalStepRepository extends JpaRepository<ApprovalStep, UUID> {

  @EntityGraph(attributePaths = "instance")
  Optional<ApprovalStep> findByIdAndOrganizationId(UUID id, UUID organizationId);

  @Query(
      """
      select s from ApprovalStep s
       where s.organizationId = :organizationId
         and s.status = com.company.procurement.approval.domain.ApprovalStepStatus.PENDING
         and s.slaDueAt < :now
       order by s.slaDueAt asc
      """)
  List<ApprovalStep> findDueSteps(
      @Param("organizationId") UUID organizationId, @Param("now") Instant now);

  @Query(
      """
      select s from ApprovalStep s
       join fetch s.instance i
       where s.organizationId = :organizationId
         and s.status = com.company.procurement.approval.domain.ApprovalStepStatus.PENDING
         and i.status = com.company.procurement.approval.domain.ApprovalInstanceStatus.IN_PROGRESS
         and i.currentSequence = s.sequence
       order by s.slaDueAt asc
      """)
  List<ApprovalStep> findCurrentPendingSteps(@Param("organizationId") UUID organizationId);
}
