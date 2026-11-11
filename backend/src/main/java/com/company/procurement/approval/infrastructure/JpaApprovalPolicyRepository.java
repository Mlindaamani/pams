package com.company.procurement.approval.infrastructure;

import com.company.procurement.approval.domain.ApprovalPolicy;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaApprovalPolicyRepository
    extends JpaRepository<ApprovalPolicy, UUID>, JpaSpecificationExecutor<ApprovalPolicy> {

  Optional<ApprovalPolicy> findByIdAndOrganizationId(UUID id, UUID organizationId);

  @EntityGraph(attributePaths = "steps")
  @Query(
      """
      select p from ApprovalPolicy p
       where p.organizationId = :organizationId
         and p.entityType = :entityType
         and p.active = true
         and p.amountMin <= :amount
         and (p.amountMax is null or :amount < p.amountMax)
       order by p.amountMin desc
      """)
  Optional<ApprovalPolicy> findActiveByAmount(
      @Param("organizationId") UUID organizationId,
      @Param("entityType") String entityType,
      @Param("amount") BigDecimal amount);
}
