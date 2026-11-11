package com.company.procurement.organization.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record SelfProfileRequest(
    @NotBlank @Size(max = 200) String displayName, @NotNull UUID departmentId, UUID managerId) {}
