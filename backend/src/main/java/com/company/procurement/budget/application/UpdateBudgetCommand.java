package com.company.procurement.budget.application;

import java.math.BigDecimal;

public record UpdateBudgetCommand(BigDecimal allocatedAmount) {}
