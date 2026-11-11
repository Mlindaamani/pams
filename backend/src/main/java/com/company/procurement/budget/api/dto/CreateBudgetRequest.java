package com.company.procurement.budget.api.dto;

import com.company.procurement.shared.api.MoneyRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateBudgetRequest(
    @NotNull(message = "departmentId is required") UUID departmentId,
    @Min(value = 2000, message = "fiscalYear must be 2000 or later")
        @Max(value = 2200, message = "fiscalYear must be 2200 or earlier")
        int fiscalYear,
    @Size(max = 100, message = "category must be at most 100 characters") String category,
    @NotNull(message = "allocated is required") @Valid MoneyRequest allocated) {}
