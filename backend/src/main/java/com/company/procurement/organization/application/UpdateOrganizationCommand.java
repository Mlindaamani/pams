package com.company.procurement.organization.application;

import java.math.BigDecimal;

public record UpdateOrganizationCommand(String name, BigDecimal invoiceTolerancePercent) {}
