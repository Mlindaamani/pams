package com.company.procurement.approval.infrastructure;

import com.company.procurement.approval.domain.ApprovalInstance;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaApprovalInstanceRepository extends JpaRepository<ApprovalInstance, UUID> {

  Optional<ApprovalInstance> findByOrganizationIdAndPurchaseRequestId(
      UUID organizationId, UUID purchaseRequestId);

  @EntityGraph(attributePaths = "steps")
  @Query(
      "select i from ApprovalInstance i where i.organizationId = :organizationId"
          + " and i.purchaseRequestId = :purchaseRequestId")
  Optional<ApprovalInstance> findDetail(
      @Param("organizationId") UUID organizationId,
      @Param("purchaseRequestId") UUID purchaseRequestId);

  @EntityGraph(attributePaths = "steps")
  @Query("select i from ApprovalInstance i where i.id = :id and i.organizationId = :organizationId")
  Optional<ApprovalInstance> findDetailById(
      @Param("id") UUID id, @Param("organizationId") UUID organizationId);

  @Modifying(flushAutomatically = true)
  @Query(
      "delete from ApprovalInstance i where i.organizationId = :organizationId"
          + " and i.purchaseRequestId = :purchaseRequestId")
  int deleteByRequest(
      @Param("organizationId") UUID organizationId,
      @Param("purchaseRequestId") UUID purchaseRequestId);
}
