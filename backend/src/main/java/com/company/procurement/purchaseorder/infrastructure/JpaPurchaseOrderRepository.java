package com.company.procurement.purchaseorder.infrastructure;

import com.company.procurement.purchaseorder.domain.PurchaseOrder;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaPurchaseOrderRepository
    extends JpaRepository<PurchaseOrder, UUID>, JpaSpecificationExecutor<PurchaseOrder> {

  Optional<PurchaseOrder> findByIdAndOrganizationId(UUID id, UUID organizationId);

  Optional<PurchaseOrder> findByOrganizationIdAndPurchaseRequestIdAndStatusNot(
      UUID organizationId,
      UUID purchaseRequestId,
      com.company.procurement.purchaseorder.domain.PurchaseOrderStatus status);

  @EntityGraph(attributePaths = "lines")
  @Query("select o from PurchaseOrder o where o.id = :id and o.organizationId = :organizationId")
  Optional<PurchaseOrder> findDetail(
      @Param("id") UUID id, @Param("organizationId") UUID organizationId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select o from PurchaseOrder o where o.id = :id and o.organizationId = :organizationId")
  Optional<PurchaseOrder> findForUpdate(
      @Param("id") UUID id, @Param("organizationId") UUID organizationId);
}
