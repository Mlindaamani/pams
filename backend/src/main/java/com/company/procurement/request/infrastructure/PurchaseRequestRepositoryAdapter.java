package com.company.procurement.request.infrastructure;

import com.company.procurement.request.domain.PurchaseRequest;
import com.company.procurement.request.domain.PurchaseRequestFilter;
import com.company.procurement.request.domain.PurchaseRequestRepository;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

@Repository
public class PurchaseRequestRepositoryAdapter implements PurchaseRequestRepository {

  private final JpaPurchaseRequestRepository jpa;

  public PurchaseRequestRepositoryAdapter(JpaPurchaseRequestRepository jpa) {
    this.jpa = jpa;
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
        predicates.add(
            cb.like(
                cb.lower(root.get("title")),
                "%" + filter.titleContains().trim().toLowerCase() + "%"));
      }
      return cb.and(predicates.toArray(Predicate[]::new));
    };
  }
}
