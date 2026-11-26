package com.company.procurement.purchaseorder.repositories;

import com.company.procurement.purchaseorder.domain.PurchaseOrder;
import com.company.procurement.purchaseorder.domain.PurchaseOrderFilter;
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

  long countNonDraft(UUID organizationId);

  long countByVendor(UUID organizationId, UUID vendorId);
}
