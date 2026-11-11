package com.company.procurement.budget.api.dto;

import com.company.procurement.shared.dto.MoneyDto;
import java.time.Instant;
import java.util.UUID;

public record BudgetResponse(
    UUID id,
    String budgetNumber,
    UUID departmentId,
    int fiscalYear,
    String category,
    MoneyDto allocated,
    MoneyDto reserved,
    MoneyDto committed,
    MoneyDto actual,
    MoneyDto remaining,
    Instant createdAt,
    Instant updatedAt,
    long version) {}
