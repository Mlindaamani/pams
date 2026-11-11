package com.company.procurement.organization.api.dto;

import com.company.procurement.shared.api.MoneyRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record UpdateUserProfileRequest(
    @NotBlank(message = "displayName is required")
        @Size(max = 200, message = "displayName must be at most 200 characters")
        String displayName,
    @NotNull(message = "departmentId is required") UUID departmentId,
    UUID managerId,
    @Valid MoneyRequest approvalLimit,
    @NotNull(message = "active is required") Boolean active) {}
