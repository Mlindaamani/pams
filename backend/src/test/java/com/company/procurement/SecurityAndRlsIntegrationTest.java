package com.company.procurement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Sql(
    statements = {
      "DELETE FROM demo_request",
      "DELETE FROM outbox_event",
      "DELETE FROM audit_event",
      "DELETE FROM platform_audit_event",
      "DELETE FROM user_profile",
      "DELETE FROM vendor",
      "DELETE FROM catalog_item",
      "DELETE FROM department",
      "DELETE FROM tenant_provisioning",
      "DELETE FROM tenant_subscription",
      "DELETE FROM stripe_event",
      "DELETE FROM organization"
    },
    executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class SecurityAndRlsIntegrationTest {

  private static final UUID BOOTSTRAP_ORG = UUID.fromString("99999999-9999-9999-9999-999999999999");

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private JdbcTemplate jdbcTemplate;

  private UUID orgA;
  private UUID orgB;

  @BeforeEach
  void setUp() throws Exception {
    orgA = createOrganization("sec-org-a");
    orgB = createOrganization("sec-org-b");
  }

  @Test
  void firstLoginProvisioningCreatesAProfileAndOnboardingChecklist() throws Exception {
    mockMvc
        .perform(get("/api/v1/users/me").with(token(orgA, "REQUESTER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.email").exists())
        .andExpect(jsonPath("$.displayName").exists());

    mockMvc
        .perform(get("/api/v1/onboarding").with(token(orgA, "ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hasDepartment").value(true))
        .andExpect(jsonPath("$.totalSteps").value(5));
  }

  @Test
  void platformAdminsCanReadDemoRequests() throws Exception {
    jdbcTemplate.update(
        "insert into demo_request (name, email, company, message, status, created_at) values (?, ?, ?, ?, 'NEW', now())",
        "Prospect",
        "prospect@example.com",
        "Prospect Ltd",
        "Interested in PAMS");

    mockMvc
        .perform(get("/api/v1/platform/demo-requests").with(token(orgA, "PLATFORM_ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].name").value("Prospect"))
        .andExpect(jsonPath("$.content[0].status").value("NEW"));

    mockMvc
        .perform(get("/api/v1/platform/demo-requests").with(token(orgA, "ADMIN")))
        .andExpect(status().isForbidden());
  }

  @Test
  void publicPricingAndDemoRequestsAreAvailableWithoutAuthentication() throws Exception {
    mockMvc
        .perform(get("/api/v1/public/plans"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].code").exists())
        .andExpect(jsonPath("$[0].name").exists());

    mockMvc
        .perform(
            post("/api/v1/public/demo-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "name", "Prospect",
                            "email", "prospect@example.com",
                            "company", "Prospect Ltd",
                            "message", "We need procurement controls"))))
        .andExpect(status().isNoContent());

    mockMvc.perform(get("/api/v1/vendors")).andExpect(status().isUnauthorized());
  }

  @Test
  void rejectsUnauthenticatedRequestsWithProblemDetail() throws Exception {
    mockMvc
        .perform(get("/api/v1/vendors"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("unauthorized"));
  }

  @Test
  void rejectsRequestersFromProcurementMutations() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/vendors")
                .with(token(orgA, "REQUESTER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", "Acme", "taxId", "TAX-1"))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("forbidden"));
  }

  @Test
  void unauthenticatedRequestsAreRejectedOnAllResources() throws Exception {
    for (String path :
        List.of(
            "/api/v1/organizations",
            "/api/v1/departments",
            "/api/v1/users",
            "/api/v1/vendors",
            "/api/v1/catalog-items")) {
      mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
    }
  }

  @Test
  void requesterCannotMutateReferenceData() throws Exception {
    Map<String, Map<String, Object>> bodies =
        Map.of(
            "/api/v1/departments", Map.of("name", "Nope"),
            "/api/v1/users",
                Map.of(
                    "keycloakSubject", "nobody",
                    "email", "nobody@example.com",
                    "displayName", "Nobody",
                    "departmentId", UUID.randomUUID().toString()),
            "/api/v1/vendors", Map.of("name", "Nope", "taxId", "TAX-NO"),
            "/api/v1/catalog-items",
                Map.of(
                    "sku", "NOPE",
                    "name", "Nope",
                    "unit", "EA",
                    "defaultPrice", Map.of("amount", "1", "currency", "TZS")));

    for (Map.Entry<String, Map<String, Object>> entry : bodies.entrySet()) {
      mockMvc
          .perform(
              post(entry.getKey())
                  .with(token(orgA, "REQUESTER"))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(json(entry.getValue())))
          .andExpect(status().isForbidden());
    }
  }

  @Test
  void allowsProcurementOfficerToCreateVendors() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/vendors")
                .with(token(orgA, "PROCUREMENT_OFFICER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", "Acme", "taxId", "TAX-1"))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("ACTIVE"));
  }

  @Test
  void organizationListingIsScopedToTheTokenTenant() throws Exception {
    mockMvc
        .perform(get("/api/v1/organizations").with(token(orgA, "ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].id").value(orgA.toString()));

    mockMvc
        .perform(get("/api/v1/organizations/{id}", orgB).with(token(orgA, "ADMIN")))
        .andExpect(status().isNotFound());
  }

  @Test
  void rowLevelSecurityDeniesAndScopesTenantData() throws Exception {
    createDepartment(orgA, "Finance A");
    createDepartment(orgB, "Finance B");

    Long visibleWithoutTenant =
        jdbcTemplate.execute(
            (ConnectionCallback<Long>)
                connection -> {
                  try (Statement statement = connection.createStatement()) {
                    statement.execute("SET ROLE pams_app");
                    statement.execute("select set_config('app.current_org', '', false)");
                  }
                  try {
                    return countDepartments(connection);
                  } finally {
                    try (Statement statement = connection.createStatement()) {
                      statement.execute("RESET ROLE");
                    }
                  }
                });

    Long visibleForOrgA =
        jdbcTemplate.execute(
            (ConnectionCallback<Long>)
                connection -> {
                  try (Statement statement = connection.createStatement()) {
                    statement.execute("SET ROLE pams_app");
                  }
                  setTenant(connection, orgA);
                  try {
                    return countDepartments(connection);
                  } finally {
                    try (Statement statement = connection.createStatement()) {
                      statement.execute("RESET ROLE");
                    }
                  }
                });

    Long visibleForOrgB =
        jdbcTemplate.execute(
            (ConnectionCallback<Long>)
                connection -> {
                  try (Statement statement = connection.createStatement()) {
                    statement.execute("SET ROLE pams_app");
                  }
                  setTenant(connection, orgB);
                  try {
                    return countDepartments(connection);
                  } finally {
                    try (Statement statement = connection.createStatement()) {
                      statement.execute("RESET ROLE");
                    }
                  }
                });

    assertThat(visibleWithoutTenant).isZero();
    assertThat(visibleForOrgA).isEqualTo(1L);
    assertThat(visibleForOrgB).isEqualTo(1L);
  }

  private static Long countDepartments(Connection connection) throws java.sql.SQLException {
    try (Statement statement = connection.createStatement();
        ResultSet resultSet = statement.executeQuery("select count(*) from department")) {
      resultSet.next();
      return resultSet.getLong(1);
    }
  }

  private static void setTenant(Connection connection, UUID organizationId)
      throws java.sql.SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement("select set_config('app.current_org', ?, false)")) {
      statement.setString(1, organizationId.toString());
      statement.execute();
    }
  }

  private UUID createOrganization(String slug) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/organizations")
                    .with(token(BOOTSTRAP_ORG, "ADMIN"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(Map.of("name", slug, "slug", slug))))
            .andExpect(status().isCreated())
            .andReturn();
    return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
  }

  private void createDepartment(UUID organizationId, String name) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/departments")
                .with(token(organizationId, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", name))))
        .andExpect(status().isCreated());
  }

  private RequestPostProcessor token(UUID organizationId, String... roles) {
    return jwt()
        .jwt(
            jwt ->
                jwt.subject("test-user")
                    .claim("organization_id", organizationId.toString())
                    .claim("realm_access", Map.of("roles", List.of(roles))))
        .authorities(authorities(roles));
  }

  private List<GrantedAuthority> authorities(String... roles) {
    return Arrays.stream(roles)
        .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
        .toList();
  }

  private String json(Object value) throws Exception {
    return objectMapper.writeValueAsString(value);
  }
}
