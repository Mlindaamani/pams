package com.company.procurement.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pams.keycloak-admin")
public record KeycloakAdminProperties(
    String baseUrl,
    String realm,
    String authRealm,
    String clientId,
    String clientSecret,
    String adminUser,
    String adminPassword) {

  public boolean configured() {
    boolean serviceAccount = serviceAccountConfigured();
    boolean hasAdminPassword =
        adminUser != null
            && !adminUser.isBlank()
            && adminPassword != null
            && !adminPassword.isBlank();
    return baseUrl != null
        && !baseUrl.isBlank()
        && realm != null
        && !realm.isBlank()
        && (serviceAccount || hasAdminPassword);
  }

  public boolean serviceAccountConfigured() {
    return clientId != null
        && !clientId.isBlank()
        && clientSecret != null
        && !clientSecret.isBlank();
  }

  public boolean passwordGrantConfigured() {
    return adminUser != null
        && !adminUser.isBlank()
        && adminPassword != null
        && !adminPassword.isBlank();
  }
}
