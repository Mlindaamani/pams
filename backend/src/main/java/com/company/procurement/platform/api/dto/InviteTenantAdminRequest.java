package com.company.procurement.platform.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InviteTenantAdminRequest(
    @NotBlank @Email @Size(max = 320) String email, @NotBlank @Size(max = 200) String name) {}
