package com.company.procurement.approval.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApprovalStepRepository {

  Optional<ApprovalStep> findByIdAndOrganizationId(UUID id, UUID organizationId);

  List<ApprovalStep> findDueSteps(UUID organizationId, Instant now);

  List<ApprovalStep> findCurrentPendingSteps(UUID organizationId);
}
