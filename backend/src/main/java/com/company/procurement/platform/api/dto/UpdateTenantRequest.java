package com.company.procurement.platform.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record UpdateTenantRequest(
    @NotBlank @Size(max = 200) String name,
    @NotBlank @Size(min = 2, max = 100) @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$") String slug,
    @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal invoiceTolerancePercent) {}
