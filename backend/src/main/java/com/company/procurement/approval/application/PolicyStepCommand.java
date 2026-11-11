package com.company.procurement.approval.application;

import com.company.procurement.approval.domain.ApproverType;
import java.util.UUID;

public record PolicyStepCommand(
    String name,
    ApproverType approverType,
    String approverRole,
    UUID approverUserId,
    int slaHours,
    Integer stage) {}
