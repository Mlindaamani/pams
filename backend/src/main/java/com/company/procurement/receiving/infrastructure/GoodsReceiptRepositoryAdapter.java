package com.company.procurement.receiving.infrastructure;

import com.company.procurement.receiving.domain.GoodsReceipt;
import com.company.procurement.receiving.domain.GoodsReceiptRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class GoodsReceiptRepositoryAdapter implements GoodsReceiptRepository {

  private final JpaGoodsReceiptRepository jpa;

  public GoodsReceiptRepositoryAdapter(JpaGoodsReceiptRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public GoodsReceipt save(GoodsReceipt receipt) {
    return jpa.save(receipt);
  }

  @Override
  public Optional<GoodsReceipt> findByIdAndOrganizationId(UUID id, UUID organizationId) {
    return jpa.findDetail(id, organizationId);
  }

  @Override
  public List<GoodsReceipt> findByPurchaseOrder(UUID organizationId, UUID purchaseOrderId) {
    return jpa.findByPurchaseOrder(organizationId, purchaseOrderId);
  }
}
