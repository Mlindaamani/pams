package com.company.procurement.vendor.infrastructure;

import com.company.procurement.vendor.domain.Vendor;
import com.company.procurement.vendor.domain.VendorRepository;
import com.company.procurement.vendor.domain.VendorStatus;
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
public class VendorRepositoryAdapter implements VendorRepository {

  private final JpaVendorRepository jpa;

  public VendorRepositoryAdapter(JpaVendorRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public Vendor save(Vendor vendor) {
    return jpa.save(vendor);
  }

  @Override
  public Optional<Vendor> findByIdAndOrganizationId(UUID id, UUID organizationId) {
    return jpa.findByIdAndOrganizationId(id, organizationId);
  }

  @Override
  public boolean existsByOrganizationIdAndTaxId(UUID organizationId, String taxId) {
    return jpa.existsByOrganizationIdAndTaxId(organizationId, taxId);
  }

  @Override
  public boolean existsByOrganizationIdAndTaxIdAndIdNot(
      UUID organizationId, String taxId, UUID id) {
    return jpa.existsByOrganizationIdAndTaxIdAndIdNot(organizationId, taxId, id);
  }

  @Override
  public Page<Vendor> search(
      UUID organizationId, VendorStatus status, String text, Pageable pageable) {
    return jpa.findAll(specification(organizationId, status, text), pageable);
  }

  private static Specification<Vendor> specification(
      UUID organizationId, VendorStatus status, String text) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      predicates.add(cb.equal(root.get("organizationId"), organizationId));
      if (status != null) {
        predicates.add(cb.equal(root.get("status"), status));
      }
      if (text != null && !text.isBlank()) {
        String pattern = "%" + text.trim().toLowerCase() + "%";
        predicates.add(
            cb.or(
                cb.like(cb.lower(root.get("name")), pattern),
                cb.like(cb.lower(root.get("taxId")), pattern)));
      }
      return cb.and(predicates.toArray(Predicate[]::new));
    };
  }
}
