package com.company.procurement.approval.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public record UpdateApprovalPolicyRequest(
    @NotBlank(message = "name is required")
        @Size(max = 200, message = "name must be at most 200 characters")
        String name,
    @Size(max = 30) String entityType,
    @NotNull(message = "amountMin is required")
        @DecimalMin(value = "0", message = "amountMin must not be negative")
        BigDecimal amountMin,
    @DecimalMin(value = "0", message = "amountMax must not be negative") BigDecimal amountMax,
    @NotEmpty(message = "at least one step is required") @Valid List<PolicyStepRequest> steps) {}
