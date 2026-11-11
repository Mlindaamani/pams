package com.company.procurement.request.api.dto;

import com.company.procurement.shared.dto.MoneyDto;
import java.util.UUID;

public record PurchaseRequestLineResponse(
    UUID id,
    String description,
    String quantity,
    String unit,
    MoneyDto unitPrice,
    MoneyDto lineTotal,
    UUID catalogItemId,
    UUID vendorId,
    boolean nonCatalog,
    String notes,
    String catalogReviewStatus) {}
