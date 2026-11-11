package com.company.procurement.receiving.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface VendorInvoiceRepository {

  VendorInvoice save(VendorInvoice invoice);

  Optional<VendorInvoice> findDetailByIdAndOrganizationId(UUID id, UUID organizationId);

  boolean existsActiveByVendorAndNumber(UUID organizationId, UUID vendorId, String invoiceNumber);

  Page<VendorInvoice> search(UUID organizationId, VendorInvoiceFilter filter, Pageable pageable);

  List<InvoicedQuantity> invoicedQuantities(UUID organizationId, UUID purchaseOrderId);

  record InvoicedQuantity(UUID purchaseOrderLineId, BigDecimal quantity) {}
}
