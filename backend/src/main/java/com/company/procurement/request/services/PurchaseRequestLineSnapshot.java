package com.company.procurement.request.services;

import java.math.BigDecimal;

public record PurchaseRequestLineSnapshot(
    String description,
    BigDecimal quantity,
    String unit,
    BigDecimal unitPrice,
    BigDecimal lineTotal,
    java.util.UUID vendorId) {}
