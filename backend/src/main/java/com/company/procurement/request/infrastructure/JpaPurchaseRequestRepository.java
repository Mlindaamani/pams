package com.company.procurement.request.infrastructure;

import com.company.procurement.request.domain.PurchaseRequest;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaPurchaseRequestRepository
    extends JpaRepository<PurchaseRequest, UUID>, JpaSpecificationExecutor<PurchaseRequest> {

  Optional<PurchaseRequest> findByIdAndOrganizationId(UUID id, UUID organizationId);

  @EntityGraph(attributePaths = "lines")
  @Query("select r from PurchaseRequest r where r.id = :id and r.organizationId = :organizationId")
  Optional<PurchaseRequest> findDetail(
      @Param("id") UUID id, @Param("organizationId") UUID organizationId);
}
