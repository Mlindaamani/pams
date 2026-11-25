package com.company.procurement.platform.services;

import java.math.BigDecimal;

public record UpdateTenantCommand(String name, String slug, BigDecimal invoiceTolerancePercent) {}
