package com.company.procurement.approval.domain;

import java.util.UUID;

public record ApprovalStepDraft(
    String name,
    ApproverType approverType,
    String approverRole,
    UUID assigneeUserId,
    int slaHours,
    int stage) {}
