package com.company.procurement.catalog.application;

import java.math.BigDecimal;

public record CreateCatalogItemCommand(
    String sku,
    String name,
    String unit,
    BigDecimal defaultPrice,
    String currency,
    String category,
    java.util.UUID defaultVendorId,
    com.company.procurement.catalog.domain.CatalogDepartmentScope departmentScope,
    java.util.List<java.util.UUID> departmentIds) {}
