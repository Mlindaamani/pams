package com.company.procurement.request.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record PurchaseRequestLineDraft(
    String description,
    BigDecimal quantity,
    String unit,
    BigDecimal unitPrice,
    UUID catalogItemId) {}
