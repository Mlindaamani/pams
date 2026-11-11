package com.company.procurement.organization.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateBrandingRequest(
    @NotBlank @Size(max = 200) String displayName,
    @NotBlank @Pattern(regexp = "(?i)#[0-9a-f]{6}") String primaryColor,
    @NotBlank @Pattern(regexp = "(?i)#[0-9a-f]{6}") String secondaryColor,
    @Size(max = 200) String emailFromName,
    @Email @Size(max = 320) String supportEmail,
    Integer cornerRadius,
    String fontFamily,
    String uiDensity,
    @Size(max = 40) String requestLabel,
    @Size(max = 40) String orderLabel,
    @Size(max = 40) String vendorLabel,
    @Size(max = 40) String budgetLabel,
    @Size(max = 40) String catalogLabel) {}
