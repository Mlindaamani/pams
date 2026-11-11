package com.company.procurement.platform.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreateTenantRequest(
    @NotBlank @Size(max = 200) String name,
    @NotBlank @Size(min = 2, max = 100) @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$") String slug,
    @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal invoiceTolerancePercent,
    @Email @Size(max = 320) String adminEmail,
    @Size(max = 200) String adminName,
    @Size(max = 30) String planCode) {}
