package com.company.procurement.organization.application;

import com.company.procurement.identity.application.KeycloakAdminPort;
import com.company.procurement.organization.domain.UserProfile;
import com.company.procurement.organization.domain.UserProfileRepository;
import com.company.procurement.shared.security.CurrentUser;
import com.company.procurement.shared.tenant.TenantContext;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Synchronizes Keycloak identity data with local user profiles. */
@Service
public class UserIdentitySyncService {

  private static final Logger log = LoggerFactory.getLogger(UserIdentitySyncService.class);

  private final KeycloakAdminPort keycloak;
  private final UserProfileRepository users;
  private final CurrentUser currentUser;

  public UserIdentitySyncService(
      KeycloakAdminPort keycloak, UserProfileRepository users, CurrentUser currentUser) {
    this.keycloak = keycloak;
    this.users = users;
    this.currentUser = currentUser;
  }

  public void syncCurrentIdentity() {
    UUID organizationId = TenantContext.require();
    KeycloakAdminPort.KeycloakUserProfile profile;
    try {
      profile = keycloak.userProfile(currentUser.subject());
    } catch (RuntimeException ex) {
      log.warn("Could not synchronize the current user's Keycloak profile", ex);
      return;
    }
    if (profile == null) return;
    String displayName = (profile.firstName() + " " + profile.lastName()).trim();
    if (displayName.isBlank()) displayName = profile.username();
    if (displayName.isBlank() || profile.email().isBlank()) return;
    apply(organizationId, currentUser.subject(), profile.email(), displayName);
  }

  public void pushDisplayName(String displayName) {
    keycloak.updateDisplayName(currentUser.subject(), displayName);
  }

  @Transactional
  public void apply(UUID organizationId, String subject, String email, String displayName) {
    UserProfile profile =
        users.findByOrganizationIdAndKeycloakSubject(organizationId, subject).orElse(null);
    if (profile == null) return;
    if (profile.getEmail().equals(email) && profile.getDisplayName().equals(displayName)) return;
    profile.synchronizeIdentity(email, displayName);
    users.save(profile);
  }
}
