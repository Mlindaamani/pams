package com.company.procurement;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.util.Arrays;
import java.util.HashMap;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@Sql(
    statements = {
      "DELETE FROM platform_audit_event",
      "DELETE FROM idempotency_record",
      "DELETE FROM audit_event",
      "DELETE FROM outbox_event",
      "DELETE FROM notification",
      "DELETE FROM user_profile",
      "DELETE FROM vendor",
      "DELETE FROM catalog_item",
      "DELETE FROM department",
      "DELETE FROM tenant_provisioning",
      "DELETE FROM tenant_subscription",
      "DELETE FROM subscription_mrr_snapshot",
      "DELETE FROM subscription_plan WHERE code LIKE 'TEST%'",
      "DELETE FROM subscription_feature WHERE code LIKE 'test%'",
      "DELETE FROM stripe_event",
      "DELETE FROM organization"
    },
    executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class ReferenceDataApiIntegrationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired
  private com.company.procurement.subscription.application.SubscriptionLifecycleService
      subscriptionLifecycle;

  private UUID orgA;
  private UUID orgB;

  @BeforeEach
  void setUp() throws Exception {
    orgA = createOrganization("org-a", "Org A");
    orgB = createOrganization("org-b", "Org B");
  }

  @Test
  void organizationCrudAndDuplicateSlug() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/organizations")
                .with(token(BOOTSTRAP_ORG, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", "Org C", "slug", "org-c"))))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", containsString("/api/v1/organizations/")))
        .andExpect(jsonPath("$.slug").value("org-c"));

    mockMvc
        .perform(get("/api/v1/organizations/{id}", orgA).with(token(orgA, "ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Org A"));

    mockMvc
        .perform(get("/api/v1/organizations").with(token(orgA, "ADMIN")).param("slug", "org-a"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].slug").value("org-a"));

    mockMvc
        .perform(
            put("/api/v1/organizations/{id}", orgA)
                .with(token(orgA, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", "Org A Renamed"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Org A Renamed"));

    mockMvc
        .perform(
            post("/api/v1/organizations")
                .with(token(orgA, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", "Duplicate", "slug", "org-a"))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("conflict"));
  }

  @Test
  void requiresTenantClaimAndWhitelistsSortFields() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/departments")
                .with(tokenWithoutTenant("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", "Finance"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("invalid-request"));

    mockMvc
        .perform(get("/api/v1/vendors").with(token(orgA, "ADMIN")).param("sort", "password,desc"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("invalid-request"));
  }

  @Test
  void departmentCrudIsTenantIsolated() throws Exception {
    UUID finance = createDepartment(orgA, "Finance");

    mockMvc
        .perform(
            post("/api/v1/departments")
                .with(token(orgA, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", "finance"))))
        .andExpect(status().isConflict());

    mockMvc
        .perform(get("/api/v1/departments/{id}", finance).with(token(orgB, "ADMIN")))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(get("/api/v1/departments").with(token(orgB, "ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));

    mockMvc
        .perform(
            put("/api/v1/departments/{id}", finance)
                .with(token(orgA, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", "Operations"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Operations"));

    mockMvc
        .perform(delete("/api/v1/departments/{id}", finance).with(token(orgA, "ADMIN")))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/v1/departments/{id}", finance).with(token(orgA, "ADMIN")))
        .andExpect(status().isNotFound());
  }

  @Test
  void userProfilesEnforceManagerAndDepartmentRules() throws Exception {
    UUID finance = createDepartment(orgA, "Finance");

    UUID alice =
        createUser(
            orgA,
            Map.of(
                "keycloakSubject", "sub-alice",
                "email", "alice@example.com",
                "displayName", "Alice",
                "departmentId", finance.toString(),
                "approvalLimit", Map.of("amount", "1000.5", "currency", "TZS")));

    mockMvc
        .perform(get("/api/v1/users/{id}", alice).with(token(orgA, "ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.approvalLimit.amount").value("1000.5000"));

    UUID bob =
        createUser(
            orgA,
            Map.of(
                "keycloakSubject", "sub-bob",
                "email", "bob@example.com",
                "displayName", "Bob",
                "departmentId", finance.toString(),
                "managerId", alice.toString()));

    updateUserExpecting(orgA, alice, finance, bob, status().isUnprocessableEntity());
    updateUserExpecting(orgA, alice, finance, alice, status().isUnprocessableEntity());

    Map<String, Object> badDepartment = new HashMap<>();
    badDepartment.put("keycloakSubject", "sub-carol");
    badDepartment.put("email", "carol@example.com");
    badDepartment.put("displayName", "Carol");
    badDepartment.put("departmentId", createDepartment(orgB, "Finance B").toString());
    mockMvc
        .perform(
            post("/api/v1/users")
                .with(token(orgA, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(badDepartment)))
        .andExpect(status().isUnprocessableEntity());

    mockMvc
        .perform(
            post("/api/v1/users")
                .with(token(orgA, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(userBody("sub-alice", "alice2@example.com", finance))))
        .andExpect(status().isConflict());

    mockMvc
        .perform(
            get("/api/v1/users")
                .with(token(orgA, "ADMIN"))
                .param("departmentId", finance.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2));
  }

  @Test
  void vendorStatusLifecycleAndSoftDelete() throws Exception {
    UUID vendor = createVendor(orgA, "Acme Supplies", "TAX-100");

    mockMvc
        .perform(
            post("/api/v1/vendors")
                .with(token(orgA, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", "Other", "taxId", "TAX-100"))))
        .andExpect(status().isConflict());

    mockMvc
        .perform(get("/api/v1/vendors/{id}", vendor).with(token(orgB, "ADMIN")))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(post("/api/v1/vendors/{id}/suspend", vendor).with(token(orgA, "ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUSPENDED"));

    mockMvc
        .perform(post("/api/v1/vendors/{id}/blacklist", vendor).with(token(orgA, "ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("BLACKLISTED"));

    mockMvc
        .perform(post("/api/v1/vendors/{id}/activate", vendor).with(token(orgA, "ADMIN")))
        .andExpect(status().isConflict());

    mockMvc
        .perform(get("/api/v1/vendors").with(token(orgA, "ADMIN")).param("status", "BLACKLISTED"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1));

    mockMvc
        .perform(delete("/api/v1/vendors/{id}", vendor).with(token(orgA, "ADMIN")))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/v1/vendors/{id}", vendor).with(token(orgA, "ADMIN")))
        .andExpect(status().isNotFound());

    createVendor(orgA, "Acme Again", "TAX-100");
  }

  @Test
  void catalogItemsExposeMoneyAndFilters() throws Exception {
    UUID item =
        createCatalogItem(
            orgA,
            Map.of(
                "sku", "SKU-1",
                "name", "Laptop",
                "unit", "EA",
                "defaultPrice", Map.of("amount", "12.5", "currency", "TZS"),
                "category", "IT"));

    mockMvc
        .perform(get("/api/v1/catalog-items/{id}", item).with(token(orgA, "ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.defaultPrice.amount").value("12.5000"))
        .andExpect(jsonPath("$.defaultPrice.currency").value("TZS"));

    mockMvc
        .perform(
            post("/api/v1/catalog-items")
                .with(token(orgA, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "sku", "SKU-1",
                            "name", "Duplicate",
                            "unit", "EA",
                            "defaultPrice", Map.of("amount", "1", "currency", "TZS")))))
        .andExpect(status().isConflict());

    mockMvc
        .perform(
            put("/api/v1/catalog-items/{id}", item)
                .with(token(orgA, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "name", "Laptop Pro",
                            "unit", "EA",
                            "defaultPrice", Map.of("amount", "20", "currency", "TZS"),
                            "category", "IT",
                            "active", false))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.defaultPrice.amount").value("20.0000"))
        .andExpect(jsonPath("$.active").value(false));

    mockMvc
        .perform(
            get("/api/v1/catalog-items")
                .with(token(orgA, "ADMIN"))
                .param("active", "false")
                .param("category", "IT")
                .param("q", "laptop"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1));

    mockMvc
        .perform(delete("/api/v1/catalog-items/{id}", item).with(token(orgA, "ADMIN")))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/v1/catalog-items").with(token(orgA, "ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  void vendorUpdateUniquenessAndReactivation() throws Exception {
    UUID vendor = createVendor(orgA, "Acme Supplies", "TAX-300");
    createVendor(orgA, "Beta Supplies", "TAX-301");

    mockMvc
        .perform(
            put("/api/v1/vendors/{id}", vendor)
                .with(token(orgA, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "name", "Acme Trading",
                            "taxId", "TAX-300",
                            "contactName", "Jane",
                            "contactEmail", "jane@acme.test"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Acme Trading"))
        .andExpect(jsonPath("$.contactEmail").value("jane@acme.test"));

    mockMvc
        .perform(
            put("/api/v1/vendors/{id}", vendor)
                .with(token(orgA, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", "Acme Trading", "taxId", "TAX-301"))))
        .andExpect(status().isConflict());

    mockMvc
        .perform(post("/api/v1/vendors/{id}/suspend", vendor).with(token(orgA, "ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUSPENDED"));

    mockMvc
        .perform(post("/api/v1/vendors/{id}/activate", vendor).with(token(orgA, "ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVE"));
  }

  @Test
  void paginationCapsPageSize() throws Exception {
    for (int i = 0; i < 3; i++) {
      createCatalogItem(
          orgA,
          Map.of(
              "sku",
              "BULK-" + i,
              "name",
              "Bulk " + i,
              "unit",
              "EA",
              "defaultPrice",
              Map.of("amount", "1", "currency", "TZS")));
    }

    mockMvc
        .perform(
            get("/api/v1/catalog-items")
                .with(token(orgA, "ADMIN"))
                .param("size", "1000")
                .param("sort", "name,asc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.size").value(100))
        .andExpect(jsonPath("$.totalElements").value(3))
        .andExpect(jsonPath("$.content[0].name").value("Bulk 0"));
  }

  private static final UUID BOOTSTRAP_ORG = UUID.fromString("99999999-9999-9999-9999-999999999999");

  private RequestPostProcessor token(UUID organizationId, String... roles) {
    return jwt()
        .jwt(
            jwt ->
                jwt.subject("test-user")
                    .claim("organization_id", organizationId.toString())
                    .claim("realm_access", java.util.Map.of("roles", List.of(roles))))
        .authorities(authorities(roles));
  }

  private RequestPostProcessor tokenWithoutTenant(String... roles) {
    return jwt()
        .jwt(
            jwt ->
                jwt.subject("test-user")
                    .claim("realm_access", java.util.Map.of("roles", List.of(roles))))
        .authorities(authorities(roles));
  }

  private List<GrantedAuthority> authorities(String... roles) {
    return Arrays.stream(roles)
        .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
        .toList();
  }

  @Test
  void systemStatusRequiresAdmin() throws Exception {
    mockMvc
        .perform(get("/api/v1/admin/system/status").with(token(orgA, "ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"))
        .andExpect(jsonPath("$.database").value("UP"))
        .andExpect(jsonPath("$.version").exists());

    mockMvc
        .perform(get("/api/v1/admin/system/status").with(token(orgA, "REQUESTER")))
        .andExpect(status().isForbidden());
  }

  @Test
  void platformTenantManagementAndAnalyticsArePermissionControlled() throws Exception {
    String tenantSlug = "platform-tenant-" + UUID.randomUUID().toString().substring(0, 8);
    MvcResult created =
        mockMvc
            .perform(
                post("/api/v1/platform/tenants")
                    .with(token(orgA, "PLATFORM_ADMIN"))
                    .header("Idempotency-Key", "tenant-create")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json(
                            Map.of(
                                "name",
                                "Platform Tenant",
                                "slug",
                                tenantSlug,
                                "invoiceTolerancePercent",
                                3))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andReturn();
    UUID tenantId = idOf(created);

    mockMvc
        .perform(get("/api/v1/subscription").with(token(tenantId, "ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("TRIALING"))
        .andExpect(jsonPath("$.plan.code").value("GROWTH"));

    mockMvc
        .perform(get("/api/v1/subscription/plans").with(token(tenantId, "ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(3));

    mockMvc
        .perform(get("/api/v1/platform/subscriptions").with(token(orgA, "PLATFORM_ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].planCode").value("GROWTH"));

    mockMvc
        .perform(
            get("/api/v1/platform/tenants")
                .param("q", tenantSlug)
                .with(token(orgA, "PLATFORM_ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].slug").value(tenantSlug));

    mockMvc
        .perform(get("/api/v1/platform/tenants").with(token(orgA, "FINANCE")))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(get("/api/v1/platform/analytics/dashboard").with(token(orgA, "PLATFORM_ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.summary.totalTenants").isNumber())
        .andExpect(jsonPath("$.tenants").isArray());

    mockMvc
        .perform(
            post("/api/v1/platform/tenants/{id}/suspend", tenantId)
                .with(token(orgA, "PLATFORM_ADMIN"))
                .header("If-Match", created.getResponse().getHeader("ETag"))
                .header("Idempotency-Key", "tenant-suspend"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUSPENDED"));

    mockMvc
        .perform(get("/api/v1/vendors").with(token(tenantId, "ADMIN")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("tenant-suspended"));
  }

  @Test
  void platformSubscriptionOperationsMetricsAndTenantDetail() throws Exception {
    String tenantSlug = "saas-tenant-" + UUID.randomUUID().toString().substring(0, 8);
    MvcResult created =
        mockMvc
            .perform(
                post("/api/v1/platform/tenants")
                    .with(token(orgA, "PLATFORM_ADMIN"))
                    .header("Idempotency-Key", "saas-tenant-create")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json(
                            Map.of(
                                "name",
                                "SaaS Tenant",
                                "slug",
                                tenantSlug,
                                "invoiceTolerancePercent",
                                2,
                                "planCode",
                                "GROWTH"))))
            .andExpect(status().isCreated())
            .andReturn();
    UUID tenantId = idOf(created);
    Map<String, Object> initialBilling =
        jdbcTemplate.queryForMap(
            "select billing_source, monthly_price_snapshot from tenant_subscription where organization_id = ?",
            tenantId);
    assertEquals("TRIAL", initialBilling.get("billing_source"));
    assertEquals(
        0,
        new java.math.BigDecimal("99.0000")
            .compareTo((java.math.BigDecimal) initialBilling.get("monthly_price_snapshot")));

    mockMvc
        .perform(
            get("/api/v1/platform/tenants/{id}/detail", tenantId)
                .with(token(orgA, "PLATFORM_ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tenant.slug").value(tenantSlug))
        .andExpect(jsonPath("$.subscription.planCode").value("GROWTH"))
        .andExpect(jsonPath("$.usage.users").isNumber());

    mockMvc
        .perform(get("/api/v1/platform/subscriptions/metrics").with(token(orgA, "PLATFORM_ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mrr").isNumber())
        .andExpect(jsonPath("$.trialingTenants").isNumber())
        .andExpect(jsonPath("$.planRevenue").isArray())
        .andExpect(jsonPath("$.mrrTrend").isArray());

    mockMvc
        .perform(get("/api/v1/platform/subscriptions/plans").with(token(orgA, "PLATFORM_ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(3));

    mockMvc
        .perform(
            post("/api/v1/platform/tenants/{id}/subscription/change-plan", tenantId)
                .with(token(orgA, "PLATFORM_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("planCode", "STARTER", "billingInterval", "ANNUAL"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.plan.code").value("STARTER"))
        .andExpect(jsonPath("$.billingInterval").value("ANNUAL"));
    Map<String, Object> changedBilling =
        jdbcTemplate.queryForMap(
            "select billing_source, monthly_price_snapshot, annual_price_snapshot from tenant_subscription where organization_id = ?",
            tenantId);
    assertEquals("TRIAL", changedBilling.get("billing_source"));
    assertEquals(
        0,
        new java.math.BigDecimal("29.0000")
            .compareTo((java.math.BigDecimal) changedBilling.get("monthly_price_snapshot")));
    assertEquals(
        0,
        new java.math.BigDecimal("290.0000")
            .compareTo((java.math.BigDecimal) changedBilling.get("annual_price_snapshot")));

    mockMvc
        .perform(
            post("/api/v1/platform/tenants/{id}/subscription/extend-trial", tenantId)
                .with(token(orgA, "PLATFORM_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("days", 7))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("TRIALING"));

    mockMvc
        .perform(
            post("/api/v1/platform/tenants/{id}/subscription/activate", tenantId)
                .with(token(orgA, "PLATFORM_ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.currentPeriodEnd").exists());
    assertEquals(
        "MANUAL",
        jdbcTemplate.queryForObject(
            "select billing_source from tenant_subscription where organization_id = ?",
            String.class,
            tenantId));

    mockMvc
        .perform(
            post("/api/v1/subscription/change-preview")
                .with(token(tenantId, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("planCode", "GROWTH", "interval", "MONTHLY"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.billingSource").value("MANUAL"))
        .andExpect(jsonPath("$.amountDue").value(0));

    mockMvc
        .perform(
            get("/api/v1/platform/subscriptions/reconciliation")
                .with(token(orgA, "PLATFORM_ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.checked").isNumber())
        .andExpect(jsonPath("$.issues").isArray());

    mockMvc
        .perform(get("/api/v1/platform/subscriptions/metrics").with(token(orgA, "PLATFORM_ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.payingTenants").isNumber())
        .andExpect(jsonPath("$.mrr").isNumber());

    mockMvc
        .perform(
            post("/api/v1/platform/tenants/{id}/subscription/cancel", tenantId)
                .with(token(orgA, "PLATFORM_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("atPeriodEnd", true))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.cancelAtPeriodEnd").value(true));

    mockMvc
        .perform(
            post("/api/v1/platform/tenants/{id}/subscription/reactivate", tenantId)
                .with(token(orgA, "PLATFORM_ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.cancelAtPeriodEnd").value(false));

    mockMvc
        .perform(get("/api/v1/platform/tenants").with(token(orgA, "ADMIN")))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(get("/api/v1/subscription").with(token(orgA, "PLATFORM_ADMIN")))
        .andExpect(status().isForbidden());
  }

  @Test
  void platformSubscriptionPlanCrudIsPermissionControlled() throws Exception {
    String code = "TEST_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    Map<String, Object> plan = new HashMap<>();
    plan.put("code", code);
    plan.put("name", "Test Plan");
    plan.put("description", "Plan created by integration test.");
    plan.put("monthlyPrice", 49);
    plan.put("annualPrice", 490);
    plan.put("currency", "USD");
    plan.put("trialDays", 14);
    plan.put("maxUsers", 15);
    plan.put("maxDepartments", 4);
    plan.put("maxRequestsMonth", 250);
    plan.put("maxStorageMb", 4096);
    plan.put("featureCodes", List.of("requests", "approvals"));
    plan.put("active", true);
    plan.put("sortOrder", 99);
    plan.put("stripePriceMonthly", "price_test_monthly");
    plan.put("stripePriceAnnual", "price_test_annual");
    mockMvc
        .perform(
            post("/api/v1/platform/subscription-plans")
                .with(token(orgA, "PLATFORM_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(plan)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(code))
        .andExpect(jsonPath("$.active").value(true))
        .andExpect(jsonPath("$.features[0].code").value("requests"))
        .andExpect(jsonPath("$.features[1].code").value("approvals"));

    mockMvc
        .perform(get("/api/v1/platform/subscription-plans").with(token(orgA, "PLATFORM_ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.code == '%s')].name".formatted(code)).value("Test Plan"));

    plan.put("name", "Test Plan Updated");
    plan.put("monthlyPrice", 59);
    plan.put("active", false);
    mockMvc
        .perform(
            put("/api/v1/platform/subscription-plans/{code}", code)
                .with(token(orgA, "PLATFORM_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(plan)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Test Plan Updated"))
        .andExpect(jsonPath("$.active").value(false));

    mockMvc
        .perform(
            delete("/api/v1/platform/subscription-plans/{code}", code)
                .with(token(orgA, "PLATFORM_ADMIN")))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            post("/api/v1/platform/subscription-plans")
                .with(token(orgA, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(plan)))
        .andExpect(status().isForbidden());
  }

  @Test
  void platformSubscriptionFeatureCatalogueCrudAndPlanSelection() throws Exception {
    String code = "test_" + UUID.randomUUID().toString().substring(0, 8).toLowerCase();
    Map<String, Object> feature = new HashMap<>();
    feature.put("code", code);
    feature.put("name", "Test feature");
    feature.put("description", "Feature created by integration test.");
    feature.put("category", "CORE");
    feature.put("active", true);
    feature.put("sortOrder", 500);

    mockMvc
        .perform(
            post("/api/v1/platform/subscription-features")
                .with(token(orgA, "PLATFORM_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(feature)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(code))
        .andExpect(jsonPath("$.category").value("CORE"));

    mockMvc
        .perform(get("/api/v1/platform/subscription-features").with(token(orgA, "PLATFORM_ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.code == 'requests')].name").value("Purchase requests"));

    feature.put("name", "Test feature updated");
    feature.put("active", false);
    mockMvc
        .perform(
            put("/api/v1/platform/subscription-features/{code}", code)
                .with(token(orgA, "PLATFORM_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(feature)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Test feature updated"))
        .andExpect(jsonPath("$.active").value(false));

    mockMvc
        .perform(
            delete("/api/v1/platform/subscription-features/{code}", code)
                .with(token(orgA, "PLATFORM_ADMIN")))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            post("/api/v1/platform/subscription-features")
                .with(token(orgA, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(feature)))
        .andExpect(status().isForbidden());
  }

  @Test
  void subscriptionFeaturesEnforceModuleAccess() throws Exception {
    String starterSlug = "starter-" + UUID.randomUUID().toString().substring(0, 8);
    UUID starterId = createPlatformTenant(starterSlug, "STARTER");

    mockMvc
        .perform(get("/api/v1/me/features").with(token(starterId, "ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.features.length()").value(5))
        .andExpect(jsonPath("$.features[0]").value("requests"));

    mockMvc
        .perform(get("/api/v1/vendors").with(token(starterId, "ADMIN")))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/v1/reports/spend").with(token(starterId, "ADMIN")))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/v1/vendor-invoices").with(token(starterId, "ADMIN")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("feature-not-enabled"));

    mockMvc
        .perform(get("/api/v1/webhooks").with(token(starterId, "ADMIN")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("feature-not-enabled"));

    mockMvc
        .perform(
            get("/api/v1/purchase-orders/{id}/receipts", UUID.randomUUID())
                .with(token(starterId, "ADMIN")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("feature-not-enabled"));

    mockMvc
        .perform(get("/api/v1/reports/spend.csv").with(token(starterId, "ADMIN")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("feature-not-enabled"));

    String growthSlug = "growth-" + UUID.randomUUID().toString().substring(0, 8);
    UUID growthId = createPlatformTenant(growthSlug, "GROWTH");

    mockMvc
        .perform(get("/api/v1/me/features").with(token(growthId, "ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.features.length()").value(9));

    mockMvc
        .perform(get("/api/v1/vendor-invoices").with(token(growthId, "ADMIN")))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/v1/webhooks").with(token(growthId, "ADMIN")))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/v1/reports/spend.csv").with(token(growthId, "ADMIN")))
        .andExpect(status().isOk());
  }

  @Test
  void subscriptionLifecycleNotifiesTrialsAndExpiresThem() throws Exception {
    UUID tenantId =
        createPlatformTenant(
            "lifecycle-" + UUID.randomUUID().toString().substring(0, 8), "STARTER");
    jdbcTemplate.update(
        "insert into tenant_provisioning (organization_id, admin_email, admin_name, status, created_at, updated_at) values (?, 'billing@example.com', 'Billing Admin', 'ACTIVE', now(), now())",
        tenantId);
    jdbcTemplate.update(
        "update tenant_subscription set trial_ends_at = now() + interval '2 days', trial_reminder_7d_sent_at = null, trial_reminder_1d_sent_at = null where organization_id = ?",
        tenantId);

    subscriptionLifecycle.runLifecycleNotifications();

    assertEquals(
        1,
        jdbcTemplate.queryForObject(
            "select count(*) from outbox_event where organization_id = ? and event_type = 'SubscriptionTrialEnding'",
            Integer.class,
            tenantId));
    assertEquals(
        1,
        jdbcTemplate.queryForObject(
            "select count(*) from tenant_subscription where organization_id = ? and trial_reminder_7d_sent_at is not null",
            Integer.class,
            tenantId));

    jdbcTemplate.update(
        "update tenant_subscription set status = 'TRIALING', trial_ends_at = now() - interval '1 hour', trial_expired_notified_at = null where organization_id = ?",
        tenantId);
    subscriptionLifecycle.runLifecycleNotifications();

    assertEquals(
        "EXPIRED",
        jdbcTemplate.queryForObject(
            "select status from tenant_subscription where organization_id = ?",
            String.class,
            tenantId));
    assertEquals(
        1,
        jdbcTemplate.queryForObject(
            "select count(*) from outbox_event where organization_id = ? and event_type = 'SubscriptionTrialExpired'",
            Integer.class,
            tenantId));
  }

  @Test
  void tenantBrandingIsEnterpriseGatedAndTenantScoped() throws Exception {
    UUID enterpriseId =
        createPlatformTenant("brand-" + UUID.randomUUID().toString().substring(0, 8), "ENTERPRISE");
    UUID starterId =
        createPlatformTenant("nobrand-" + UUID.randomUUID().toString().substring(0, 8), "STARTER");

    mockMvc
        .perform(get("/api/v1/branding").with(token(enterpriseId, "ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.primaryColor").value("#0B5CAD"))
        .andExpect(jsonPath("$.hasLogo").value(false));

    mockMvc
        .perform(
            put("/api/v1/branding")
                .with(token(enterpriseId, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "displayName", "Acme Workspace",
                            "primaryColor", "#123456",
                            "secondaryColor", "#654321",
                            "emailFromName", "Acme Procurement",
                            "supportEmail", "support@acme.test",
                            "requestLabel", "Requisitions",
                            "orderLabel", "Orders",
                            "vendorLabel", "Suppliers",
                            "budgetLabel", "Allocations",
                            "catalogLabel", "Items"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.displayName").value("Acme Workspace"))
        .andExpect(jsonPath("$.primaryColor").value("#123456"))
        .andExpect(jsonPath("$.requestLabel").value("Requisitions"))
        .andExpect(jsonPath("$.vendorLabel").value("Suppliers"));
    assertEquals(
        1,
        jdbcTemplate.queryForObject(
            "select count(*) from audit_event where organization_id = ? and action = 'BRANDING_UPDATED'",
            Integer.class,
            enterpriseId));

    mockMvc
        .perform(
            put("/api/v1/branding")
                .with(token(starterId, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "displayName", "Starter Workspace",
                            "primaryColor", "#123456",
                            "secondaryColor", "#654321"))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("feature-not-enabled"));

    mockMvc
        .perform(
            put("/api/v1/branding")
                .with(token(enterpriseId, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "displayName", "Acme Workspace",
                            "primaryColor", "navy",
                            "secondaryColor", "#654321"))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void auditExportIsEnterpriseGatedAndReturnsCsv() throws Exception {
    UUID enterpriseId =
        createPlatformTenant(
            "audit-exp-" + UUID.randomUUID().toString().substring(0, 8), "ENTERPRISE");
    UUID starterId =
        createPlatformTenant(
            "audit-noexp-" + UUID.randomUUID().toString().substring(0, 8), "STARTER");

    MvcResult export =
        mockMvc
            .perform(get("/api/v1/audit-events/export").with(token(enterpriseId, "ADMIN")))
            .andExpect(request().asyncStarted())
            .andReturn();
    mockMvc
        .perform(asyncDispatch(export))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Disposition", containsString("audit-log.csv")))
        .andExpect(content().string(containsString("event_id,occurred_at,action")));

    mockMvc
        .perform(get("/api/v1/audit-events/export").with(token(starterId, "ADMIN")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("feature-not-enabled"));
  }

  @Test
  void workflowActionsAreUnderstandableAndEntitySpecific() throws Exception {
    List<Map<String, Object>> validRules =
        List.of(
            Map.of(
                "fromStatus", "SCHEDULED",
                "actionCode", "APPROVE",
                "requiredPermission", "payment:manage"),
            Map.of(
                "fromStatus", "SCHEDULED",
                "actionCode", "CANCEL",
                "requiredPermission", "payment:manage"),
            Map.of(
                "fromStatus", "APPROVED",
                "actionCode", "MARK_PAID",
                "requiredPermission", "payment:manage"),
            Map.of(
                "fromStatus", "APPROVED",
                "actionCode", "CANCEL",
                "requiredPermission", "payment:manage"));

    mockMvc
        .perform(
            put("/api/v1/admin/workflow-actions/vendor_payment")
                .with(token(orgA, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("rules", validRules))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(4));

    mockMvc
        .perform(
            put("/api/v1/admin/workflow-actions/vendor_payment")
                .with(token(orgA, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "rules",
                            List.of(
                                Map.of(
                                    "fromStatus", "SCHEDULED",
                                    "actionCode", "MARK_PAID",
                                    "requiredPermission", "payment:manage"),
                                Map.of(
                                    "fromStatus", "SCHEDULED",
                                    "actionCode", "MARK_PAID",
                                    "requiredPermission", "payment:manage"))))))
        .andExpect(status().isUnprocessableEntity());

    mockMvc
        .perform(
            put("/api/v1/admin/workflow-actions/vendor_payment")
                .with(token(orgA, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "rules",
                            List.of(
                                Map.of(
                                    "fromStatus", "NOT_A_STATUS",
                                    "actionCode", "APPROVE",
                                    "requiredPermission", "payment:manage"))))))
        .andExpect(status().isUnprocessableEntity());

    mockMvc
        .perform(
            put("/api/v1/admin/workflow-actions/vendor_payment")
                .with(token(orgA, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "rules",
                            List.of(
                                Map.of(
                                    "fromStatus", "SCHEDULED",
                                    "actionCode", "EDIT",
                                    "requiredPermission", "payment:manage"))))))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  void rolePermissionFiltersAndSinglePermissionToggle() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/admin/access/roles/AUDITOR/permissions")
                .param("assignment", "available")
                .param("module", "webhook")
                .with(token(orgA, "ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.permissions[0].code").value("webhook:manage"))
        .andExpect(jsonPath("$.permissions[0].assigned").value(false));

    mockMvc
        .perform(
            put("/api/v1/admin/access/roles/AUDITOR/permissions/webhook:manage")
                .with(token(orgA, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"assigned\":true}"))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            get("/api/v1/admin/access/roles/AUDITOR/permissions")
                .param("assignment", "assigned")
                .param("module", "webhook")
                .with(token(orgA, "ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.permissions[0].assigned").value(true));

    mockMvc
        .perform(
            put("/api/v1/admin/access/roles/AUDITOR/permissions/webhook:manage")
                .with(token(orgA, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"assigned\":false}"))
        .andExpect(status().isNoContent());
  }

  @Test
  void milestoneOneReferenceEndpoints() throws Exception {
    UUID departmentId = createDepartment(orgA, "Operations");
    createUser(
        orgA,
        Map.of(
            "keycloakSubject", "test-user",
            "email", "test@example.com",
            "displayName", "Test User",
            "departmentId", departmentId.toString()));

    mockMvc
        .perform(get("/api/v1/users/me").with(token(orgA, "ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.displayName").value("Test User"));

    mockMvc
        .perform(get("/api/v1/users/delegation-candidates").with(token(orgA, "ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));

    UUID vendor = createVendor(orgA, "Bulk Vendor", "BULK-1");
    UUID catalogItem =
        createCatalogItem(
            orgA,
            Map.of(
                "sku", "BULK-SKU",
                "name", "Bulk Item",
                "unit", "EA",
                "defaultPrice", Map.of("amount", "10.00", "currency", "TZS")));

    mockMvc
        .perform(
            post("/api/v1/vendors/bulk-delete")
                .with(token(orgA, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("ids", List.of(vendor.toString())))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.processed").value(1));

    mockMvc
        .perform(
            post("/api/v1/catalog-items/bulk-delete")
                .with(token(orgA, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("ids", List.of(catalogItem.toString())))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.processed").value(1));

    UUID departmentToDelete = createDepartment(orgA, "Temporary");
    mockMvc
        .perform(
            post("/api/v1/departments/bulk-delete")
                .with(token(orgA, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("ids", List.of(departmentToDelete.toString())))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.processed").value(1));

    jdbcTemplate.update(
        """
        insert into notification (id, organization_id, recipient_subject, type, title, body,
                                  event_id, created_at, created_by, updated_at, updated_by, version)
        values (gen_random_uuid(), ?, 'test-user', 'TEST', 'Test', 'Body',
                gen_random_uuid(), now(), 'system', now(), 'system', 0)
        """,
        orgA);

    mockMvc
        .perform(post("/api/v1/notifications/read-all").with(token(orgA, "ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.updated").value(1));

    mockMvc
        .perform(get("/api/v1/notifications/unread-count").with(token(orgA, "ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.unread").value(0));
  }

  private UUID createPlatformTenant(String slug, String planCode) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/platform/tenants")
                    .with(token(orgA, "PLATFORM_ADMIN"))
                    .header("Idempotency-Key", "feature-tenant-" + slug)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json(
                            Map.of(
                                "name", slug,
                                "slug", slug,
                                "invoiceTolerancePercent", 2,
                                "planCode", planCode))))
            .andExpect(status().isCreated())
            .andReturn();
    return idOf(result);
  }

  private UUID createOrganization(String slug, String name) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/organizations")
                    .with(token(BOOTSTRAP_ORG, "ADMIN"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(Map.of("name", name, "slug", slug))))
            .andExpect(status().isCreated())
            .andReturn();
    return idOf(result);
  }

  private UUID createDepartment(UUID organizationId, String name) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/departments")
                    .with(token(organizationId, "ADMIN"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(Map.of("name", name))))
            .andExpect(status().isCreated())
            .andReturn();
    return idOf(result);
  }

  private UUID createUser(UUID organizationId, Map<String, Object> body) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/users")
                    .with(token(organizationId, "ADMIN"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(body)))
            .andExpect(status().isCreated())
            .andReturn();
    return idOf(result);
  }

  private Map<String, Object> userBody(String subject, String email, UUID departmentId) {
    return Map.of(
        "keycloakSubject",
        subject,
        "email",
        email,
        "displayName",
        "User " + subject,
        "departmentId",
        departmentId.toString());
  }

  private void updateUserExpecting(
      UUID organizationId, UUID userId, UUID departmentId, UUID managerId, ResultMatcher matcher)
      throws Exception {
    mockMvc
        .perform(
            put("/api/v1/users/{id}", userId)
                .with(token(organizationId, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "displayName",
                            "Updated",
                            "departmentId",
                            departmentId.toString(),
                            "managerId",
                            managerId.toString(),
                            "active",
                            true))))
        .andExpect(matcher);
  }

  private UUID createVendor(UUID organizationId, String name, String taxId) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/vendors")
                    .with(token(organizationId, "ADMIN"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(Map.of("name", name, "taxId", taxId))))
            .andExpect(status().isCreated())
            .andReturn();
    return idOf(result);
  }

  private UUID createCatalogItem(UUID organizationId, Map<String, Object> body) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/catalog-items")
                    .with(token(organizationId, "ADMIN"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(body)))
            .andExpect(status().isCreated())
            .andReturn();
    return idOf(result);
  }

  private UUID idOf(MvcResult result) throws Exception {
    return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
  }

  private String json(Object value) throws Exception {
    return objectMapper.writeValueAsString(value);
  }
}
