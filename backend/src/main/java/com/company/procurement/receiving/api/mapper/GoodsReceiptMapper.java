package com.company.procurement.receiving.api.mapper;

import com.company.procurement.receiving.api.dto.GoodsReceiptLineResponse;
import com.company.procurement.receiving.api.dto.GoodsReceiptResponse;
import com.company.procurement.receiving.domain.GoodsReceipt;
import org.springframework.stereotype.Component;

@Component
public class GoodsReceiptMapper {

  public GoodsReceiptResponse toResponse(GoodsReceipt receipt) {
    return new GoodsReceiptResponse(
        receipt.getId(),
        receipt.getReceiptNumber(),
        receipt.getPurchaseOrderId(),
        receipt.getReceivedDate(),
        receipt.getDeliveryNoteReference(),
        receipt.getNotes(),
        receipt.getRecordedByName(),
        receipt.getCreatedBy(),
        receipt.getCreatedAt(),
        receipt.getLines().stream()
            .map(
                line ->
                    new GoodsReceiptLineResponse(
                        line.getPurchaseOrderLineId(),
                        line.getDescription(),
                        line.getUnit(),
                        line.getQuantity().toPlainString()))
            .toList());
  }
}
