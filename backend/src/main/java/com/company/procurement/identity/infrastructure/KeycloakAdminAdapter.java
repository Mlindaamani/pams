package com.company.procurement.identity.infrastructure;

import com.company.procurement.identity.application.KeycloakAdminPort;
import com.company.procurement.shared.config.KeycloakAdminProperties;
import com.company.procurement.shared.error.BusinessRuleException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class KeycloakAdminAdapter implements KeycloakAdminPort {

  private final KeycloakAdminProperties properties;
  private final ObjectMapper objectMapper;
  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  public KeycloakAdminAdapter(KeycloakAdminProperties properties, ObjectMapper objectMapper) {
    this.properties = properties;
    this.objectMapper = objectMapper;
  }

  @Override
  public String createUser(
      UUID organizationId,
      String email,
      String displayName,
      Set<String> roles,
      String temporaryPassword) {
    requireConfigured();
    String token = token();
    String username = email.trim().toLowerCase();
    String[] parts = displayName.trim().split("\\s+", 2);
    Map<String, Object> body =
        Map.of(
            "username",
            username,
            "email",
            username,
            "firstName",
            parts[0],
            "lastName",
            parts.length > 1 ? parts[1] : "",
            "enabled",
            true,
            "emailVerified",
            true,
            "attributes",
            Map.of("organization_id", List.of(organizationId.toString())),
            "requiredActions",
            List.of("UPDATE_PASSWORD"),
            "credentials",
            List.of(Map.of("type", "password", "value", temporaryPassword, "temporary", true)));
    HttpResponse<String> response =
        send("POST", "/admin/realms/" + properties.realm() + "/users", token, body);
    if (response.statusCode() == 409) {
      throw new BusinessRuleException("A Keycloak user with this email already exists");
    }
    if (response.statusCode() >= 400) {
      throw new BusinessRuleException("Could not create Keycloak user");
    }
    String subject = response.headers().firstValue("Location").orElse("");
    subject = subject.substring(subject.lastIndexOf('/') + 1);
    if (subject.isBlank()) {
      throw new BusinessRuleException("Keycloak did not return the created user ID");
    }
    for (String role : roles) {
      JsonNode roleBody = get("/admin/realms/" + properties.realm() + "/roles/" + role, token);
      send(
          "POST",
          "/admin/realms/" + properties.realm() + "/users/" + subject + "/role-mappings/realm",
          token,
          List.of(Map.of("id", roleBody.path("id").asText(), "name", role)));
    }
    return subject;
  }

  @Override
  public KeycloakUserProfile userProfile(String subject) {
    if (!properties.configured() || subject == null || subject.isBlank()) return null;
    String token = token();
    HttpResponse<String> response =
        send(
            "GET",
            "/admin/realms/" + properties.realm() + "/users/" + encode(subject),
            token,
            null);
    if (response.statusCode() == 404) return null;
    if (response.statusCode() >= 400) {
      throw new BusinessRuleException("Could not read Keycloak user profile");
    }
    try {
      JsonNode node = objectMapper.readTree(response.body());
      return new KeycloakUserProfile(
          node.path("id").asText(subject),
          node.path("username").asText(""),
          node.path("email").asText(""),
          node.path("firstName").asText(""),
          node.path("lastName").asText(""));
    } catch (Exception ex) {
      throw new IllegalStateException("Could not parse Keycloak user profile", ex);
    }
  }

  @Override
  public void updateDisplayName(String subject, String displayName) {
    if (!properties.configured() || subject == null || subject.isBlank()) return;
    String token = token();
    JsonNode existing =
        get("/admin/realms/" + properties.realm() + "/users/" + encode(subject), token);
    if (existing == null || existing.isMissingNode() || existing.isNull()) return;
    String[] parts = displayName.trim().split("\\s+", 2);
    Map<String, Object> body = new java.util.LinkedHashMap<>();
    body.put("username", existing.path("username").asText());
    if (!existing.path("email").asText("").isBlank()) {
      body.put("email", existing.path("email").asText());
    }
    body.put("firstName", parts[0]);
    body.put("lastName", parts.length > 1 ? parts[1] : "");
    body.put("enabled", existing.path("enabled").asBoolean(true));
    body.put("emailVerified", existing.path("emailVerified").asBoolean(true));
    if (existing.has("attributes") && existing.get("attributes").isObject()) {
      body.put("attributes", objectMapper.convertValue(existing.get("attributes"), Map.class));
    }
    if (existing.has("requiredActions") && existing.get("requiredActions").isArray()) {
      body.put(
          "requiredActions",
          objectMapper.convertValue(existing.get("requiredActions"), java.util.List.class));
    }
    HttpResponse<String> response =
        send(
            "PUT",
            "/admin/realms/" + properties.realm() + "/users/" + encode(subject),
            token,
            body);
    if (response.statusCode() >= 400) {
      throw new BusinessRuleException("Could not update Keycloak user profile");
    }
  }

  @Override
  public String createOrResetUser(
      UUID organizationId,
      String email,
      String displayName,
      Set<String> roles,
      String temporaryPassword) {
    requireConfigured();
    String token = token();
    String normalizedEmail = email.trim().toLowerCase();
    String[] parts = displayName.trim().split("\\s+", 2);
    JsonNode existing =
        get(
            "/admin/realms/"
                + properties.realm()
                + "/users?email="
                + encode(normalizedEmail)
                + "&exact=true",
            token);
    if (existing.isEmpty()) {
      return createUser(organizationId, email, displayName, roles, temporaryPassword);
    }
    String subject = existing.get(0).path("id").asText();
    String existingUsername = existing.get(0).path("username").asText(normalizedEmail);
    Map<String, Object> profile =
        Map.of(
            "username",
            existingUsername,
            "email",
            normalizedEmail,
            "firstName",
            parts[0],
            "lastName",
            parts.length > 1 ? parts[1] : "",
            "enabled",
            true,
            "emailVerified",
            true,
            "attributes",
            Map.of("organization_id", List.of(organizationId.toString())));
    HttpResponse<String> updated =
        send("PUT", "/admin/realms/" + properties.realm() + "/users/" + subject, token, profile);
    if (updated.statusCode() >= 400) {
      throw new BusinessRuleException("Could not update Keycloak user");
    }
    HttpResponse<String> reset =
        send(
            "PUT",
            "/admin/realms/" + properties.realm() + "/users/" + subject + "/reset-password",
            token,
            Map.of("type", "password", "value", temporaryPassword, "temporary", true));
    if (reset.statusCode() >= 400) {
      throw new BusinessRuleException("Could not reset Keycloak user password");
    }
    for (String role : roles) {
      JsonNode roleBody = get("/admin/realms/" + properties.realm() + "/roles/" + role, token);
      send(
          "POST",
          "/admin/realms/" + properties.realm() + "/users/" + subject + "/role-mappings/realm",
          token,
          List.of(Map.of("id", roleBody.path("id").asText(), "name", role)));
    }
    return subject;
  }

  @Override
  public void disableUser(String subject) {
    if (!properties.configured() || subject == null || subject.isBlank()) return;
    send(
        "PUT",
        "/admin/realms/" + properties.realm() + "/users/" + subject,
        token(),
        Map.of("enabled", false));
  }

  private String token() {
    String authRealm;
    String form;
    if (properties.serviceAccountConfigured()) {
      authRealm = properties.realm();
      form =
          "grant_type=client_credentials&client_id="
              + encode(properties.clientId())
              + "&client_secret="
              + encode(properties.clientSecret());
    } else {
      authRealm =
          properties.authRealm() != null && !properties.authRealm().isBlank()
              ? properties.authRealm()
              : properties.realm();
      form =
          "grant_type=password&client_id=admin-cli&username="
              + encode(properties.adminUser())
              + "&password="
              + encode(properties.adminPassword());
    }
    try {
      HttpRequest request =
          HttpRequest.newBuilder(
                  URI.create(
                      properties.baseUrl()
                          + "/realms/"
                          + authRealm
                          + "/protocol/openid-connect/token"))
              .timeout(Duration.ofSeconds(15))
              .header("Content-Type", "application/x-www-form-urlencoded")
              .POST(HttpRequest.BodyPublishers.ofString(form))
              .build();
      HttpResponse<String> response =
          http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() >= 400) {
        throw new BusinessRuleException("Keycloak admin authentication failed");
      }
      return objectMapper.readTree(response.body()).path("access_token").asText();
    } catch (BusinessRuleException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new IllegalStateException("Could not authenticate to Keycloak admin API", ex);
    }
  }

  private JsonNode get(String path, String token) {
    HttpResponse<String> response = send("GET", path, token, null);
    if (response.statusCode() >= 400) {
      throw new BusinessRuleException("Could not read Keycloak role");
    }
    try {
      return objectMapper.readTree(response.body());
    } catch (Exception ex) {
      throw new IllegalStateException("Could not parse Keycloak response", ex);
    }
  }

  private HttpResponse<String> send(String method, String path, String token, Object body) {
    try {
      HttpRequest.Builder builder =
          HttpRequest.newBuilder(URI.create(properties.baseUrl() + path))
              .timeout(Duration.ofSeconds(20))
              .header("Authorization", "Bearer " + token)
              .header("Content-Type", "application/json");
      if (body == null) {
        builder.method(method, HttpRequest.BodyPublishers.noBody());
      } else {
        builder.method(
            method,
            HttpRequest.BodyPublishers.ofString(
                objectMapper.writeValueAsString(body), StandardCharsets.UTF_8));
      }
      return http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    } catch (Exception ex) {
      throw new IllegalStateException("Could not call Keycloak admin API", ex);
    }
  }

  private void requireConfigured() {
    if (!properties.configured()) {
      throw new BusinessRuleException("Keycloak admin provisioning is not configured");
    }
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
