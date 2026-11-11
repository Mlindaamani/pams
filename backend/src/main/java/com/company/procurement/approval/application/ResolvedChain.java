package com.company.procurement.approval.application;

import com.company.procurement.approval.domain.ApprovalStepDraft;
import java.util.List;
import java.util.UUID;

public record ResolvedChain(
    UUID policyId, int policyVersion, String policyName, List<ApprovalStepDraft> steps) {}
