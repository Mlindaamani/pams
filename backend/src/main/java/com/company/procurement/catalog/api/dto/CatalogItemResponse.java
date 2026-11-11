package com.company.procurement.catalog.api.dto;

import com.company.procurement.shared.dto.MoneyDto;
import java.time.Instant;
import java.util.UUID;

public record CatalogItemResponse(
    UUID id,
    String sku,
    String name,
    String unit,
    MoneyDto defaultPrice,
    String category,
    UUID defaultVendorId,
    boolean active,
    Instant createdAt,
    Instant updatedAt,
    long version) {}
