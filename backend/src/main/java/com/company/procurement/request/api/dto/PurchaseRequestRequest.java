package com.company.procurement.request.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PurchaseRequestRequest(
    @NotBlank(message = "title is required")
        @Size(max = 300, message = "title must be at most 300 characters")
        String title,
    @NotBlank(message = "justification is required")
        @Size(max = 2000, message = "justification must be at most 2000 characters")
        String justification,
    @NotNull(message = "departmentId is required") UUID departmentId,
    LocalDate neededBy,
    @NotBlank(message = "currency is required")
        @Pattern(regexp = "^[A-Z]{3}$", message = "currency must be a 3-letter uppercase code")
        String currency,
    Boolean budgetExceptionRequested,
    @NotEmpty(message = "at least one line item is required") @Valid
        List<PurchaseRequestLineRequest> lines) {}
