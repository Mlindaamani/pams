package com.company.procurement.organization.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record UpdateOrganizationRequest(
    @NotBlank(message = "name is required")
        @Size(max = 200, message = "name must be at most 200 characters")
        String name,
    @DecimalMin(value = "0.00", message = "invoiceTolerancePercent must be at least 0")
        @DecimalMax(value = "100.00", message = "invoiceTolerancePercent must be at most 100")
        BigDecimal invoiceTolerancePercent) {}
