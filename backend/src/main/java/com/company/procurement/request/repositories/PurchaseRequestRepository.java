package com.company.procurement.request.repositories;

import com.company.procurement.request.domain.PurchaseRequest;
import com.company.procurement.request.domain.PurchaseRequestFilter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PurchaseRequestRepository {

  PurchaseRequest save(PurchaseRequest request);

  Optional<PurchaseRequest> findByIdAndOrganizationId(UUID id, UUID organizationId);

  Optional<PurchaseRequest> findDetailByIdAndOrganizationId(UUID id, UUID organizationId);

  long countNonDraft(UUID organizationId);

  long countTotal(UUID organizationId);

  long countCreatedBetween(UUID organizationId, java.time.Instant from, java.time.Instant to);

  List<UUID> popularCatalogItemIds(UUID organizationId, UUID departmentId, int limit);

  List<NonCatalogReviewRow> findPendingNonCatalog(UUID organizationId);

  boolean resolveNonCatalogLine(
      UUID organizationId,
      UUID lineId,
      com.company.procurement.request.domain.CatalogReviewStatus status,
      String actor);

  record NonCatalogReviewRow(
      UUID id,
      UUID requestId,
      String requestNumber,
      String requesterName,
      String departmentName,
      String description,
      String unit,
      java.math.BigDecimal unitPrice,
      String notes,
      java.time.Instant createdAt) {}

  Page<PurchaseRequest> search(
      UUID organizationId, PurchaseRequestFilter filter, Pageable pageable);
}
