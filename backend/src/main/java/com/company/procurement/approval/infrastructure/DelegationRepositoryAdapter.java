package com.company.procurement.approval.infrastructure;

import com.company.procurement.approval.domain.Delegation;
import com.company.procurement.approval.domain.DelegationRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class DelegationRepositoryAdapter implements DelegationRepository {

  private final JpaDelegationRepository jpa;

  public DelegationRepositoryAdapter(JpaDelegationRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public Delegation save(Delegation delegation) {
    return jpa.save(delegation);
  }

  @Override
  public Optional<Delegation> findByIdAndOrganizationId(UUID id, UUID organizationId) {
    return jpa.findByIdAndOrganizationId(id, organizationId);
  }

  @Override
  public boolean existsActiveDelegation(
      UUID organizationId, UUID delegatorUserId, UUID delegateUserId, Instant moment) {
    return jpa.existsActiveDelegation(organizationId, delegatorUserId, delegateUserId, moment);
  }

  @Override
  public List<Delegation> findByOrganizationIdAndDelegatorUserId(
      UUID organizationId, UUID delegatorUserId) {
    return jpa.findByOrganizationIdAndDelegatorUserIdOrderByStartsAtDesc(
        organizationId, delegatorUserId);
  }
}
