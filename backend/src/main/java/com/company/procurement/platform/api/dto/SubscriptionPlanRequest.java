package com.company.procurement.platform.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public record SubscriptionPlanRequest(
    @Size(max = 30) String code,
    @NotBlank @Size(max = 100) String name,
    @NotBlank @Size(max = 500) String description,
    @NotNull @DecimalMin("0.00") BigDecimal monthlyPrice,
    @NotNull @DecimalMin("0.00") BigDecimal annualPrice,
    @NotBlank @Pattern(regexp = "(?i)[A-Z]{3}") String currency,
    @Min(0) @Max(365) int trialDays,
    Integer maxUsers,
    Integer maxDepartments,
    Integer maxRequestsMonth,
    Integer maxStorageMb,
    List<String> featureCodes,
    boolean active,
    @Min(0) int sortOrder,
    String stripePriceMonthly,
    String stripePriceAnnual) {}
