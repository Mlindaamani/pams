package com.company.procurement.organization.infrastructure;

import com.company.procurement.organization.domain.Organization;
import com.company.procurement.organization.domain.OrganizationRepository;
import com.company.procurement.organization.domain.OrganizationStatus;
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
public class OrganizationRepositoryAdapter implements OrganizationRepository {

  private final JpaOrganizationRepository jpa;

  public OrganizationRepositoryAdapter(JpaOrganizationRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public Organization save(Organization organization) {
    return jpa.save(organization);
  }

  @Override
  public Optional<Organization> findById(UUID id) {
    return jpa.findById(id);
  }

  @Override
  public Optional<Organization> findBySlug(String slug) {
    return jpa.findBySlug(slug);
  }

  @Override
  public boolean existsBySlug(String slug) {
    return jpa.existsBySlug(slug);
  }

  @Override
  public boolean existsBySlugAndIdNot(String slug, UUID id) {
    return jpa.existsBySlugAndIdNot(slug, id);
  }

  @Override
  public void flush() {
    jpa.flush();
  }

  @Override
  public List<UUID> findAllIds() {
    return jpa.findAllIds();
  }

  @Override
  public Page<Organization> search(UUID id, String name, String slug, Pageable pageable) {
    return jpa.findAll(specification(id, name, slug), pageable);
  }

  @Override
  public Page<Organization> searchAll(String query, OrganizationStatus status, Pageable pageable) {
    return jpa.findAll(platformSpecification(query, status), pageable);
  }

  private static Specification<Organization> platformSpecification(
      String query, OrganizationStatus status) {
    return (root, criteriaQuery, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      if (status != null) {
        predicates.add(cb.equal(root.get("status"), status));
      }
      if (query != null && !query.isBlank()) {
        String pattern = "%" + query.trim().toLowerCase() + "%";
        predicates.add(
            cb.or(
                cb.like(cb.lower(root.get("name")), pattern),
                cb.like(cb.lower(root.get("slug")), pattern)));
      }
      return cb.and(predicates.toArray(Predicate[]::new));
    };
  }

  private static Specification<Organization> specification(UUID id, String name, String slug) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      if (id != null) {
        predicates.add(cb.equal(root.get("id"), id));
      }
      if (name != null && !name.isBlank()) {
        predicates.add(cb.like(cb.lower(root.get("name")), "%" + name.trim().toLowerCase() + "%"));
      }
      if (slug != null && !slug.isBlank()) {
        predicates.add(cb.equal(cb.lower(root.get("slug")), slug.trim().toLowerCase()));
      }
      return cb.and(predicates.toArray(Predicate[]::new));
    };
  }
}
