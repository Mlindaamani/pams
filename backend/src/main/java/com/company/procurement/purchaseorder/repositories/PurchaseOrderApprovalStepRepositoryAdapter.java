package com.company.procurement.purchaseorder.repositories;

import com.company.procurement.purchaseorder.domain.PurchaseOrderApprovalStep;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class PurchaseOrderApprovalStepRepositoryAdapter
    implements PurchaseOrderApprovalStepRepository {

  private final JpaPurchaseOrderApprovalStepRepository jpa;

  public PurchaseOrderApprovalStepRepositoryAdapter(JpaPurchaseOrderApprovalStepRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public List<PurchaseOrderApprovalStep> findByApprovalId(UUID approvalId) {
    return jpa.findByApprovalIdOrderBySequenceAscIdAsc(approvalId);
  }

  @Override
  public PurchaseOrderApprovalStep save(PurchaseOrderApprovalStep step) {
    return jpa.save(step);
  }

  @Override
  public List<PurchaseOrderApprovalStep> saveAll(List<PurchaseOrderApprovalStep> steps) {
    return jpa.saveAll(steps);
  }

  @Override
  public void deleteByApprovalId(UUID approvalId) {
    jpa.deleteByApprovalId(approvalId);
  }
}
