package com.company.procurement.organization.application;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateUserProfileCommand(
    String keycloakSubject,
    String email,
    String displayName,
    UUID departmentId,
    UUID managerId,
    BigDecimal approvalLimitAmount,
    String approvalLimitCurrency) {}
