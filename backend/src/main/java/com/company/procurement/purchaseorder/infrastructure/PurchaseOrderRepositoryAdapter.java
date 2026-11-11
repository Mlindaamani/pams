package com.company.procurement.purchaseorder.infrastructure;

import com.company.procurement.purchaseorder.domain.PurchaseOrder;
import com.company.procurement.purchaseorder.domain.PurchaseOrderFilter;
import com.company.procurement.purchaseorder.domain.PurchaseOrderRepository;
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
public class PurchaseOrderRepositoryAdapter implements PurchaseOrderRepository {

  private final JpaPurchaseOrderRepository jpa;

  public PurchaseOrderRepositoryAdapter(JpaPurchaseOrderRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public PurchaseOrder save(PurchaseOrder order) {
    return jpa.save(order);
  }

  @Override
  public void flush() {
    jpa.flush();
  }

  @Override
  public Optional<PurchaseOrder> findByIdAndOrganizationId(UUID id, UUID organizationId) {
    return jpa.findByIdAndOrganizationId(id, organizationId);
  }

  @Override
  public Optional<PurchaseOrder> findDetailByIdAndOrganizationId(UUID id, UUID organizationId) {
    return jpa.findDetail(id, organizationId);
  }

  @Override
  public Optional<PurchaseOrder> findForUpdate(UUID id, UUID organizationId) {
    return jpa.findForUpdate(id, organizationId);
  }

  @Override
  public Optional<PurchaseOrder> findActiveByOrganizationIdAndPurchaseRequestId(
      UUID organizationId, UUID purchaseRequestId) {
    return jpa.findByOrganizationIdAndPurchaseRequestIdAndStatusNot(
        organizationId,
        purchaseRequestId,
        com.company.procurement.purchaseorder.domain.PurchaseOrderStatus.CANCELLED);
  }

  @Override
  public Page<PurchaseOrder> search(
      UUID organizationId, PurchaseOrderFilter filter, Pageable pageable) {
    return jpa.findAll(specification(organizationId, filter), pageable);
  }

  private static Specification<PurchaseOrder> specification(
      UUID organizationId, PurchaseOrderFilter filter) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      predicates.add(cb.equal(root.get("organizationId"), organizationId));
      if (filter.status() != null) {
        predicates.add(cb.equal(root.get("status"), filter.status()));
      }
      if (filter.vendorId() != null) {
        predicates.add(cb.equal(root.get("vendorId"), filter.vendorId()));
      }
      if (filter.purchaseRequestId() != null) {
        predicates.add(cb.equal(root.get("purchaseRequestId"), filter.purchaseRequestId()));
      }
      if (filter.createdFrom() != null) {
        predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filter.createdFrom()));
      }
      if (filter.createdTo() != null) {
        predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), filter.createdTo()));
      }
      if (filter.numberContains() != null && !filter.numberContains().isBlank()) {
        predicates.add(
            cb.like(
                cb.lower(root.get("poNumber")),
                "%" + filter.numberContains().trim().toLowerCase() + "%"));
      }
      return cb.and(predicates.toArray(Predicate[]::new));
    };
  }
}
