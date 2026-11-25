package com.company.procurement.organization.services;

import java.math.BigDecimal;

public record UpdateOrganizationCommand(String name, BigDecimal invoiceTolerancePercent) {}
