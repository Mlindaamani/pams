package com.company.procurement.purchaseorder.domain;

import java.time.Instant;
import java.util.UUID;

public record PurchaseOrderFilter(
    PurchaseOrderStatus status,
    UUID vendorId,
    UUID purchaseRequestId,
    Instant createdFrom,
    Instant createdTo,
    String numberContains) {}
