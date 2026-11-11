package com.company.procurement.purchaseorder.api.dto;

import com.company.procurement.purchaseorder.domain.PurchaseOrderStatus;
import com.company.procurement.shared.dto.MoneyDto;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PurchaseOrderResponse(
    UUID id,
    UUID requestId,
    String poNumber,
    UUID vendorId,
    PurchaseOrderStatus status,
    String currency,
    MoneyDto total,
    String paymentTerms,
    LocalDate deliveryDate,
    Instant issuedAt,
    Instant createdAt,
    Instant updatedAt,
    long version,
    List<PurchaseOrderLineResponse> lines) {}
