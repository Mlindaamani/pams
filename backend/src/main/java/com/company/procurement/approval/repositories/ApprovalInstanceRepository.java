package com.company.procurement.approval.repositories;

import com.company.procurement.approval.domain.ApprovalInstance;
import java.util.Optional;
import java.util.UUID;

public interface ApprovalInstanceRepository {

  ApprovalInstance save(ApprovalInstance instance);

  Optional<ApprovalInstance> findByOrganizationIdAndPurchaseRequestId(
      UUID organizationId, UUID purchaseRequestId);

  Optional<ApprovalInstance> findDetailByOrganizationIdAndPurchaseRequestId(
      UUID organizationId, UUID purchaseRequestId);

  Optional<ApprovalInstance> findDetailByIdAndOrganizationId(UUID id, UUID organizationId);

  void delete(ApprovalInstance instance);

  void deleteByOrganizationIdAndPurchaseRequestId(UUID organizationId, UUID purchaseRequestId);
}
