package com.company.procurement.request.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PurchaseRequestRepository {

  PurchaseRequest save(PurchaseRequest request);

  Optional<PurchaseRequest> findByIdAndOrganizationId(UUID id, UUID organizationId);

  Optional<PurchaseRequest> findDetailByIdAndOrganizationId(UUID id, UUID organizationId);

  Page<PurchaseRequest> search(
      UUID organizationId, PurchaseRequestFilter filter, Pageable pageable);
}
