package com.company.procurement.receiving.api.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record GoodsReceiptResponse(
    UUID id,
    UUID purchaseOrderId,
    LocalDate receivedDate,
    String deliveryNoteReference,
    String notes,
    String recordedByName,
    String createdBy,
    Instant createdAt,
    List<GoodsReceiptLineResponse> lines) {}
