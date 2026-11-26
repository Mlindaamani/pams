package com.company.procurement.purchaseorder.repositories;

import com.company.procurement.purchaseorder.domain.PurchaseOrderApproval;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaPurchaseOrderApprovalRepository
    extends JpaRepository<PurchaseOrderApproval, UUID> {

  Optional<PurchaseOrderApproval> findByOrganizationIdAndPurchaseOrderId(
      UUID organizationId, UUID purchaseOrderId);

  @Modifying
  @Query(
      "delete from PurchaseOrderApproval a"
          + " where a.organizationId = :organizationId and a.purchaseOrderId = :purchaseOrderId")
  int deleteByPurchaseOrder(
      @Param("organizationId") UUID organizationId, @Param("purchaseOrderId") UUID purchaseOrderId);
}
