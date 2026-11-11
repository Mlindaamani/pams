package com.company.procurement.approval.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DelegationRepository {

  Delegation save(Delegation delegation);

  Optional<Delegation> findByIdAndOrganizationId(UUID id, UUID organizationId);

  boolean existsActiveDelegation(
      UUID organizationId, UUID delegatorUserId, UUID delegateUserId, Instant moment);

  List<Delegation> findByOrganizationIdAndDelegatorUserId(
      UUID organizationId, UUID delegatorUserId);
}
