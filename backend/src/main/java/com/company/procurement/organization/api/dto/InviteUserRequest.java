package com.company.procurement.organization.api.dto;

import com.company.procurement.shared.api.MoneyRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;

public record InviteUserRequest(
    @NotBlank @Email @Size(max = 320) String email,
    @NotBlank @Size(max = 200) String displayName,
    @NotNull UUID departmentId,
    UUID managerId,
    @NotEmpty Set<String> roles,
    @Valid MoneyRequest approvalLimit) {}
