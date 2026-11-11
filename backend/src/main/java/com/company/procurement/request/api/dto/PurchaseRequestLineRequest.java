package com.company.procurement.request.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record PurchaseRequestLineRequest(
    @NotBlank(message = "description is required")
        @Size(max = 500, message = "description must be at most 500 characters")
        String description,
    @NotNull(message = "quantity is required")
        @DecimalMin(value = "0.0001", message = "quantity must be positive")
        BigDecimal quantity,
    @NotBlank(message = "unit is required")
        @Size(max = 20, message = "unit must be at most 20 characters")
        String unit,
    @NotNull(message = "unitPrice is required")
        @DecimalMin(value = "0.0000", message = "unitPrice must not be negative")
        BigDecimal unitPrice,
    UUID catalogItemId) {}
