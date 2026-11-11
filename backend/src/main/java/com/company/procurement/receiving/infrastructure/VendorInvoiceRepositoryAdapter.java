package com.company.procurement.receiving.infrastructure;

import com.company.procurement.receiving.domain.VendorInvoice;
import com.company.procurement.receiving.domain.VendorInvoiceFilter;
import com.company.procurement.receiving.domain.VendorInvoiceRepository;
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
public class VendorInvoiceRepositoryAdapter implements VendorInvoiceRepository {

  private final JpaVendorInvoiceRepository jpa;

  public VendorInvoiceRepositoryAdapter(JpaVendorInvoiceRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public VendorInvoice save(VendorInvoice invoice) {
    return jpa.save(invoice);
  }

  @Override
  public Optional<VendorInvoice> findDetailByIdAndOrganizationId(UUID id, UUID organizationId) {
    return jpa.findDetail(id, organizationId);
  }

  @Override
  public boolean existsActiveByVendorAndNumber(
      UUID organizationId, UUID vendorId, String invoiceNumber) {
    return jpa.existsActiveByVendorAndNumber(organizationId, vendorId, invoiceNumber.trim());
  }

  @Override
  public Page<VendorInvoice> search(
      UUID organizationId, VendorInvoiceFilter filter, Pageable pageable) {
    return jpa.findAll(specification(organizationId, filter), pageable);
  }

  @Override
  public List<InvoicedQuantity> invoicedQuantities(UUID organizationId, UUID purchaseOrderId) {
    return jpa.sumInvoicedQuantities(organizationId, purchaseOrderId).stream()
        .map(
            projection ->
                new InvoicedQuantity(projection.getPurchaseOrderLineId(), projection.getQuantity()))
        .toList();
  }

  private static Specification<VendorInvoice> specification(
      UUID organizationId, VendorInvoiceFilter filter) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      predicates.add(cb.equal(root.get("organizationId"), organizationId));
      if (filter.status() != null) {
        predicates.add(cb.equal(root.get("status"), filter.status()));
      }
      if (filter.purchaseOrderId() != null) {
        predicates.add(cb.equal(root.get("purchaseOrderId"), filter.purchaseOrderId()));
      }
      if (filter.vendorId() != null) {
        predicates.add(cb.equal(root.get("vendorId"), filter.vendorId()));
      }
      if (filter.invoiceNumber() != null && !filter.invoiceNumber().isBlank()) {
        predicates.add(
            cb.like(
                cb.lower(root.get("invoiceNumber")),
                "%" + filter.invoiceNumber().trim().toLowerCase() + "%"));
      }
      if (filter.dateFrom() != null) {
        predicates.add(cb.greaterThanOrEqualTo(root.get("invoiceDate"), filter.dateFrom()));
      }
      if (filter.dateTo() != null) {
        predicates.add(cb.lessThanOrEqualTo(root.get("invoiceDate"), filter.dateTo()));
      }
      return cb.and(predicates.toArray(Predicate[]::new));
    };
  }
}
