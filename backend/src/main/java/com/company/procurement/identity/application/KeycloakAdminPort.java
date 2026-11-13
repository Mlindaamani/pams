package com.company.procurement.identity.application;

import java.util.Set;
import java.util.UUID;

public interface KeycloakAdminPort {

  String createUser(
      UUID organizationId,
      String email,
      String displayName,
      Set<String> roles,
      String temporaryPassword);

  KeycloakUserProfile userProfile(String subject);

  void updateDisplayName(String subject, String displayName);

  String createOrResetUser(
      UUID organizationId,
      String email,
      String displayName,
      Set<String> roles,
      String temporaryPassword);

  void disableUser(String subject);

  void deleteUser(String subject);

  record KeycloakUserProfile(
      String subject, String username, String email, String firstName, String lastName) {}
}
