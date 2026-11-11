package com.company.procurement.receiving.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record VendorInvoiceLineDraft(
    UUID purchaseOrderLineId,
    String description,
    String unit,
    BigDecimal quantity,
    BigDecimal unitPrice,
    BigDecimal lineTotal,
    boolean matched,
    BigDecimal priceVariancePercent,
    String matchMessage) {}
