package com.company.procurement.catalog.api.dto;

import com.company.procurement.shared.api.MoneyRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateCatalogItemRequest(
    @NotBlank(message = "name is required")
        @Size(max = 200, message = "name must be at most 200 characters")
        String name,
    @NotBlank(message = "unit is required")
        @Size(max = 20, message = "unit must be at most 20 characters")
        String unit,
    @NotNull(message = "defaultPrice is required") @Valid MoneyRequest defaultPrice,
    @Size(max = 100, message = "category must be at most 100 characters") String category,
    java.util.UUID defaultVendorId,
    @NotNull(message = "active is required") Boolean active) {}
