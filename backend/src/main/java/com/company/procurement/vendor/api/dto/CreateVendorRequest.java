package com.company.procurement.vendor.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateVendorRequest(
    @NotBlank(message = "name is required")
        @Size(max = 200, message = "name must be at most 200 characters")
        String name,
    @NotBlank(message = "taxId is required")
        @Size(max = 50, message = "taxId must be at most 50 characters")
        String taxId,
    @Size(max = 200, message = "contactName must be at most 200 characters") String contactName,
    @Email(message = "contactEmail must be valid")
        @Size(max = 320, message = "contactEmail must be at most 320 characters")
        String contactEmail,
    @Size(max = 50, message = "contactPhone must be at most 50 characters") String contactPhone,
    @Size(max = 500, message = "addressLine must be at most 500 characters") String addressLine,
    @Size(max = 200, message = "bankName must be at most 200 characters") String bankName,
    @Size(max = 200, message = "bankAccountName must be at most 200 characters")
        String bankAccountName,
    @Size(max = 100, message = "bankAccountNumber must be at most 100 characters")
        String bankAccountNumber) {}
