package com.company.procurement.request.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PurchaseRequestFilter(
    RequestStatus status,
    UUID departmentId,
    String requesterSubject,
    Instant createdFrom,
    Instant createdTo,
    BigDecimal amountMin,
    BigDecimal amountMax,
    String titleContains) {}
