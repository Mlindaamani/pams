package com.company.procurement.approval.services;

import java.math.BigDecimal;
import java.util.List;

public record CreatePolicyCommand(
    String name,
    String entityType,
    BigDecimal amountMin,
    BigDecimal amountMax,
    List<PolicyStepCommand> steps) {}
