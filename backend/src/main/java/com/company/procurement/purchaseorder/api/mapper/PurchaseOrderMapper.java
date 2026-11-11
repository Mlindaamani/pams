package com.company.procurement.purchaseorder.api.mapper;

import com.company.procurement.purchaseorder.api.dto.PurchaseOrderLineResponse;
import com.company.procurement.purchaseorder.api.dto.PurchaseOrderResponse;
import com.company.procurement.purchaseorder.domain.PurchaseOrder;
import com.company.procurement.purchaseorder.domain.PurchaseOrderLine;
import com.company.procurement.shared.dto.MoneyDto;
import org.springframework.stereotype.Component;

@Component
public class PurchaseOrderMapper {

  public PurchaseOrderResponse toResponse(PurchaseOrder order) {
    return new PurchaseOrderResponse(
        order.getId(),
        order.getPurchaseRequestId(),
        order.getPoNumber(),
        order.getVendorId(),
        order.getStatus(),
        order.getCurrency(),
        MoneyDto.of(order.getTotalAmount(), order.getCurrency()),
        order.getPaymentTerms(),
        order.getDeliveryDate(),
        order.getIssuedAt(),
        order.getCreatedAt(),
        order.getUpdatedAt(),
        order.getVersion(),
        order.getLines().stream().map(line -> toLine(line, order.getCurrency())).toList());
  }

  public PurchaseOrderResponse toSummary(PurchaseOrder order) {
    return new PurchaseOrderResponse(
        order.getId(),
        order.getPurchaseRequestId(),
        order.getPoNumber(),
        order.getVendorId(),
        order.getStatus(),
        order.getCurrency(),
        MoneyDto.of(order.getTotalAmount(), order.getCurrency()),
        order.getPaymentTerms(),
        order.getDeliveryDate(),
        order.getIssuedAt(),
        order.getCreatedAt(),
        order.getUpdatedAt(),
        order.getVersion(),
        null);
  }

  private static PurchaseOrderLineResponse toLine(PurchaseOrderLine line, String currency) {
    return new PurchaseOrderLineResponse(
        line.getId(),
        line.getSequence(),
        line.getDescription(),
        line.getQuantity().toPlainString(),
        line.getUnit(),
        MoneyDto.of(line.getUnitPrice(), currency),
        MoneyDto.of(line.getLineTotal(), currency),
        line.getReceivedQuantity().toPlainString(),
        line.getRemainingQuantity().toPlainString());
  }
}
