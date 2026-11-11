package com.company.procurement.platform.application;

import java.math.BigDecimal;

public record UpdateTenantCommand(String name, String slug, BigDecimal invoiceTolerancePercent) {}
