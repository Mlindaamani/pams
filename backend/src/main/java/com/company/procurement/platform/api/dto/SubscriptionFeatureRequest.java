package com.company.procurement.platform.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SubscriptionFeatureRequest(
    @Size(max = 50) String code,
    @NotBlank @Size(max = 100) String name,
    @NotBlank @Size(max = 500) String description,
    @NotBlank @Pattern(regexp = "(?i)CORE|PROCUREMENT|FINANCE|INTEGRATIONS|SECURITY|SUPPORT")
        String category,
    boolean active,
    @Min(0) int sortOrder) {}
