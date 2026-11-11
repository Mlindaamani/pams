package com.company.procurement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
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
class PurchaseRequestApiIntegrationTest {

  private static final UUID BOOTSTRAP_ORG = UUID.fromString("99999999-9999-9999-9999-999999999999");
  private static final int YEAR = LocalDate.now(java.time.ZoneOffset.UTC).getYear();
  private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private jakarta.persistence.EntityManagerFactory entityManagerFactory;

  private UUID orgA;
  private UUID orgB;
  private UUID departmentA;

  @BeforeEach
  void setUp() throws Exception {
    orgA = createOrganization("pr-org-a");
    orgB = createOrganization("pr-org-b");
    departmentA = createDepartment(orgA, "Finance");
    createDefaultPolicy(orgA);
  }

  @Test
  void draftLifecycleEnforcesEtagAndIdempotency() throws Exception {
    UUID requestId =
        createRequest(orgA, departmentA, "Laptops", lines(2, "100.00", 1, "50.00"), false);

    mockMvc
        .perform(get("/api/v1/purchase-requests/{id}", requestId).with(token(orgA, "REQUESTER")))
        .andExpect(status().isOk())
        .andExpect(header().string("ETag", "\"0\""))
        .andExpect(jsonPath("$.requestNumber").value(matchesPattern("PR-\\d{4}-\\d{6}")))
        .andExpect(jsonPath("$.total.amount").value("250.0000"))
        .andExpect(jsonPath("$.status").value("DRAFT"))
        .andExpect(jsonPath("$.lines.length()").value(2));

    mockMvc
        .perform(
            put("/api/v1/purchase-requests/{id}", requestId)
                .with(token(orgA, "REQUESTER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody(departmentA, "Laptops updated", lines(1, "300.00"), false)))
        .andExpect(status().isPreconditionRequired())
        .andExpect(jsonPath("$.code").value("precondition-required"));

    mockMvc
        .perform(
            put("/api/v1/purchase-requests/{id}", requestId)
                .with(token(orgA, "REQUESTER"))
                .header("If-Match", "\"99\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody(departmentA, "Laptops updated", lines(1, "300.00"), false)))
        .andExpect(status().isPreconditionFailed())
        .andExpect(jsonPath("$.code").value("precondition-failed"));

    mockMvc
        .perform(
            put("/api/v1/purchase-requests/{id}", requestId)
                .with(token(orgA, "REQUESTER"))
                .header("If-Match", "\"0\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody(departmentA, "Laptops updated", lines(1, "300.00"), false)))
        .andExpect(status().isOk())
        .andExpect(header().string("ETag", "\"1\""))
        .andExpect(jsonPath("$.total.amount").value("300.0000"));

    mockMvc
        .perform(
            post("/api/v1/purchase-requests")
                .with(token(orgA, "REQUESTER"))
                .header(IDEMPOTENCY_HEADER, "create-key-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody(departmentA, "First", lines(1, "10.00"), false)))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/v1/purchase-requests")
                .with(token(orgA, "REQUESTER"))
                .header(IDEMPOTENCY_HEADER, "create-key-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody(departmentA, "Different payload", lines(1, "20.00"), false)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("idempotency-key-reuse"));

    mockMvc
        .perform(
            delete("/api/v1/purchase-requests/{id}", requestId)
                .with(token(orgA, "REQUESTER"))
                .header("If-Match", "\"1\""))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/v1/purchase-requests/{id}", requestId).with(token(orgA, "REQUESTER")))
        .andExpect(status().isNotFound());
  }

  @Test
  void referenceNumbersAreSearchableAndStable() throws Exception {
    UUID requestId =
        createRequest(orgA, departmentA, "Referenced request", lines(1, "25.00"), false);
    MvcResult detail =
        mockMvc
            .perform(
                get("/api/v1/purchase-requests/{id}", requestId).with(token(orgA, "REQUESTER")))
            .andExpect(status().isOk())
            .andReturn();
    String requestNumber =
        JsonPath.read(detail.getResponse().getContentAsString(), "$.requestNumber");

    mockMvc
        .perform(
            get("/api/v1/purchase-requests")
                .param("q", requestNumber)
                .with(token(orgA, "REQUESTER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].id").value(requestId.toString()))
        .andExpect(jsonPath("$.content[0].requestNumber").value(requestNumber));

    UUID budgetId = createBudget(orgA, departmentA, "1000.00");
    mockMvc
        .perform(get("/api/v1/budgets/{id}", budgetId).with(token(orgA, "FINANCE")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.budgetNumber").value(matchesPattern("BUD-\\d{4}-\\d{6}")));
  }

  @Test
  void catalogLinesUseSnapshotsAndCustomLinesNeedReview() throws Exception {
    UUID catalogItemId = createCatalogItem("CAT-PAPER", "Printer paper", "Pack of 10", "100.00");
    List<Map<String, Object>> requestLines =
        List.of(
            Map.of(
                "description",
                "Tampered description",
                "quantity",
                2,
                "unit",
                "BOX",
                "unitPrice",
                new BigDecimal("1.00"),
                "catalogItemId",
                catalogItemId.toString(),
                "notes",
                "Urgent delivery"),
            Map.of(
                "description", "Custom toner",
                "quantity", 1,
                "unit", "EA",
                "unitPrice", new BigDecimal("50.00"),
                "notes", "Not in catalog"));

    MvcResult created =
        mockMvc
            .perform(
                post("/api/v1/purchase-requests")
                    .with(token(orgA, "REQUESTER"))
                    .header(IDEMPOTENCY_HEADER, "catalog-snapshot-" + UUID.randomUUID())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody(departmentA, "Catalog snapshot", requestLines, false)))
            .andExpect(status().isCreated())
            .andReturn();
    UUID requestId =
        UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.id"));

    mockMvc
        .perform(get("/api/v1/purchase-requests/{id}", requestId).with(token(orgA, "REQUESTER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lines[0].description").value("Printer paper"))
        .andExpect(jsonPath("$.lines[0].unit").value("Pack of 10"))
        .andExpect(jsonPath("$.lines[0].unitPrice.amount").value("100.0000"))
        .andExpect(jsonPath("$.lines[0].nonCatalog").value(false))
        .andExpect(jsonPath("$.lines[0].notes").value("Urgent delivery"))
        .andExpect(jsonPath("$.lines[1].nonCatalog").value(true))
        .andExpect(jsonPath("$.lines[1].catalogReviewStatus").value("PENDING"));
  }

  @Test
  void nonCatalogItemsAppearInAdminReviewQueue() throws Exception {
    createRequest(
        orgA,
        departmentA,
        "Custom item request",
        List.of(
            Map.of(
                "description", "Custom toner",
                "quantity", 1,
                "unit", "EA",
                "unitPrice", new BigDecimal("50.00"),
                "notes", "Not in catalog")),
        false);

    MvcResult queue =
        mockMvc
            .perform(get("/api/v1/catalog-items/non-catalog-review").with(token(orgA, "ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].description").value("Custom toner"))
            .andReturn();
    UUID lineId =
        UUID.fromString(JsonPath.read(queue.getResponse().getContentAsString(), "$[0].id"));

    mockMvc
        .perform(
            put("/api/v1/catalog-items/non-catalog-review/{id}", lineId)
                .with(token(orgA, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("status", "DISMISSED"))))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/v1/catalog-items/non-catalog-review").with(token(orgA, "ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void submitReservesBudgetAndCancelReleasesIt() throws Exception {
    UUID budgetId = createBudget(orgA, departmentA, "1000.00");
    UUID requestId = createRequest(orgA, departmentA, "Laptops", lines(2, "125.00"), false);

    String submitBody = "{}";
    mockMvc
        .perform(
            post("/api/v1/purchase-requests/{id}/submit", requestId)
                .with(token(orgA, "REQUESTER"))
                .header("If-Match", "\"0\"")
                .header(IDEMPOTENCY_HEADER, "submit-key-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(submitBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("IN_APPROVAL"))
        .andExpect(jsonPath("$.budgetReserved").value(true));

    mockMvc
        .perform(get("/api/v1/budgets/{id}", budgetId).with(token(orgA, "FINANCE")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reserved.amount").value("250.0000"));

    mockMvc
        .perform(
            post("/api/v1/purchase-requests/{id}/submit", requestId)
                .with(token(orgA, "REQUESTER"))
                .header("If-Match", "\"0\"")
                .header(IDEMPOTENCY_HEADER, "submit-key-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(submitBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("IN_APPROVAL"));

    mockMvc
        .perform(get("/api/v1/budgets/{id}", budgetId).with(token(orgA, "FINANCE")))
        .andExpect(jsonPath("$.reserved.amount").value("250.0000"));

    MvcResult timeline =
        mockMvc
            .perform(
                get("/api/v1/purchase-requests/{id}/approval", requestId)
                    .with(token(orgA, "REQUESTER")))
            .andExpect(status().isOk())
            .andReturn();
    UUID stepId =
        UUID.fromString(
            JsonPath.read(timeline.getResponse().getContentAsString(), "$.steps[0].id"));

    mockMvc
        .perform(
            post("/api/v1/approvals/{stepId}/request-changes", stepId)
                .with(tokenSubject("approver-user", orgA, "APPROVER"))
                .header(IDEMPOTENCY_HEADER, "changes-key-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("comment", "Please add quotes"))))
        .andExpect(status().isOk());

    MvcResult refreshed =
        mockMvc
            .perform(
                get("/api/v1/purchase-requests/{id}", requestId).with(token(orgA, "REQUESTER")))
            .andExpect(jsonPath("$.status").value("CHANGES_REQUESTED"))
            .andReturn();
    String changesEtag = refreshed.getResponse().getHeader("ETag");

    mockMvc
        .perform(
            post("/api/v1/purchase-requests/{id}/cancel", requestId)
                .with(token(orgA, "REQUESTER"))
                .header("If-Match", changesEtag)
                .header(IDEMPOTENCY_HEADER, "cancel-key-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"));

    mockMvc
        .perform(get("/api/v1/budgets/{id}", budgetId).with(token(orgA, "FINANCE")))
        .andExpect(jsonPath("$.reserved.amount").value("0.0000"));
  }

  @Test
  void budgetExceededCanBeOverriddenOnlyWithExceptionFlag() throws Exception {
    UUID budgetId = createBudget(orgA, departmentA, "100.00");

    UUID regular = createRequest(orgA, departmentA, "Too expensive", lines(1, "200.00"), false);
    mockMvc
        .perform(
            post("/api/v1/purchase-requests/{id}/submit", regular)
                .with(token(orgA, "REQUESTER"))
                .header("If-Match", "\"0\"")
                .header(IDEMPOTENCY_HEADER, "exceeded-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("budget-exceeded"));

    UUID exceptionRequest = createRequest(orgA, departmentA, "Exception", lines(1, "200.00"), true);
    mockMvc
        .perform(
            post("/api/v1/purchase-requests/{id}/submit", exceptionRequest)
                .with(token(orgA, "REQUESTER"))
                .header("If-Match", "\"0\"")
                .header(IDEMPOTENCY_HEADER, "exceeded-2")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("IN_APPROVAL"))
        .andExpect(jsonPath("$.budgetException").value(true))
        .andExpect(jsonPath("$.budgetReserved").value(false));

    mockMvc
        .perform(get("/api/v1/budgets/{id}", budgetId).with(token(orgA, "FINANCE")))
        .andExpect(jsonPath("$.reserved.amount").value("0.0000"));
  }

  @Test
  void searchIsFilteredByTenantAndOwnershipWithoutNPlusOne() throws Exception {
    for (int i = 0; i < 5; i++) {
      createRequest(orgA, departmentA, "Item " + i, lines(1, "10.00"), false);
    }

    mockMvc
        .perform(
            get("/api/v1/purchase-requests")
                .with(token(orgA, "REQUESTER"))
                .param("status", "DRAFT")
                .param("departmentId", departmentA.toString())
                .param("sort", "createdAt,desc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(5))
        .andExpect(jsonPath("$.content[0].lines").doesNotExist());

    Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    statistics.clear();
    mockMvc
        .perform(get("/api/v1/purchase-requests").with(token(orgA, "REQUESTER")))
        .andExpect(status().isOk());
    assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(3);

    mockMvc
        .perform(get("/api/v1/purchase-requests").with(token(orgB, "REQUESTER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));

    UUID requestId =
        UUID.fromString(
            JsonPath.read(
                mockMvc
                    .perform(get("/api/v1/purchase-requests").with(token(orgA, "REQUESTER")))
                    .andReturn()
                    .getResponse()
                    .getContentAsString(),
                "$.content[0].id"));

    mockMvc
        .perform(
            get("/api/v1/purchase-requests/{id}", requestId)
                .with(tokenSubject("someone-else", orgA, "REQUESTER")))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(get("/api/v1/purchase-requests/{id}", requestId).with(token(orgA, "ADMIN")))
        .andExpect(status().isOk());
  }

  @Test
  void attachmentsAreValidatedStoredAndDownloaded() throws Exception {
    UUID requestId = createRequest(orgA, departmentA, "With attachment", lines(1, "10.00"), false);
    byte[] pdf =
        ("%PDF-1.4\n1 0 obj<</Type/Catalog>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF")
            .getBytes(StandardCharsets.UTF_8);

    mockMvc
        .perform(
            multipart("/api/v1/purchase-requests/{id}/attachments", requestId)
                .file(new MockMultipartFile("file", "quote.pdf", "application/pdf", pdf))
                .with(token(orgA, "REQUESTER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.contentType").value("application/pdf"));

    mockMvc
        .perform(
            get("/api/v1/purchase-requests/{id}/attachments", requestId)
                .with(token(orgA, "REQUESTER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));

    MvcResult list =
        mockMvc
            .perform(
                get("/api/v1/purchase-requests/{id}/attachments", requestId)
                    .with(token(orgA, "REQUESTER")))
            .andReturn();
    UUID attachmentId =
        UUID.fromString(JsonPath.read(list.getResponse().getContentAsString(), "$[0].id"));

    MvcResult download =
        mockMvc
            .perform(
                get(
                        "/api/v1/purchase-requests/{id}/attachments/{attachmentId}",
                        requestId,
                        attachmentId)
                    .with(token(orgA, "REQUESTER")))
            .andExpect(status().isOk())
            .andReturn();
    assertThat(download.getResponse().getContentAsByteArray()).isEqualTo(pdf);

    mockMvc
        .perform(
            multipart("/api/v1/purchase-requests/{id}/attachments", requestId)
                .file(
                    new MockMultipartFile(
                        "file",
                        "notes.txt",
                        "text/plain",
                        "hello".getBytes(StandardCharsets.UTF_8)))
                .with(token(orgA, "REQUESTER")))
        .andExpect(status().isUnprocessableEntity());

    mockMvc
        .perform(
            delete(
                    "/api/v1/purchase-requests/{id}/attachments/{attachmentId}",
                    requestId,
                    attachmentId)
                .with(token(orgA, "REQUESTER")))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            get("/api/v1/purchase-requests/{id}/attachments", requestId)
                .with(token(orgA, "REQUESTER")))
        .andExpect(jsonPath("$.length()").value(0));
  }

  private List<Map<String, Object>> lines(Object... args) {
    List<Map<String, Object>> lines = new java.util.ArrayList<>();
    for (int i = 0; i < args.length; i += 2) {
      lines.add(
          Map.of(
              "description",
              "Line " + i,
              "quantity",
              ((Number) args[i]).doubleValue(),
              "unit",
              "EA",
              "unitPrice",
              new BigDecimal(args[i + 1].toString())));
    }
    return lines;
  }

  private String requestBody(
      UUID departmentId,
      String title,
      List<Map<String, Object>> lines,
      boolean budgetExceptionRequested)
      throws Exception {
    return json(
        Map.of(
            "title",
            title,
            "justification",
            "Justification",
            "departmentId",
            departmentId.toString(),
            "neededBy",
            LocalDate.now().plusDays(14).toString(),
            "currency",
            "TZS",
            "budgetExceptionRequested",
            budgetExceptionRequested,
            "lines",
            lines));
  }

  private UUID createRequest(
      UUID organizationId,
      UUID departmentId,
      String title,
      List<Map<String, Object>> lines,
      boolean budgetExceptionRequested)
      throws Exception {
    String key = "create-" + UUID.randomUUID();
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/purchase-requests")
                    .with(token(organizationId, "REQUESTER"))
                    .header(IDEMPOTENCY_HEADER, key)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody(departmentId, title, lines, budgetExceptionRequested)))
            .andExpect(status().isCreated())
            .andReturn();
    return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
  }

  private void createDefaultPolicy(UUID organizationId) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/approval-policies")
                .with(token(organizationId, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "name",
                            "Default approvals",
                            "amountMin",
                            0,
                            "steps",
                            List.of(
                                Map.of(
                                    "name", "Approver",
                                    "approverType", "ROLE",
                                    "approverRole", "APPROVER",
                                    "slaHours", 24))))))
        .andExpect(status().isCreated());
  }

  private UUID createCatalogItem(String sku, String name, String unit, String amount)
      throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/catalog-items")
                    .with(token(orgA, "ADMIN"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json(
                            Map.of(
                                "sku", sku,
                                "name", name,
                                "unit", unit,
                                "defaultPrice", Map.of("amount", amount, "currency", "TZS")))))
            .andExpect(status().isCreated())
            .andReturn();
    return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
  }

  private UUID createBudget(UUID organizationId, UUID departmentId, String amount)
      throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/budgets")
                    .with(token(organizationId, "FINANCE"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json(
                            Map.of(
                                "departmentId", departmentId.toString(),
                                "fiscalYear", YEAR,
                                "allocated", Map.of("amount", amount, "currency", "TZS")))))
            .andExpect(status().isCreated())
            .andReturn();
    return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
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
