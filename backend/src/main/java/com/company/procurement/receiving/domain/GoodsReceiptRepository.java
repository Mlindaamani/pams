package com.company.procurement.receiving.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GoodsReceiptRepository {

  GoodsReceipt save(GoodsReceipt receipt);

  Optional<GoodsReceipt> findByIdAndOrganizationId(UUID id, UUID organizationId);

  List<GoodsReceipt> findByPurchaseOrder(UUID organizationId, UUID purchaseOrderId);
}
