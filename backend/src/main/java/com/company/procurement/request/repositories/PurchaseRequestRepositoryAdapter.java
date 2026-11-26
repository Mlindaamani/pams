package com.company.procurement.request.repositories;

import com.company.procurement.request.domain.CatalogReviewStatus;
import com.company.procurement.request.domain.PurchaseRequest;
import com.company.procurement.request.domain.PurchaseRequestFilter;
import com.company.procurement.request.domain.RequestStatus;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

@Repository
public class PurchaseRequestRepositoryAdapter implements PurchaseRequestRepository {

  private final JpaPurchaseRequestRepository jpa;
  private final Clock clock;

  public PurchaseRequestRepositoryAdapter(JpaPurchaseRequestRepository jpa, Clock clock) {
    this.jpa = jpa;
    this.clock = clock;
  }

  @Override
  public PurchaseRequest save(PurchaseRequest request) {
    return jpa.save(request);
  }

  @Override
  public Optional<PurchaseRequest> findByIdAndOrganizationId(UUID id, UUID organizationId) {
    return jpa.findByIdAndOrganizationId(id, organizationId);
  }

  @Override
  public Optional<PurchaseRequest> findDetailByIdAndOrganizationId(UUID id, UUID organizationId) {
    return jpa.findDetail(id, organizationId);
  }

  @Override
  public long countNonDraft(UUID organizationId) {
    return jpa.countByOrganizationIdAndStatusNot(organizationId, RequestStatus.DRAFT);
  }

  @Override
  public long countTotal(UUID organizationId) {
    return jpa.countByOrganizationId(organizationId);
  }

  @Override
  public long countCreatedBetween(
      UUID organizationId, java.time.Instant from, java.time.Instant to) {
    return jpa.countByOrganizationIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
        organizationId, from, to);
  }

  @Override
  public List<UUID> popularCatalogItemIds(UUID organizationId, UUID departmentId, int limit) {
    return jpa.popularCatalogItemIds(
        organizationId, departmentId, PageRequest.of(0, Math.max(1, limit)));
  }

  @Override
  public List<NonCatalogReviewRow> findPendingNonCatalog(UUID organizationId) {
    return jpa.findPendingNonCatalog(organizationId).stream()
        .map(
            row ->
                new NonCatalogReviewRow(
                    row.getId(),
                    row.getRequestId(),
                    row.getRequestNumber(),
                    row.getRequesterName(),
                    row.getDepartmentName(),
                    row.getDescription(),
                    row.getUnit(),
                    row.getUnitPrice(),
                    row.getNotes(),
                    row.getCreatedAt()))
        .toList();
  }

  @Override
  public boolean resolveNonCatalogLine(
      UUID organizationId, UUID lineId, CatalogReviewStatus status, String actor) {
    return jpa.resolveNonCatalogLine(organizationId, lineId, status, actor, clock.instant()) > 0;
  }

  @Override
  public Page<PurchaseRequest> search(
      UUID organizationId, PurchaseRequestFilter filter, Pageable pageable) {
    return jpa.findAll(specification(organizationId, filter), pageable);
  }

  private static Specification<PurchaseRequest> specification(
      UUID organizationId, PurchaseRequestFilter filter) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      predicates.add(cb.equal(root.get("organizationId"), organizationId));
      if (filter.status() != null) {
        predicates.add(cb.equal(root.get("status"), filter.status()));
      }
      if (filter.departmentId() != null) {
        predicates.add(cb.equal(root.get("departmentId"), filter.departmentId()));
      }
      if (filter.requesterSubject() != null) {
        predicates.add(cb.equal(root.get("requesterSubject"), filter.requesterSubject()));
      }
      if (filter.createdFrom() != null) {
        predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filter.createdFrom()));
      }
      if (filter.createdTo() != null) {
        predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), filter.createdTo()));
      }
      if (filter.amountMin() != null) {
        predicates.add(cb.greaterThanOrEqualTo(root.get("totalAmount"), filter.amountMin()));
      }
      if (filter.amountMax() != null) {
        predicates.add(cb.lessThanOrEqualTo(root.get("totalAmount"), filter.amountMax()));
      }
      if (filter.titleContains() != null && !filter.titleContains().isBlank()) {
        String pattern = "%" + filter.titleContains().trim().toLowerCase() + "%";
        predicates.add(
            cb.or(
                cb.like(cb.lower(root.get("title")), pattern),
                cb.like(cb.lower(root.get("requestNumber")), pattern)));
      }
      return cb.and(predicates.toArray(Predicate[]::new));
    };
  }
}
