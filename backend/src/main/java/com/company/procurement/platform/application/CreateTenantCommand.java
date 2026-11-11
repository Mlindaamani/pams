package com.company.procurement.platform.application;

import java.math.BigDecimal;

public record CreateTenantCommand(
    String name,
    String slug,
    BigDecimal invoiceTolerancePercent,
    String adminEmail,
    String adminName,
    String planCode) {}
