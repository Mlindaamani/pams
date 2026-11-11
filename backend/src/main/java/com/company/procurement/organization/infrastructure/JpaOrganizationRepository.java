package com.company.procurement.organization.infrastructure;

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

  Optional<Organization> findBySlug(String slug);

  boolean existsBySlug(String slug);

  boolean existsBySlugAndIdNot(String slug, UUID id);
}
