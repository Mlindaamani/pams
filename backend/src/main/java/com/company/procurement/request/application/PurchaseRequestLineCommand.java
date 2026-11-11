package com.company.procurement.request.application;

import java.math.BigDecimal;
import java.util.UUID;

public record PurchaseRequestLineCommand(
    String description,
    BigDecimal quantity,
    String unit,
    BigDecimal unitPrice,
    UUID catalogItemId,
    UUID vendorId,
    String notes) {}
