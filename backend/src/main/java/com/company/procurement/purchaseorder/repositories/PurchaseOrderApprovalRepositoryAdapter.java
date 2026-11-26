package com.company.procurement.purchaseorder.repositories;

import com.company.procurement.purchaseorder.domain.PurchaseOrderApproval;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class PurchaseOrderApprovalRepositoryAdapter implements PurchaseOrderApprovalRepository {

  private final JpaPurchaseOrderApprovalRepository jpa;

  public PurchaseOrderApprovalRepositoryAdapter(JpaPurchaseOrderApprovalRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public Optional<PurchaseOrderApproval> findByOrganizationIdAndPurchaseOrderId(
      UUID organizationId, UUID purchaseOrderId) {
    return jpa.findByOrganizationIdAndPurchaseOrderId(organizationId, purchaseOrderId);
  }

  @Override
  public PurchaseOrderApproval save(PurchaseOrderApproval approval) {
    return jpa.save(approval);
  }

  @Override
  public void delete(PurchaseOrderApproval approval) {
    jpa.delete(approval);
  }

  @Override
  public void deleteByPurchaseOrder(UUID organizationId, UUID purchaseOrderId) {
    jpa.deleteByPurchaseOrder(organizationId, purchaseOrderId);
  }
}
