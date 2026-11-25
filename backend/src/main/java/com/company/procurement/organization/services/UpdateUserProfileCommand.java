package com.company.procurement.organization.services;

import java.math.BigDecimal;
import java.util.UUID;

public record UpdateUserProfileCommand(
    String displayName,
    UUID departmentId,
    UUID managerId,
    BigDecimal approvalLimitAmount,
    String approvalLimitCurrency,
    boolean active) {}
