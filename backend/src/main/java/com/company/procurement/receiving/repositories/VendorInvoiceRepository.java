package com.company.procurement.receiving.repositories;

import com.company.procurement.receiving.domain.VendorInvoice;
import com.company.procurement.receiving.domain.VendorInvoiceFilter;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface VendorInvoiceRepository {

  VendorInvoice save(VendorInvoice invoice);

  Optional<VendorInvoice> findDetailByIdAndOrganizationId(UUID id, UUID organizationId);

  Optional<VendorInvoice> findByIdAndOrganizationId(UUID id, UUID organizationId);

  Optional<VendorInvoice> findForUpdate(UUID id, UUID organizationId);

  List<VendorInvoice> findAllByIds(java.util.Collection<UUID> ids);

  boolean existsActiveByVendorAndNumber(UUID organizationId, UUID vendorId, String invoiceNumber);

  Page<VendorInvoice> search(UUID organizationId, VendorInvoiceFilter filter, Pageable pageable);

  List<InvoicedQuantity> invoicedQuantities(UUID organizationId, UUID purchaseOrderId);

  long countByPurchaseOrder(UUID organizationId, UUID purchaseOrderId);

  long countByVendor(UUID organizationId, UUID vendorId);

  List<AgingProjection> aging(UUID organizationId);

  record AgingProjection(
      UUID vendorId,
      String vendorName,
      String currency,
      BigDecimal currentAmount,
      BigDecimal days1To30,
      BigDecimal days31To60,
      BigDecimal days61To90,
      BigDecimal daysOver90,
      BigDecimal totalOutstanding,
      long invoiceCount) {}

  record InvoicedQuantity(UUID purchaseOrderLineId, BigDecimal quantity) {}
}
