package com.company.procurement.organization.infrastructure;

import com.company.procurement.organization.domain.UserProfile;
import com.company.procurement.organization.domain.UserProfileRepository;
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
public class UserProfileRepositoryAdapter implements UserProfileRepository {

  private final JpaUserProfileRepository jpa;

  public UserProfileRepositoryAdapter(JpaUserProfileRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public UserProfile save(UserProfile userProfile) {
    return jpa.save(userProfile);
  }

  @Override
  public Optional<UserProfile> findByIdAndOrganizationId(UUID id, UUID organizationId) {
    return jpa.findByIdAndOrganizationId(id, organizationId);
  }

  @Override
  public Optional<UserProfile> findByOrganizationIdAndKeycloakSubject(
      UUID organizationId, String keycloakSubject) {
    return jpa.findByOrganizationIdAndKeycloakSubject(organizationId, keycloakSubject);
  }

  @Override
  public boolean existsByOrganizationIdAndKeycloakSubject(
      UUID organizationId, String keycloakSubject) {
    return jpa.existsByOrganizationIdAndKeycloakSubject(organizationId, keycloakSubject);
  }

  @Override
  public boolean existsByOrganizationIdAndKeycloakSubjectAndIdNot(
      UUID organizationId, String keycloakSubject, UUID id) {
    return jpa.existsByOrganizationIdAndKeycloakSubjectAndIdNot(
        organizationId, keycloakSubject, id);
  }

  @Override
  public Page<UserProfile> search(
      UUID organizationId, UUID departmentId, Boolean active, String text, Pageable pageable) {
    return jpa.findAll(specification(organizationId, departmentId, active, text), pageable);
  }

  private static Specification<UserProfile> specification(
      UUID organizationId, UUID departmentId, Boolean active, String text) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      predicates.add(cb.equal(root.get("organizationId"), organizationId));
      if (departmentId != null) {
        predicates.add(cb.equal(root.get("departmentId"), departmentId));
      }
      if (active != null) {
        predicates.add(cb.equal(root.get("active"), active));
      }
      if (text != null && !text.isBlank()) {
        String pattern = "%" + text.trim().toLowerCase() + "%";
        predicates.add(
            cb.or(
                cb.like(cb.lower(root.get("email")), pattern),
                cb.like(cb.lower(root.get("displayName")), pattern)));
      }
      return cb.and(predicates.toArray(Predicate[]::new));
    };
  }
}
