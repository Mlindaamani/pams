package com.company.procurement.approval.application;

import java.math.BigDecimal;
import java.util.List;

public record UpdatePolicyCommand(
    String name,
    String entityType,
    BigDecimal amountMin,
    BigDecimal amountMax,
    List<PolicyStepCommand> steps) {}
