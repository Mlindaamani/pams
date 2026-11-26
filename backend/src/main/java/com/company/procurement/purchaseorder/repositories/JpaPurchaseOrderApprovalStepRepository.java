package com.company.procurement.purchaseorder.repositories;

import com.company.procurement.purchaseorder.domain.PurchaseOrderApprovalStep;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaPurchaseOrderApprovalStepRepository
    extends JpaRepository<PurchaseOrderApprovalStep, UUID> {

  List<PurchaseOrderApprovalStep> findByApprovalIdOrderBySequenceAscIdAsc(UUID approvalId);

  @Modifying
  @Query("delete from PurchaseOrderApprovalStep s where s.approvalId = :approvalId")
  int deleteByApprovalId(@Param("approvalId") UUID approvalId);
}
