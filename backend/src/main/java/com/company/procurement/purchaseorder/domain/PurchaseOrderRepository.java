package com.company.procurement.purchaseorder.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PurchaseOrderRepository {

  PurchaseOrder save(PurchaseOrder order);

  void flush();

  Optional<PurchaseOrder> findByIdAndOrganizationId(UUID id, UUID organizationId);

  Optional<PurchaseOrder> findDetailByIdAndOrganizationId(UUID id, UUID organizationId);

  Optional<PurchaseOrder> findForUpdate(UUID id, UUID organizationId);

  Optional<PurchaseOrder> findActiveByOrganizationIdAndPurchaseRequestId(
      UUID organizationId, UUID purchaseRequestId);

  Page<PurchaseOrder> search(UUID organizationId, PurchaseOrderFilter filter, Pageable pageable);
}
