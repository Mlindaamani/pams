package com.company.procurement.organization.application;

import com.company.procurement.identity.application.KeycloakAdminPort;
import com.company.procurement.organization.domain.UserProfile;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Resolves Keycloak role memberships without coupling user reads to Keycloak failures. */
@Service
public class UserRoleDirectoryService {

  private static final Logger log = LoggerFactory.getLogger(UserRoleDirectoryService.class);
  private static final List<String> TENANT_ROLES =
      List.of("ADMIN", "REQUESTER", "APPROVER", "PROCUREMENT_OFFICER", "FINANCE", "AUDITOR");

  private final KeycloakAdminPort keycloak;

  public UserRoleDirectoryService(KeycloakAdminPort keycloak) {
    this.keycloak = keycloak;
  }

  public Map<String, Set<String>> rolesFor(List<UserProfile> users) {
    if (users.isEmpty()) {
      return Map.of();
    }
    try {
      Map<String, Set<String>> memberships = keycloak.usersByRole(TENANT_ROLES);
      Map<String, Set<String>> visible = new HashMap<>();
      for (UserProfile user : users) {
        Set<String> roles = memberships.get(user.getKeycloakSubject());
        if (roles != null && !roles.isEmpty()) {
          visible.put(user.getKeycloakSubject(), roles);
        }
      }
      return visible;
    } catch (RuntimeException ex) {
      log.warn("Could not read Keycloak role memberships; returning users without roles", ex);
      return Map.of();
    }
  }
}
