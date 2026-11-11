package com.company.procurement.catalog.infrastructure;

import com.company.procurement.catalog.domain.CatalogItem;
import com.company.procurement.catalog.domain.CatalogItemRepository;
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
public class CatalogItemRepositoryAdapter implements CatalogItemRepository {

  private final JpaCatalogItemRepository jpa;

  public CatalogItemRepositoryAdapter(JpaCatalogItemRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public CatalogItem save(CatalogItem item) {
    return jpa.save(item);
  }

  @Override
  public Optional<CatalogItem> findByIdAndOrganizationId(UUID id, UUID organizationId) {
    return jpa.findByIdAndOrganizationId(id, organizationId);
  }

  @Override
  public boolean existsByOrganizationIdAndSku(UUID organizationId, String sku) {
    return jpa.existsByOrganizationIdAndSku(organizationId, sku);
  }

  @Override
  public boolean existsByOrganizationIdAndSkuAndIdNot(UUID organizationId, String sku, UUID id) {
    return jpa.existsByOrganizationIdAndSkuAndIdNot(organizationId, sku, id);
  }

  @Override
  public Page<CatalogItem> search(
      UUID organizationId, String category, Boolean active, String text, Pageable pageable) {
    return jpa.findAll(specification(organizationId, category, active, text), pageable);
  }

  private static Specification<CatalogItem> specification(
      UUID organizationId, String category, Boolean active, String text) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      predicates.add(cb.equal(root.get("organizationId"), organizationId));
      if (category != null && !category.isBlank()) {
        predicates.add(cb.equal(cb.lower(root.get("category")), category.trim().toLowerCase()));
      }
      if (active != null) {
        predicates.add(cb.equal(root.get("active"), active));
      }
      if (text != null && !text.isBlank()) {
        String pattern = "%" + text.trim().toLowerCase() + "%";
        predicates.add(
            cb.or(
                cb.like(cb.lower(root.get("sku")), pattern),
                cb.like(cb.lower(root.get("name")), pattern)));
      }
      return cb.and(predicates.toArray(Predicate[]::new));
    };
  }
}
