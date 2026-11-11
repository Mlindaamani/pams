package com.company.procurement.approval.api.dto;

import com.company.procurement.approval.domain.ApprovalStepStatus;
import com.company.procurement.approval.domain.ApproverType;
import com.company.procurement.shared.dto.MoneyDto;
import java.time.Instant;
import java.util.UUID;

public record ApprovalStepResponse(
    UUID id,
    UUID requestId,
    int sequence,
    String name,
    ApproverType approverType,
    String assigneeRole,
    UUID assigneeUserId,
    ApprovalStepStatus status,
    String comment,
    String actedBySubject,
    String actedByName,
    Instant actedAt,
    Instant slaDueAt,
    Instant reminderSentAt,
    Instant escalatedAt,
    MoneyDto total) {}
