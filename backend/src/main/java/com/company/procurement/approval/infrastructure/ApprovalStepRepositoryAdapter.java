package com.company.procurement.approval.infrastructure;

import com.company.procurement.approval.domain.ApprovalStep;
import com.company.procurement.approval.domain.ApprovalStepRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class ApprovalStepRepositoryAdapter implements ApprovalStepRepository {

  private final JpaApprovalStepRepository jpa;

  public ApprovalStepRepositoryAdapter(JpaApprovalStepRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public Optional<ApprovalStep> findByIdAndOrganizationId(UUID id, UUID organizationId) {
    return jpa.findByIdAndOrganizationId(id, organizationId);
  }

  @Override
  public List<ApprovalStep> findDueSteps(UUID organizationId, Instant now) {
    return jpa.findDueSteps(organizationId, now);
  }

  @Override
  public List<ApprovalStep> findCurrentPendingSteps(UUID organizationId) {
    return jpa.findCurrentPendingSteps(organizationId);
  }
}
