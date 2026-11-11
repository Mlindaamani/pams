package com.company.procurement.platform.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DemoRequest(
    @NotBlank @Size(max = 200) String name,
    @NotBlank @Email @Size(max = 320) String email,
    @NotBlank @Size(max = 200) String company,
    @Size(max = 50) String phone,
    @Size(max = 2000) String message) {}
