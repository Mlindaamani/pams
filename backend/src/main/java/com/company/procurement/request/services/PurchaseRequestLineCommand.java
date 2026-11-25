package com.company.procurement.request.services;

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
