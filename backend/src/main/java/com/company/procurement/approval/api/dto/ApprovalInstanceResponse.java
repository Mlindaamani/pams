package com.company.procurement.approval.api.dto;

import com.company.procurement.approval.domain.ApprovalInstanceStatus;
import com.company.procurement.shared.dto.MoneyDto;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ApprovalInstanceResponse(
    UUID id,
    UUID requestId,
    UUID policyId,
    int policyVersion,
    MoneyDto total,
    ApprovalInstanceStatus status,
    Integer currentSequence,
    String lastComment,
    List<ApprovalStepResponse> steps,
    Instant createdAt,
    Instant updatedAt,
    long version) {}
