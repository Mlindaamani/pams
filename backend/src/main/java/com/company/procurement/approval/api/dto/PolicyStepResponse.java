package com.company.procurement.approval.api.dto;

import com.company.procurement.approval.domain.ApprovalConditionType;
import com.company.procurement.approval.domain.ApproverType;
import java.util.UUID;

public record PolicyStepResponse(
    UUID id,
    int sequence,
    String name,
    ApproverType approverType,
    String approverRole,
    UUID approverUserId,
    int slaHours,
    int minApprovals,
    ApprovalConditionType conditionType,
    UUID conditionDepartmentId) {}
