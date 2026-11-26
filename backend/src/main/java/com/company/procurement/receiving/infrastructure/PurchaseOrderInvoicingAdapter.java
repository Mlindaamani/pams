package com.company.procurement.receiving.infrastructure;

import com.company.procurement.purchaseorder.domain.PurchaseOrder;
import com.company.procurement.purchaseorder.domain.PurchaseOrderLine;
import com.company.procurement.purchaseorder.repositories.PurchaseOrderInvoicingPort;
import com.company.procurement.receiving.repositories.VendorInvoiceRepository;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class PurchaseOrderInvoicingAdapter implements PurchaseOrderInvoicingPort {

  private final VendorInvoiceRepository invoices;

  public PurchaseOrderInvoicingAdapter(VendorInvoiceRepository invoices) {
    this.invoices = invoices;
  }

  @Override
  public boolean hasInvoiceableRemaining(PurchaseOrder order) {
    Map<UUID, BigDecimal> invoiced =
        invoices.invoicedQuantities(order.getOrganizationId(), order.getId()).stream()
            .collect(
                Collectors.toMap(
                    VendorInvoiceRepository.InvoicedQuantity::purchaseOrderLineId,
                    VendorInvoiceRepository.InvoicedQuantity::quantity));
    for (PurchaseOrderLine line : order.getLines()) {
      BigDecimal alreadyInvoiced = invoiced.getOrDefault(line.getId(), BigDecimal.ZERO);
      if (line.getReceivedQuantity().compareTo(alreadyInvoiced) > 0) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean hasInvoices(PurchaseOrder order) {
    return invoices.countByPurchaseOrder(order.getOrganizationId(), order.getId()) > 0;
  }
}
