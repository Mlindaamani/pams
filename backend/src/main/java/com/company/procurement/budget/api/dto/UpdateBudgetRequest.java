package com.company.procurement.budget.api.dto;

import com.company.procurement.shared.api.MoneyRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record UpdateBudgetRequest(
    @NotNull(message = "allocated is required") @Valid MoneyRequest allocated) {}
