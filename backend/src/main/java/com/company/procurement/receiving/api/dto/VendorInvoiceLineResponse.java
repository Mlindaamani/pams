package com.company.procurement.receiving.api.dto;

import com.company.procurement.shared.dto.MoneyDto;
import java.math.BigDecimal;
import java.util.UUID;

public record VendorInvoiceLineResponse(
    UUID purchaseOrderLineId,
    String description,
    String unit,
    String quantity,
    MoneyDto unitPrice,
    MoneyDto lineTotal,
    boolean matched,
    BigDecimal priceVariancePercent,
    String matchMessage) {}
