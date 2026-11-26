package com.company.procurement.purchaseorder.repositories;

import com.company.procurement.purchaseorder.domain.PurchaseOrder;

/** Read-only invoice state used by purchase-order actions. */
public interface PurchaseOrderInvoicingPort {

  boolean hasInvoiceableRemaining(PurchaseOrder order);

  boolean hasInvoices(PurchaseOrder order);
}
