package com.company.procurement.receiving.api.dto;

import java.util.UUID;

public record GoodsReceiptLineResponse(
    UUID purchaseOrderLineId, String description, String unit, String quantity) {}
