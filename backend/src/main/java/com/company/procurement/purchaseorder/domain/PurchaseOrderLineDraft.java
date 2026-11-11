package com.company.procurement.purchaseorder.domain;

import java.math.BigDecimal;

public record PurchaseOrderLineDraft(
    String description, BigDecimal quantity, String unit, BigDecimal unitPrice) {}
