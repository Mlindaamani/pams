package com.company.procurement.budget.application;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateBudgetCommand(
    UUID departmentId,
    int fiscalYear,
    String category,
    String currency,
    BigDecimal allocatedAmount) {}
