package com.company.procurement.organization.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserProfileRepository {

  UserProfile save(UserProfile userProfile);

  Optional<UserProfile> findByIdAndOrganizationId(UUID id, UUID organizationId);

  Optional<UserProfile> findByOrganizationIdAndKeycloakSubject(
      UUID organizationId, String keycloakSubject);

  boolean existsByOrganizationIdAndKeycloakSubject(UUID organizationId, String keycloakSubject);

  boolean existsByOrganizationIdAndKeycloakSubjectAndIdNot(
      UUID organizationId, String keycloakSubject, UUID id);

  Page<UserProfile> search(
      UUID organizationId, UUID departmentId, Boolean active, String text, Pageable pageable);
}
