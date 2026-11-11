package com.company.procurement.request.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PurchaseRequestSnapshot(
    UUID requestId,
    String requesterSubject,
    String requesterName,
    UUID departmentId,
    String currency,
    BigDecimal totalAmount,
    UUID budgetId,
    BigDecimal reservedAmount,
    List<PurchaseRequestLineSnapshot> lines) {}
