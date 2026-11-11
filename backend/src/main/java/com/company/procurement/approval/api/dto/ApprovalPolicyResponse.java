package com.company.procurement.approval.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ApprovalPolicyResponse(
    UUID id,
    String name,
    String entityType,
    BigDecimal amountMin,
    BigDecimal amountMax,
    int policyVersion,
    boolean active,
    List<PolicyStepResponse> steps,
    Instant createdAt,
    Instant updatedAt,
    long version) {}
