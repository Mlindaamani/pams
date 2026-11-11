package com.company.procurement.organization.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateOrganizationRequest(
    @NotBlank(message = "name is required")
        @Size(max = 200, message = "name must be at most 200 characters")
        String name,
    @NotBlank(message = "slug is required")
        @Size(min = 2, max = 100, message = "slug must be between 2 and 100 characters")
        @Pattern(
            regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$",
            message = "slug must be lowercase alphanumeric words separated by hyphens")
        String slug) {}
