package com.company.procurement.purchaseorder.api.dto;

import com.company.procurement.shared.dto.MoneyDto;
import java.util.UUID;

public record PurchaseOrderLineResponse(
    UUID id,
    int sequence,
    String description,
    String quantity,
    String unit,
    MoneyDto unitPrice,
    MoneyDto lineTotal,
    String receivedQuantity,
    String remainingQuantity) {}
