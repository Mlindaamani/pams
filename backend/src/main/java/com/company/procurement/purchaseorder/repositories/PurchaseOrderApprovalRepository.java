package com.company.procurement.purchaseorder.repositories;

import com.company.procurement.purchaseorder.domain.PurchaseOrderApproval;
import java.util.Optional;
import java.util.UUID;

/** Persistence port for purchase-order approvals. */
public interface PurchaseOrderApprovalRepository {

  Optional<PurchaseOrderApproval> findByOrganizationIdAndPurchaseOrderId(
      UUID organizationId, UUID purchaseOrderId);

  PurchaseOrderApproval save(PurchaseOrderApproval approval);

  void delete(PurchaseOrderApproval approval);

  void deleteByPurchaseOrder(UUID organizationId, UUID purchaseOrderId);
}
