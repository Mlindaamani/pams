package com.company.procurement.organization.application;

import com.company.procurement.organization.domain.UserProfile;
import com.company.procurement.organization.domain.UserProfileRepository;
import com.company.procurement.shared.tenant.TenantContext;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only lookups other modules may use without touching organization repositories. */
@Service
public class UserProfileLookupService {

  private final UserProfileRepository users;

  public UserProfileLookupService(UserProfileRepository users) {
    this.users = users;
  }

  @Transactional(readOnly = true)
  public Optional<UserProfile> findBySubject(String subject) {
    return users.findByOrganizationIdAndKeycloakSubject(TenantContext.require(), subject);
  }

  @Transactional(readOnly = true)
  public Optional<UserProfile> findById(UUID id) {
    return users.findByIdAndOrganizationId(id, TenantContext.require());
  }
}
