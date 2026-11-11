package com.company.procurement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
      "DELETE FROM outbox_event",
      "DELETE FROM notification",
      "DELETE FROM goods_receipt_line",
      "DELETE FROM goods_receipt",
      "DELETE FROM purchase_order_line",
      "DELETE FROM purchase_order",
      "DELETE FROM purchase_order_sequence",
      "DELETE FROM audit_event",
      "DELETE FROM approval_step",
      "DELETE FROM approval_instance",
      "DELETE FROM delegation",
      "DELETE FROM idempotency_record",
      "DELETE FROM request_attachment",
      "DELETE FROM purchase_request_line",
      "DELETE FROM purchase_request",
      "DELETE FROM approval_policy_step",
      "DELETE FROM approval_policy",
      "DELETE FROM budget",
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
class ReportingAndAuditIntegrationTest {

  private static final UUID BOOTSTRAP_ORG = UUID.fromString("99999999-9999-9999-9999-999999999999");
  private static final int YEAR = LocalDate.now(ZoneOffset.UTC).getYear();
  private static final String IDEMPOTENCY = "Idempotency-Key";

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  private UUID org;
  private UUID department;
  private UUID vendor;
  private UUID catalogItem;

  @BeforeEach
  void setUp() throws Exception {
    org = createOrganization("report-org");
    department = createDepartment(org);
    createBudget();
    vendor = createVendor();
    catalogItem = createCatalogItem();
    createPolicy();
  }

  @Test
  void spendReportsGroupAndCsvAreCorrectAfterCacheEviction() throws Exception {
    UUID requestId = createApprovedRequest("500.00");

    mockMvc
        .perform(get("/api/v1/reports/spend").with(token(org, "FINANCE")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rows.length()").value(0));

    createAndIssuePurchaseOrder(requestId);

    for (String groupBy : List.of("department", "vendor", "category", "month")) {
      mockMvc
          .perform(
              get("/api/v1/reports/spend").with(token(org, "FINANCE")).param("groupBy", groupBy))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.rows.length()").value(1))
          .andExpect(jsonPath("$.rows[0].total").value(500.0000));
    }

    MvcResult csv =
        mockMvc
            .perform(
                get("/api/v1/reports/spend.csv")
                    .with(token(org, "FINANCE"))
                    .param("groupBy", "vendor"))
            .andExpect(status().isOk())
            .andReturn();
    assertThat(csv.getResponse().getContentAsString())
        .startsWith("key,label,currency,total,order_count")
        .contains("Report Vendor", "500.0000");
  }

  @Test
  void commitmentsAndCycleTimeReportsAreProjectionBased() throws Exception {
    createAndIssuePurchaseOrder(createApprovedRequest("500.00"));

    mockMvc
        .perform(get("/api/v1/reports/commitments").with(token(org, "FINANCE")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rows.length()").value(1))
        .andExpect(jsonPath("$.rows[0].committed.amount").value("500.0000"))
        .andExpect(jsonPath("$.rows[0].reserved.amount").value("0.0000"))
        .andExpect(jsonPath("$.rows[0].remaining.amount").value("99500.0000"));

    mockMvc
        .perform(
            get("/api/v1/reports/approval-cycle-time")
                .with(token(org, "FINANCE"))
                .param("groupBy", "policy"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rows[0].count").value(1));

    mockMvc
        .perform(
            get("/api/v1/reports/approval-cycle-time")
                .with(token(org, "FINANCE"))
                .param("groupBy", "approver"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rows[0].count").value(1));
  }

  @Test
  void auditLogUsesKeysetPaginationAndIsRestricted() throws Exception {
    createAndIssuePurchaseOrder(createApprovedRequest("100.00"));

    mockMvc
        .perform(get("/api/v1/audit-events").with(token(org, "REQUESTER")))
        .andExpect(status().isForbidden());

    MvcResult first =
        mockMvc
            .perform(get("/api/v1/audit-events").with(token(org, "AUDITOR")).param("limit", "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(2))
            .andReturn();
    String body = first.getResponse().getContentAsString();
    String cursor = JsonPath.read(body, "$.nextCursor");
    List<String> firstIds = JsonPath.read(body, "$.content[*].id");

    MvcResult second =
        mockMvc
            .perform(
                get("/api/v1/audit-events")
                    .with(token(org, "AUDITOR"))
                    .param("limit", "2")
                    .param("cursor", cursor))
            .andExpect(status().isOk())
            .andReturn();
    List<String> secondIds =
        JsonPath.read(second.getResponse().getContentAsString(), "$.content[*].id");
    assertThat(secondIds).isNotEmpty().doesNotContainAnyElementsOf(firstIds);

    mockMvc
        .perform(
            get("/api/v1/audit-events")
                .with(token(org, "AUDITOR"))
                .param("from", "2030-01-01T00:00:00Z"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(0));
  }

  @Test
  void reportRolesAreEnforced() throws Exception {
    mockMvc
        .perform(get("/api/v1/reports/spend").with(token(org, "REQUESTER")))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(get("/api/v1/audit-events").with(token(org, "PROCUREMENT_OFFICER")))
        .andExpect(status().isForbidden());
  }

  private void createAndIssuePurchaseOrder(UUID requestId) throws Exception {
    MvcResult created =
        mockMvc
            .perform(
                post("/api/v1/purchase-requests/{id}/purchase-orders", requestId)
                    .with(token(org, "PROCUREMENT_OFFICER"))
                    .header(IDEMPOTENCY, "po-" + UUID.randomUUID())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(Map.of("vendorId", vendor.toString(), "paymentTerms", "Net 30"))))
            .andExpect(status().isCreated())
            .andReturn();
    UUID poId = UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.id"));
    mockMvc
        .perform(
            post("/api/v1/purchase-orders/{id}/issue", poId)
                .with(token(org, "PROCUREMENT_OFFICER"))
                .header("If-Match", created.getResponse().getHeader("ETag"))
                .header(IDEMPOTENCY, "issue-" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk());
  }

  private UUID createApprovedRequest(String unitPrice) throws Exception {
    MvcResult created =
        mockMvc
            .perform(
                post("/api/v1/purchase-requests")
                    .with(tokenSubject("requesting-user", org, "REQUESTER"))
                    .header(IDEMPOTENCY, "req-" + UUID.randomUUID())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json(
                            Map.of(
                                "title",
                                "Report request",
                                "justification",
                                "Report test",
                                "departmentId",
                                department.toString(),
                                "neededBy",
                                LocalDate.now().plusDays(14).toString(),
                                "currency",
                                "TZS",
                                "lines",
                                List.of(
                                    Map.of(
                                        "description",
                                        "Laptop",
                                        "quantity",
                                        1,
                                        "unit",
                                        "EA",
                                        "unitPrice",
                                        new BigDecimal(unitPrice),
                                        "catalogItemId",
                                        catalogItem.toString()))))))
            .andExpect(status().isCreated())
            .andReturn();
    UUID requestId =
        UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.id"));
    mockMvc
        .perform(
            post("/api/v1/purchase-requests/{id}/submit", requestId)
                .with(tokenSubject("requesting-user", org, "REQUESTER"))
                .header("If-Match", "\"0\"")
                .header(IDEMPOTENCY, "sub-" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk());
    MvcResult timeline =
        mockMvc
            .perform(
                get("/api/v1/purchase-requests/{id}/approval", requestId).with(token(org, "ADMIN")))
            .andReturn();
    UUID stepId =
        UUID.fromString(
            JsonPath.read(timeline.getResponse().getContentAsString(), "$.steps[0].id"));
    mockMvc
        .perform(
            post("/api/v1/approvals/{stepId}/approve", stepId)
                .with(tokenSubject("approver-user", org, "APPROVER"))
                .header(IDEMPOTENCY, "appr-" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk());
    return requestId;
  }

  private UUID createCatalogItem() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/catalog-items")
                    .with(token(org, "ADMIN"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json(
                            Map.of(
                                "sku", "LAPTOP-R",
                                "name", "Laptop",
                                "unit", "EA",
                                "category", "IT",
                                "defaultPrice", Map.of("amount", "500.00", "currency", "TZS")))))
            .andExpect(status().isCreated())
            .andReturn();
    return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
  }

  private void createPolicy() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/approval-policies")
                .with(token(org, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "name",
                            "Report policy",
                            "amountMin",
                            0,
                            "steps",
                            List.of(
                                Map.of(
                                    "name",
                                    "Approver",
                                    "approverType",
                                    "ROLE",
                                    "approverRole",
                                    "APPROVER",
                                    "slaHours",
                                    24))))))
        .andExpect(status().isCreated());
  }

  private UUID createVendor() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/vendors")
                    .with(token(org, "ADMIN"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json(
                            Map.of(
                                "name", "Report Vendor",
                                "taxId", "VAT-R1",
                                "contactEmail", "vendor@report.test"))))
            .andExpect(status().isCreated())
            .andReturn();
    return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
  }

  private void createBudget() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/budgets")
                .with(token(org, "FINANCE"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "departmentId",
                            department.toString(),
                            "fiscalYear",
                            YEAR,
                            "allocated",
                            Map.of("amount", "100000.00", "currency", "TZS")))))
        .andExpect(status().isCreated());
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

  private UUID createDepartment(UUID organizationId) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/departments")
                    .with(token(organizationId, "ADMIN"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(Map.of("name", "Finance"))))
            .andExpect(status().isCreated())
            .andReturn();
    return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
  }

  private RequestPostProcessor token(UUID organizationId, String... roles) {
    return tokenSubject("test-user", organizationId, roles);
  }

  private RequestPostProcessor tokenSubject(String subject, UUID organizationId, String... roles) {
    return jwt()
        .jwt(
            jwt ->
                jwt.subject(subject)
                    .claim("organization_id", organizationId.toString())
                    .claim("realm_access", Map.of("roles", List.of(roles))))
        .authorities(
            Arrays.stream(roles)
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
                .toList());
  }

  private String json(Object value) throws Exception {
    return objectMapper.writeValueAsString(value);
  }
}
