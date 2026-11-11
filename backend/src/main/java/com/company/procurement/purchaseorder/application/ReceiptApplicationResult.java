package com.company.procurement.purchaseorder.application;

import com.company.procurement.purchaseorder.domain.PurchaseOrderStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Public purchase-order snapshot returned after goods are applied to an order. */
public record ReceiptApplicationResult(
    UUID purchaseOrderId, PurchaseOrderStatus status, long version, List<Line> lines) {

  public record Line(
      UUID id,
      String description,
      String unit,
      BigDecimal orderedQuantity,
      BigDecimal receivedQuantity) {}
}
