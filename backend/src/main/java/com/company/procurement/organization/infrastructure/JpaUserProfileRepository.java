package com.company.procurement.organization.infrastructure;

import com.company.procurement.organization.domain.UserProfile;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface JpaUserProfileRepository
    extends JpaRepository<UserProfile, UUID>, JpaSpecificationExecutor<UserProfile> {

  Optional<UserProfile> findByIdAndOrganizationId(UUID id, UUID organizationId);

  Optional<UserProfile> findByOrganizationIdAndKeycloakSubject(
      UUID organizationId, String keycloakSubject);

  boolean existsByOrganizationIdAndKeycloakSubject(UUID organizationId, String keycloakSubject);

  boolean existsByOrganizationIdAndKeycloakSubjectAndIdNot(
      UUID organizationId, String keycloakSubject, UUID id);
}
