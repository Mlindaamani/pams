package com.company.procurement.purchaseorder.repositories;

import com.company.procurement.purchaseorder.domain.PurchaseOrderApprovalStep;
import java.util.List;
import java.util.UUID;

/** Persistence port for purchase-order approval steps. */
public interface PurchaseOrderApprovalStepRepository {

  List<PurchaseOrderApprovalStep> findByApprovalId(UUID approvalId);

  PurchaseOrderApprovalStep save(PurchaseOrderApprovalStep step);

  List<PurchaseOrderApprovalStep> saveAll(List<PurchaseOrderApprovalStep> steps);

  void deleteByApprovalId(UUID approvalId);
}
