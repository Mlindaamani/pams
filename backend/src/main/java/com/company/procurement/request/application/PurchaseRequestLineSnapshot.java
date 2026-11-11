package com.company.procurement.request.application;

import java.math.BigDecimal;

public record PurchaseRequestLineSnapshot(
    String description,
    BigDecimal quantity,
    String unit,
    BigDecimal unitPrice,
    BigDecimal lineTotal,
    java.util.UUID vendorId) {}
