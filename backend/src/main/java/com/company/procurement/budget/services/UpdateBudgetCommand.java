package com.company.procurement.budget.services;

import java.math.BigDecimal;

public record UpdateBudgetCommand(BigDecimal allocatedAmount) {}
