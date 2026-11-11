package com.company.procurement.organization.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DepartmentRequest(
    @NotBlank(message = "name is required")
        @Size(max = 200, message = "name must be at most 200 characters")
        String name) {}
