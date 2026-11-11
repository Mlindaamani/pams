package com.company.procurement.purchaseorder.application;

import com.company.procurement.purchaseorder.domain.PurchaseOrderStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Public purchase-order data required to perform invoice matching. */
public record InvoiceMatchData(
    UUID purchaseOrderId,
    String poNumber,
    UUID vendorId,
    String currency,
    PurchaseOrderStatus status,
    List<Line> lines) {

  public record Line(
      UUID id,
      String description,
      String unit,
      BigDecimal orderedQuantity,
      BigDecimal receivedQuantity,
      BigDecimal unitPrice) {}
}
