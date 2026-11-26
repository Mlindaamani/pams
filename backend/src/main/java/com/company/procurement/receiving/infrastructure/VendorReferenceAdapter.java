package com.company.procurement.receiving.infrastructure;

import com.company.procurement.purchaseorder.repositories.PurchaseOrderRepository;
import com.company.procurement.receiving.repositories.VendorInvoiceRepository;
import com.company.procurement.vendor.repositories.VendorReferencePort;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class VendorReferenceAdapter implements VendorReferencePort {

  private final PurchaseOrderRepository purchaseOrders;
  private final VendorInvoiceRepository invoices;

  public VendorReferenceAdapter(
      PurchaseOrderRepository purchaseOrders, VendorInvoiceRepository invoices) {
    this.purchaseOrders = purchaseOrders;
    this.invoices = invoices;
  }

  @Override
  public long referenceCount(UUID organizationId, UUID vendorId) {
    return purchaseOrders.countByVendor(organizationId, vendorId)
        + invoices.countByVendor(organizationId, vendorId);
  }
}
