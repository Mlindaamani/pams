package com.company.procurement.organization.repositories;

import com.company.procurement.organization.domain.Organization;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface JpaOrganizationRepository
    extends JpaRepository<Organization, UUID>, JpaSpecificationExecutor<Organization> {

  @Query("select o.id from Organization o")
  List<UUID> findAllIds();

  List<Organization> findAllByIdIn(java.util.Collection<UUID> ids);

  Optional<Organization> findBySlug(String slug);

  boolean existsBySlug(String slug);

  boolean existsBySlugAndIdNot(String slug, UUID id);
}
