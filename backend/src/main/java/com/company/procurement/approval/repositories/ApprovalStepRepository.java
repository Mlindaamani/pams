package com.company.procurement.approval.repositories;

import com.company.procurement.approval.domain.ApprovalStep;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApprovalStepRepository {

  Optional<ApprovalStep> findByIdAndOrganizationId(UUID id, UUID organizationId);

  List<ApprovalStep> findDueSteps(UUID organizationId, Instant now);

  List<ApprovalStep> findCurrentPendingSteps(UUID organizationId);

  long countPending(UUID organizationId);

  long countCurrentPending(UUID organizationId);
}
