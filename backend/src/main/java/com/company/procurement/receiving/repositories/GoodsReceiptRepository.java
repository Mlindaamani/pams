package com.company.procurement.receiving.repositories;

import com.company.procurement.receiving.domain.GoodsReceipt;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GoodsReceiptRepository {

  GoodsReceipt save(GoodsReceipt receipt);

  Optional<GoodsReceipt> findByIdAndOrganizationId(UUID id, UUID organizationId);

  List<GoodsReceipt> findByPurchaseOrder(UUID organizationId, UUID purchaseOrderId);
}
