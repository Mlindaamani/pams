package com.company.procurement.organization.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Persistence port for the {@link Organization} aggregate. Implemented in infrastructure. */
public interface OrganizationRepository {

  Organization save(Organization organization);

  Optional<Organization> findById(UUID id);

  Optional<Organization> findBySlug(String slug);

  boolean existsBySlug(String slug);

  boolean existsBySlugAndIdNot(String slug, UUID id);

  Page<Organization> search(UUID id, String name, String slug, Pageable pageable);

  Page<Organization> searchAll(
      String query,
      com.company.procurement.organization.domain.OrganizationStatus status,
      Pageable pageable);

  List<UUID> findAllIds();

  void flush();
}
