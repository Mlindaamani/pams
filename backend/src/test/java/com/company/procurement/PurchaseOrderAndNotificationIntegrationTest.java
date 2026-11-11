package com.company.procurement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.procurement.shared.mail.InMemoryMailAdapter;
import com.company.procurement.shared.outbox.OutboxDispatcher;
import com.company.procurement.webhook.application.WebhookDeliveryDispatcher;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
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
      "DELETE FROM webhook_delivery",
      "DELETE FROM webhook_subscription",
      "DELETE FROM outbox_event",
      "DELETE FROM notification",
      "DELETE FROM vendor_payment_allocation",
      "DELETE FROM vendor_payment",
      "DELETE FROM invoice_sequence",
      "DELETE FROM vendor_invoice_line",
      "DELETE FROM vendor_invoice",
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
class PurchaseOrderAndNotificationIntegrationTest {

  private static final UUID BOOTSTRAP_ORG = UUID.fromString("99999999-9999-9999-9999-999999999999");
  private static final int YEAR = LocalDate.now(ZoneOffset.UTC).getYear();
  private static final String IDEMPOTENCY = "Idempotency-Key";

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private OutboxDispatcher outboxDispatcher;
  @Autowired private InMemoryMailAdapter mailAdapter;
  @Autowired private WebhookDeliveryDispatcher webhookDeliveryDispatcher;

  private UUID org;
  private UUID department;
  private UUID vendor;

  @BeforeEach
  void setUp() throws Exception {
    org = createOrganization("po-org");
    department = createDepartment(org, "Finance");
    createBudget();
    vendor = createVendor("Vendor One", "VAT-1", "vendor@example.com");
    createPolicy();
    mailAdapter.clear();
  }

  @Test
  void numbersPurchaseOrdersSequentially() throws Exception {
    UUID requestOne = createApprovedRequest("First", "100.00");
    UUID requestTwo = createApprovedRequest("Second", "200.00");

    MvcResult first = createPurchaseOrder(requestOne);
    MvcResult second = createPurchaseOrder(requestTwo);

    assertThat((String) JsonPath.read(first.getResponse().getContentAsString(), "$.poNumber"))
        .isEqualTo("PO-" + YEAR + "-000001");
    assertThat((String) JsonPath.read(second.getResponse().getContentAsString(), "$.poNumber"))
        .isEqualTo("PO-" + YEAR + "-000002");

    mockMvc
        .perform(
            get("/api/v1/purchase-requests/{id}", requestOne)
                .with(tokenSubject("requesting-user", org, "REQUESTER")))
        .andExpect(jsonPath("$.status").value("CONVERTED_TO_PO"));
  }

  @Test
  void updatesDraftPurchaseOrderTerms() throws Exception {
    UUID requestId = createApprovedRequest("Edit PO", "100.00");
    MvcResult created = createPurchaseOrder(requestId);
    UUID poId = UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.id"));

    mockMvc
        .perform(
            put("/api/v1/purchase-orders/{id}", poId)
                .with(tokenSubject("officer-user", org, "PROCUREMENT_OFFICER"))
                .header("If-Match", created.getResponse().getHeader("ETag"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "paymentTerms",
                            "Net 45",
                            "deliveryDate",
                            LocalDate.now().plusDays(21).toString()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paymentTerms").value("Net 45"));
  }

  @Test
  void issueConvertsBudgetWritesOutboxAndGeneratesPdf() throws Exception {
    UUID requestId = createApprovedRequest("Issue me", "400.00");
    MvcResult created = createPurchaseOrder(requestId);
    UUID poId = UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.id"));
    String etag = created.getResponse().getHeader("ETag");

    mockMvc
        .perform(
            post("/api/v1/purchase-orders/{id}/issue", poId)
                .with(tokenSubject("officer-user", org, "PROCUREMENT_OFFICER"))
                .header("If-Match", etag)
                .header(IDEMPOTENCY, "issue-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ISSUED"));

    Map<String, Object> budget =
        jdbcTemplate.queryForMap(
            "select reserved_amount, committed_amount from budget where organization_id = ?::uuid",
            org.toString());
    assertThat((java.math.BigDecimal) budget.get("reserved_amount")).isEqualByComparingTo("0.0000");
    assertThat((java.math.BigDecimal) budget.get("committed_amount"))
        .isEqualByComparingTo("400.0000");
    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from outbox_event where aggregate_id = ?::uuid",
                Long.class,
                poId.toString()))
        .isEqualTo(1L);

    MvcResult pdf =
        mockMvc
            .perform(get("/api/v1/purchase-orders/{id}/pdf", poId).with(token(org, "ADMIN")))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "application/pdf"))
            .andReturn();
    assertThat(pdf.getResponse().getContentAsByteArray()).startsWith("%PDF".getBytes());
  }

  @Test
  void outboxCreatesNotificationAndEmailAndDoesNotDuplicate() throws Exception {
    UUID requestId = createApprovedRequest("Notify", "100.00");
    issue(createPurchaseOrder(requestId));

    assertThat(outboxDispatcher.dispatchBatch()).isEqualTo(1);
    assertThat(mailAdapter.sentMessages()).hasSize(1);
    assertThat(mailAdapter.sentMessages().get(0).to()).isEqualTo("vendor@example.com");
    assertThat(mailAdapter.sentMessages().get(0).html())
        .contains("po-org", "Purchase order issued");

    mockMvc
        .perform(
            get("/api/v1/notifications").with(tokenSubject("requesting-user", org, "REQUESTER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].type").value("PURCHASE_ORDER_ISSUED"));

    mockMvc
        .perform(
            get("/api/v1/notifications/unread-count")
                .with(tokenSubject("requesting-user", org, "REQUESTER")))
        .andExpect(jsonPath("$.unread").value(1));

    assertThat(outboxDispatcher.dispatchBatch()).isZero();
    assertThat(mailAdapter.sentMessages()).hasSize(1);

    MvcResult notifications =
        mockMvc
            .perform(
                get("/api/v1/notifications")
                    .with(tokenSubject("requesting-user", org, "REQUESTER")))
            .andReturn();
    UUID notificationId =
        UUID.fromString(
            JsonPath.read(notifications.getResponse().getContentAsString(), "$.content[0].id"));
    mockMvc
        .perform(
            post("/api/v1/notifications/{id}/read", notificationId)
                .with(tokenSubject("requesting-user", org, "REQUESTER")))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            get("/api/v1/notifications/unread-count")
                .with(tokenSubject("requesting-user", org, "REQUESTER")))
        .andExpect(jsonPath("$.unread").value(0));
  }

  @Test
  void inactiveVendorCannotBeUsed() throws Exception {
    UUID suspended = createVendor("Suspended", "VAT-2", "s@example.com");
    mockMvc
        .perform(post("/api/v1/vendors/{id}/suspend", suspended).with(token(org, "ADMIN")))
        .andExpect(status().isOk());
    UUID requestId = createApprovedRequest("No vendor", "100.00");

    mockMvc
        .perform(
            post("/api/v1/purchase-requests/{id}/purchase-orders", requestId)
                .with(tokenSubject("officer-user", org, "PROCUREMENT_OFFICER"))
                .header(IDEMPOTENCY, "po-suspended")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("vendorId", suspended.toString(), "paymentTerms", "Net 30"))))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  void purchaseOrderEndpointsEnforceRolesAndTenancy() throws Exception {
    UUID orgB = createOrganization("po-org-b");
    UUID requestId = createApprovedRequest("Secure", "100.00");

    mockMvc
        .perform(
            post("/api/v1/purchase-requests/{id}/purchase-orders", requestId)
                .with(tokenSubject("requesting-user", org, "REQUESTER"))
                .header(IDEMPOTENCY, "po-forbidden")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("vendorId", vendor.toString()))))
        .andExpect(status().isForbidden());

    UUID poId =
        UUID.fromString(
            JsonPath.read(
                createPurchaseOrder(requestId).getResponse().getContentAsString(), "$.id"));
    mockMvc
        .perform(
            get("/api/v1/purchase-orders/{id}", poId)
                .with(tokenSubject("officer-user", orgB, "PROCUREMENT_OFFICER")))
        .andExpect(status().isNotFound());
  }

  @Test
  void cancellingIssuedPurchaseOrderReleasesCommitment() throws Exception {
    UUID requestId = createApprovedRequest("Cancel me", "250.00");
    MvcResult created = createPurchaseOrder(requestId);
    UUID poId = UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.id"));
    MvcResult issued = issue(created);
    String issuedEtag = issued.getResponse().getHeader("ETag");

    mockMvc
        .perform(
            post("/api/v1/purchase-orders/{id}/cancel", poId)
                .with(tokenSubject("officer-user", org, "PROCUREMENT_OFFICER"))
                .header("If-Match", issuedEtag)
                .header(IDEMPOTENCY, "cancel-po")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"));

    Map<String, Object> budget =
        jdbcTemplate.queryForMap(
            "select reserved_amount, committed_amount from budget where organization_id = ?::uuid",
            org.toString());
    assertThat((java.math.BigDecimal) budget.get("committed_amount"))
        .isEqualByComparingTo("0.0000");
  }

  @Test
  void cancellingDraftPurchaseOrderReopensRequestAndKeepsBudgetReserved() throws Exception {
    UUID requestId = createApprovedRequest("Draft cancel", "120.00");
    MvcResult created = createPurchaseOrder(requestId);
    UUID poId = UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.id"));

    mockMvc
        .perform(
            post("/api/v1/purchase-orders/{id}/cancel", poId)
                .with(tokenSubject("officer-user", org, "PROCUREMENT_OFFICER"))
                .header("If-Match", created.getResponse().getHeader("ETag"))
                .header(IDEMPOTENCY, "cancel-draft-" + poId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"));

    mockMvc
        .perform(
            get("/api/v1/purchase-requests/{id}", requestId)
                .with(tokenSubject("requesting-user", org, "REQUESTER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"));

    Map<String, Object> budget =
        jdbcTemplate.queryForMap(
            "select reserved_amount, committed_amount from budget where organization_id = ?::uuid",
            org.toString());
    assertThat((java.math.BigDecimal) budget.get("reserved_amount"))
        .isEqualByComparingTo("120.0000");
    assertThat((java.math.BigDecimal) budget.get("committed_amount"))
        .isEqualByComparingTo("0.0000");

    MvcResult replacement = createPurchaseOrder(requestId);
    assertThat((String) JsonPath.read(replacement.getResponse().getContentAsString(), "$.id"))
        .isNotEqualTo(poId.toString());
  }

  @Test
  void purchaseOrderRequiresApprovalBeforeIssueWhenPolicyConfigured() throws Exception {
    createPurchaseOrderPolicy();
    UUID requestId = createApprovedRequest("Needs PO approval", "75.00");
    MvcResult created = createPurchaseOrder(requestId);
    UUID poId = UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.id"));

    mockMvc
        .perform(
            get("/api/v1/purchase-orders/{id}", poId)
                .with(tokenSubject("officer-user", org, "PROCUREMENT_OFFICER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));

    mockMvc
        .perform(
            post("/api/v1/purchase-orders/{id}/issue", poId)
                .with(tokenSubject("officer-user", org, "PROCUREMENT_OFFICER"))
                .header("If-Match", created.getResponse().getHeader("ETag"))
                .header(IDEMPOTENCY, "issue-before-approval-" + poId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isUnprocessableEntity());

    mockMvc
        .perform(
            post("/api/v1/purchase-orders/{id}/approval/approve", poId)
                .with(tokenSubject("approver-user", org, "APPROVER"))
                .header(IDEMPOTENCY, "approve-po-" + poId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"));

    MvcResult approved =
        mockMvc
            .perform(
                get("/api/v1/purchase-orders/{id}", poId)
                    .with(tokenSubject("officer-user", org, "PROCUREMENT_OFFICER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("APPROVED"))
            .andReturn();

    mockMvc
        .perform(
            post("/api/v1/purchase-orders/{id}/issue", poId)
                .with(tokenSubject("officer-user", org, "PROCUREMENT_OFFICER"))
                .header("If-Match", approved.getResponse().getHeader("ETag"))
                .header(IDEMPOTENCY, "issue-after-approval-" + poId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ISSUED"));
  }

  @Test
  void purchaseOrderParallelStageWaitsForEveryApprover() throws Exception {
    createParallelPurchaseOrderPolicy();
    UUID requestId = createApprovedRequest("Parallel PO approval", "75.00");
    MvcResult created = createPurchaseOrder(requestId);
    UUID poId = UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.id"));

    mockMvc
        .perform(
            post("/api/v1/purchase-orders/{id}/approval/approve", poId)
                .with(tokenSubject("approver-user", org, "APPROVER"))
                .header(IDEMPOTENCY, "parallel-po-approver")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PENDING"))
        .andExpect(jsonPath("$.currentSequence").value(1));

    mockMvc
        .perform(
            post("/api/v1/purchase-orders/{id}/approval/approve", poId)
                .with(tokenSubject("finance-user", org, "FINANCE"))
                .header(IDEMPOTENCY, "parallel-po-finance")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PENDING"))
        .andExpect(jsonPath("$.currentSequence").value(2));

    mockMvc
        .perform(
            post("/api/v1/purchase-orders/{id}/approval/approve", poId)
                .with(token(org, "ADMIN"))
                .header(IDEMPOTENCY, "parallel-po-admin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"));
  }

  @Test
  void purchaseOrderQuorumCompletesStageAfterConfiguredApprovals() throws Exception {
    createQuorumPurchaseOrderPolicy();
    UUID requestId = createApprovedRequest("Quorum PO approval", "75.00");
    MvcResult created = createPurchaseOrder(requestId);
    UUID poId = UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.id"));

    mockMvc
        .perform(
            post("/api/v1/purchase-orders/{id}/approval/approve", poId)
                .with(tokenSubject("approver-user", org, "APPROVER"))
                .header(IDEMPOTENCY, "quorum-po-approver")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PENDING"));

    mockMvc
        .perform(
            post("/api/v1/purchase-orders/{id}/approval/approve", poId)
                .with(tokenSubject("finance-user", org, "FINANCE"))
                .header(IDEMPOTENCY, "quorum-po-finance")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"));
  }

  @Test
  void purchaseOrderActionsComeFromWorkflowRules() throws Exception {
    UUID requestId = createApprovedRequest("Action rules", "50.00");
    MvcResult created = createPurchaseOrder(requestId);
    UUID poId = UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.id"));

    mockMvc
        .perform(get("/api/v1/purchase-orders/{id}/actions", poId).with(token(org, "ADMIN")))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.actions").value(hasItems("EDIT", "SUBMIT_APPROVAL", "ISSUE", "CANCEL")));
  }

  @Test
  void recordsPartialAndCompleteGoodsReceipts() throws Exception {
    UUID requestId = createApprovedRequest("Receive me", "40.00", 4);
    MvcResult created = createPurchaseOrder(requestId);
    UUID poId = UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.id"));
    UUID lineId =
        UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.lines[0].id"));
    MvcResult issued = issue(created);

    MvcResult partial =
        recordReceipt(
                poId, issued.getResponse().getHeader("ETag"), lineId, "2.0000", "partial-receipt")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.receiptNumber").value(matchesPattern("GRN-\\d{4}-\\d{6}")))
            .andExpect(jsonPath("$.deliveryNoteReference").value("DN-1"))
            .andExpect(jsonPath("$.recordedByName").isNotEmpty())
            .andExpect(jsonPath("$.lines[0].quantity").value("2.0000"))
            .andReturn();

    mockMvc
        .perform(get("/api/v1/purchase-orders/{id}", poId).with(token(org, "ADMIN")))
        .andExpect(jsonPath("$.status").value("PARTIALLY_RECEIVED"))
        .andExpect(jsonPath("$.lines[0].receivedQuantity").value("2.0000"))
        .andExpect(jsonPath("$.lines[0].remainingQuantity").value("2.0000"));

    UUID receiptId =
        UUID.fromString(JsonPath.read(partial.getResponse().getContentAsString(), "$.id"));
    mockMvc
        .perform(
            get("/api/v1/purchase-orders/{poId}/receipts/{receiptId}", poId, receiptId)
                .with(token(org, "ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.purchaseOrderId").value(poId.toString()));

    String etag =
        mockMvc
            .perform(get("/api/v1/purchase-orders/{id}", poId).with(token(org, "ADMIN")))
            .andReturn()
            .getResponse()
            .getHeader("ETag");
    recordReceipt(poId, etag, lineId, "2.0000", "complete-receipt").andExpect(status().isCreated());

    mockMvc
        .perform(get("/api/v1/purchase-orders/{id}", poId).with(token(org, "ADMIN")))
        .andExpect(jsonPath("$.status").value("RECEIVED"))
        .andExpect(jsonPath("$.lines[0].receivedQuantity").value("4.0000"))
        .andExpect(jsonPath("$.lines[0].remainingQuantity").value("0.0000"));

    mockMvc
        .perform(get("/api/v1/purchase-orders/{id}/receipts", poId).with(token(org, "ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2));
  }

  @Test
  void rejectsOverReceiptAndEnforcesReceivingAccess() throws Exception {
    UUID requestId = createApprovedRequest("Over receive", "10.00", 3);
    MvcResult created = createPurchaseOrder(requestId);
    UUID poId = UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.id"));
    UUID lineId =
        UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.lines[0].id"));
    MvcResult issued = issue(created);
    String etag = issued.getResponse().getHeader("ETag");

    recordReceipt(poId, etag, lineId, "4.0000", "too-much")
        .andExpect(status().isUnprocessableEntity());

    mockMvc
        .perform(
            post("/api/v1/purchase-orders/{id}/receipts", poId)
                .with(tokenSubject("requesting-user", org, "REQUESTER"))
                .header("If-Match", etag)
                .header(IDEMPOTENCY, "receipt-forbidden")
                .contentType(MediaType.APPLICATION_JSON)
                .content(receiptJson(lineId, "1.0000")))
        .andExpect(status().isForbidden());

    UUID orgB = createOrganization("receipt-org-b");
    mockMvc
        .perform(
            get("/api/v1/purchase-orders/{id}/receipts", poId)
                .with(tokenSubject("officer-b", orgB, "PROCUREMENT_OFFICER")))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            post("/api/v1/purchase-orders/{id}/receipts", poId)
                .with(tokenSubject("officer-b", orgB, "PROCUREMENT_OFFICER"))
                .header("If-Match", "\"0\"")
                .header(IDEMPOTENCY, "receipt-other-tenant")
                .contentType(MediaType.APPLICATION_JSON)
                .content(receiptJson(lineId, "1.0000")))
        .andExpect(status().isNotFound());
  }

  @Test
  void matchesInvoiceWithinTolerance() throws Exception {
    UUID requestId = createApprovedRequest("Invoice match", "100.00", 2);
    MvcResult created = createPurchaseOrder(requestId);
    UUID poId = UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.id"));
    UUID lineId =
        UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.lines[0].id"));
    MvcResult issued = issue(created);
    recordReceipt(poId, issued.getResponse().getHeader("ETag"), lineId, "2.0000", "invoice-receipt")
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/v1/purchase-orders/{id}/vendor-invoices", poId)
                .with(tokenSubject("finance-user", org, "FINANCE"))
                .header(IDEMPOTENCY, "invoice-matched")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    invoiceJson(
                        "INV-100", lineId, "2.0000", new java.math.BigDecimal("101.00"), "10.00")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("MATCHED"))
        .andExpect(jsonPath("$.poNumber").value("PO-" + YEAR + "-000001"))
        .andExpect(jsonPath("$.tolerancePercent").value(2.00))
        .andExpect(jsonPath("$.lines[0].matched").value(true))
        .andExpect(jsonPath("$.lines[0].priceVariancePercent").value(1.0));

    mockMvc
        .perform(
            get("/api/v1/vendor-invoices")
                .param("status", "MATCHED")
                .with(tokenSubject("finance-user", org, "FINANCE")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].invoiceNumber").value("INV-100"));
  }

  @Test
  void schedulesApprovesAndRecordsVendorPayment() throws Exception {
    UUID requestId = createApprovedRequest("Payment invoice", "80.00", 2);
    MvcResult created = createPurchaseOrder(requestId);
    UUID poId = UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.id"));
    UUID lineId =
        UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.lines[0].id"));
    MvcResult issued = issue(created);
    recordReceipt(poId, issued.getResponse().getHeader("ETag"), lineId, "2.0000", "payment-receipt")
        .andExpect(status().isCreated());

    MvcResult invoice =
        mockMvc
            .perform(
                post("/api/v1/purchase-orders/{id}/vendor-invoices", poId)
                    .with(tokenSubject("finance-user", org, "FINANCE"))
                    .header(IDEMPOTENCY, "invoice-payment")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        invoiceJson(
                            "INV-PAY",
                            lineId,
                            "2.0000",
                            new java.math.BigDecimal("80.00"),
                            "0.00")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("MATCHED"))
            .andReturn();
    UUID invoiceId =
        UUID.fromString(JsonPath.read(invoice.getResponse().getContentAsString(), "$.id"));

    mockMvc
        .perform(
            post("/api/v1/vendor-invoices/{id}/approve", invoiceId)
                .with(tokenSubject("finance-user", org, "FINANCE"))
                .header("If-Match", invoice.getResponse().getHeader("ETag"))
                .header(IDEMPOTENCY, "invoice-payment-approve")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"));

    mockMvc
        .perform(
            get("/api/v1/vendor-payments/aging").with(tokenSubject("finance-user", org, "FINANCE")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rows[0].vendorName").value("Vendor One"))
        .andExpect(jsonPath("$.rows[0].totalOutstanding").value(160.00));

    MvcResult payment =
        mockMvc
            .perform(
                post("/api/v1/vendor-payments")
                    .with(tokenSubject("finance-user", org, "FINANCE"))
                    .header(IDEMPOTENCY, "payment-create")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json(
                            Map.of(
                                "vendorId", vendor.toString(),
                                "paymentDate", LocalDate.now().toString(),
                                "method", "BANK_TRANSFER",
                                "reference", "PAY-001",
                                "allocations",
                                    List.of(
                                        Map.of(
                                            "invoiceId", invoiceId.toString(), "amount", 160))))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.paymentNumber").value(matchesPattern("PAY-\\d{4}-\\d{6}")))
            .andExpect(jsonPath("$.status").value("SCHEDULED"))
            .andExpect(jsonPath("$.totalAmount").value(160))
            .andReturn();
    UUID paymentId =
        UUID.fromString(JsonPath.read(payment.getResponse().getContentAsString(), "$.id"));

    mockMvc
        .perform(
            post("/api/v1/vendor-payments/{id}/approve", paymentId)
                .with(tokenSubject("finance-user", org, "FINANCE"))
                .header(IDEMPOTENCY, "payment-approve"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"));

    mockMvc
        .perform(
            post("/api/v1/vendor-payments/{id}/pay", paymentId)
                .with(tokenSubject("finance-user", org, "FINANCE"))
                .header(IDEMPOTENCY, "payment-paid"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PAID"));

    mockMvc
        .perform(
            get("/api/v1/vendor-invoices/{id}", invoiceId)
                .with(tokenSubject("finance-user", org, "FINANCE")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PAID"))
        .andExpect(jsonPath("$.paidAmount.amount").value("160.0000"));
  }

  @Test
  void rejectedInvoiceCanBeCorrectedWithSameNumber() throws Exception {
    UUID requestId = createApprovedRequest("Reject invoice", "100.00", 2);
    MvcResult created = createPurchaseOrder(requestId);
    UUID poId = UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.id"));
    UUID lineId =
        UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.lines[0].id"));
    MvcResult issued = issue(created);
    recordReceipt(poId, issued.getResponse().getHeader("ETag"), lineId, "2.0000", "reject-receipt")
        .andExpect(status().isCreated());

    MvcResult invoice =
        mockMvc
            .perform(
                post("/api/v1/purchase-orders/{id}/vendor-invoices", poId)
                    .with(tokenSubject("finance-user", org, "FINANCE"))
                    .header(IDEMPOTENCY, "invoice-reject")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        invoiceJson(
                            "INV-REJECT",
                            lineId,
                            "2.0000",
                            new java.math.BigDecimal("120.00"),
                            "0.00")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("MISMATCHED"))
            .andReturn();

    UUID invoiceId =
        UUID.fromString(JsonPath.read(invoice.getResponse().getContentAsString(), "$.id"));
    mockMvc
        .perform(
            post("/api/v1/vendor-invoices/{id}/reject", invoiceId)
                .with(tokenSubject("finance-user", org, "FINANCE"))
                .header("If-Match", invoice.getResponse().getHeader("ETag"))
                .header(IDEMPOTENCY, "invoice-reject-action")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("note", "Vendor price is incorrect"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("REJECTED"))
        .andExpect(jsonPath("$.resolutionNote").value("Vendor price is incorrect"));

    mockMvc
        .perform(
            post("/api/v1/purchase-orders/{id}/vendor-invoices", poId)
                .with(tokenSubject("finance-user", org, "FINANCE"))
                .header(IDEMPOTENCY, "invoice-corrected")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    invoiceJson(
                        "INV-REJECT",
                        lineId,
                        "2.0000",
                        new java.math.BigDecimal("100.00"),
                        "0.00")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("MATCHED"));
  }

  @Test
  void flagsMismatchedInvoiceAndFinanceCanApproveIt() throws Exception {
    UUID requestId = createApprovedRequest("Invoice mismatch", "100.00", 2);
    MvcResult created = createPurchaseOrder(requestId);
    UUID poId = UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.id"));
    UUID lineId =
        UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.lines[0].id"));
    MvcResult issued = issue(created);
    recordReceipt(poId, issued.getResponse().getHeader("ETag"), lineId, "1.0000", "partial-invoice")
        .andExpect(status().isCreated());

    MvcResult invoice =
        mockMvc
            .perform(
                post("/api/v1/purchase-orders/{id}/vendor-invoices", poId)
                    .with(tokenSubject("finance-user", org, "FINANCE"))
                    .header(IDEMPOTENCY, "invoice-mismatch")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        invoiceJson(
                            "INV-200",
                            lineId,
                            "2.0000",
                            new java.math.BigDecimal("105.00"),
                            "0.00")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("MISMATCHED"))
            .andExpect(jsonPath("$.lines[0].matched").value(false))
            .andExpect(
                jsonPath("$.lines[0].matchMessage")
                    .value(
                        allOf(
                            containsString("exceeds received"),
                            containsString("exceeds tolerance"))))
            .andReturn();

    UUID invoiceId =
        UUID.fromString(JsonPath.read(invoice.getResponse().getContentAsString(), "$.id"));
    mockMvc
        .perform(
            post("/api/v1/vendor-invoices/{id}/approve", invoiceId)
                .with(tokenSubject("requesting-user", org, "REQUESTER"))
                .header("If-Match", invoice.getResponse().getHeader("ETag"))
                .header(IDEMPOTENCY, "invoice-approve-forbidden")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            post("/api/v1/vendor-invoices/{id}/approve", invoiceId)
                .with(tokenSubject("finance-user", org, "FINANCE"))
                .header("If-Match", invoice.getResponse().getHeader("ETag"))
                .header(IDEMPOTENCY, "invoice-approve")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"))
        .andExpect(jsonPath("$.approvedBy").value("finance-user"));

    mockMvc
        .perform(
            post("/api/v1/purchase-orders/{id}/vendor-invoices", poId)
                .with(tokenSubject("requesting-user", org, "REQUESTER"))
                .header(IDEMPOTENCY, "invoice-requester")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    invoiceJson(
                        "INV-300", lineId, "1.0000", new java.math.BigDecimal("100.00"), "0.00")))
        .andExpect(status().isForbidden());

    UUID orgB = createOrganization("invoice-org-b");
    mockMvc
        .perform(
            get("/api/v1/vendor-invoices/{id}", invoiceId)
                .with(tokenSubject("finance-b", orgB, "FINANCE")))
        .andExpect(status().isNotFound());
  }

  @Test
  void organizationToleranceControlsInvoiceMatch() throws Exception {
    mockMvc
        .perform(
            put("/api/v1/organizations/{id}", org)
                .with(token(org, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", "po-org", "invoiceTolerancePercent", 10))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.invoiceTolerancePercent").value(10));

    UUID requestId = createApprovedRequest("Tolerance", "100.00", 1);
    MvcResult created = createPurchaseOrder(requestId);
    UUID poId = UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.id"));
    UUID lineId =
        UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.lines[0].id"));
    MvcResult issued = issue(created);
    recordReceipt(
            poId, issued.getResponse().getHeader("ETag"), lineId, "1.0000", "tolerance-receipt")
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/v1/purchase-orders/{id}/vendor-invoices", poId)
                .with(tokenSubject("finance-user", org, "FINANCE"))
                .header(IDEMPOTENCY, "invoice-tolerance")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    invoiceJson(
                        "INV-TOL", lineId, "1.0000", new java.math.BigDecimal("108.00"), "0.00")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("MATCHED"))
        .andExpect(jsonPath("$.tolerancePercent").value(10.00));
  }

  private String invoiceJson(
      String number, UUID lineId, String quantity, java.math.BigDecimal price, String tax)
      throws Exception {
    return json(
        Map.of(
            "invoiceNumber",
            number,
            "invoiceDate",
            LocalDate.now().toString(),
            "currency",
            "TZS",
            "taxAmount",
            new java.math.BigDecimal(tax),
            "lines",
            List.of(
                Map.of(
                    "purchaseOrderLineId",
                    lineId.toString(),
                    "quantity",
                    new java.math.BigDecimal(quantity),
                    "unitPrice",
                    price))));
  }

  @Test
  void webhookCrudRequiresAdminAndIsTenantIsolated() throws Exception {
    MvcResult created =
        mockMvc
            .perform(
                post("/api/v1/webhooks")
                    .with(token(org, "ADMIN"))
                    .header(IDEMPOTENCY, "webhook-create")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json(
                            Map.of(
                                "url", "https://example.test/hooks/pams",
                                "description", "ERP bridge",
                                "eventTypes",
                                    List.of("PurchaseOrderIssued", "GoodsReceiptRecorded")))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.signingSecret").isNotEmpty())
            .andExpect(jsonPath("$.subscription.active").value(true))
            .andExpect(jsonPath("$.subscription.eventTypes.length()").value(2))
            .andReturn();

    String createdBody = created.getResponse().getContentAsString();
    assertThat(createdBody).doesNotContain("secret\":\"");
    UUID webhookId = UUID.fromString(JsonPath.read(createdBody, "$.subscription.id"));

    MvcResult list =
        mockMvc
            .perform(get("/api/v1/webhooks").with(token(org, "ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andReturn();
    assertThat(list.getResponse().getContentAsString()).doesNotContain("signingSecret");

    mockMvc
        .perform(get("/api/v1/webhooks").with(token(org, "FINANCE")))
        .andExpect(status().isForbidden());

    UUID orgB = createOrganization("webhook-org-b");
    mockMvc
        .perform(get("/api/v1/webhooks").with(token(orgB, "ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
    mockMvc
        .perform(
            delete("/api/v1/webhooks/{id}", webhookId)
                .with(token(orgB, "ADMIN"))
                .header("If-Match", "\"0\""))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(
            delete("/api/v1/webhooks/{id}", webhookId)
                .with(token(org, "ADMIN"))
                .header("If-Match", created.getResponse().getHeader("ETag")))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(get("/api/v1/webhooks").with(token(org, "ADMIN")))
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void deliversSignedWebhookAndRecordsDelivery() throws Exception {
    ReceivedRequest received = new ReceivedRequest();
    HttpServer server = startWebhookServer(200, received);
    try {
      WebhookCreated webhook =
          createWebhook("http://localhost:" + server.getAddress().getPort() + "/hook", "deliver");
      UUID requestId = createApprovedRequest("Webhook PO", "100.00");
      issue(createPurchaseOrder(requestId));

      assertThat(outboxDispatcher.dispatchBatch()).isEqualTo(1);
      assertThat(webhookDeliveryDispatcher.dispatchBatch()).isEqualTo(1);

      assertThat(received.body.get()).contains("PurchaseOrderIssued").contains("poNumber");
      assertThat(received.contentType.get()).isEqualTo("application/json");
      assertThat(received.eventType.get()).isEqualTo("PurchaseOrderIssued");
      assertThat(received.signature.get())
          .isEqualTo(
              expectedSignature(
                  webhook.secret(), Long.parseLong(received.timestamp.get()), received.body.get()));

      mockMvc
          .perform(get("/api/v1/webhooks/{id}/deliveries", webhook.id()).with(token(org, "ADMIN")))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[0].status").value("SUCCEEDED"))
          .andExpect(jsonPath("$[0].attempts").value(1))
          .andExpect(jsonPath("$[0].responseStatus").value(200))
          .andExpect(jsonPath("$[0].eventType").value("PurchaseOrderIssued"));
    } finally {
      server.stop(0);
    }
  }

  @Test
  void retriesFailedWebhookDelivery() throws Exception {
    ReceivedRequest received = new ReceivedRequest();
    AtomicInteger calls = new AtomicInteger();
    HttpServer server = startWebhookServer(0, received, calls);
    try {
      WebhookCreated webhook =
          createWebhook("http://localhost:" + server.getAddress().getPort() + "/hook", "retry");
      UUID requestId = createApprovedRequest("Webhook retry", "100.00");
      issue(createPurchaseOrder(requestId));
      outboxDispatcher.dispatchBatch();

      assertThat(webhookDeliveryDispatcher.dispatchBatch()).isZero();

      MvcResult failed =
          mockMvc
              .perform(
                  get("/api/v1/webhooks/{id}/deliveries", webhook.id()).with(token(org, "ADMIN")))
              .andExpect(jsonPath("$[0].status").value("PENDING"))
              .andExpect(jsonPath("$[0].attempts").value(1))
              .andExpect(jsonPath("$[0].responseStatus").value(500))
              .andReturn();
      UUID deliveryId =
          UUID.fromString(JsonPath.read(failed.getResponse().getContentAsString(), "$[0].id"));

      mockMvc
          .perform(
              post("/api/v1/webhooks/deliveries/{id}/retry", deliveryId)
                  .with(token(org, "ADMIN"))
                  .header(IDEMPOTENCY, "webhook-retry"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("PENDING"))
          .andExpect(jsonPath("$.attempts").value(0));

      assertThat(webhookDeliveryDispatcher.dispatchBatch()).isEqualTo(1);
      assertThat(calls).hasValue(2);
      mockMvc
          .perform(get("/api/v1/webhooks/{id}/deliveries", webhook.id()).with(token(org, "ADMIN")))
          .andExpect(jsonPath("$[0].status").value("SUCCEEDED"))
          .andExpect(jsonPath("$[0].attempts").value(1));
    } finally {
      server.stop(0);
    }
  }

  private WebhookCreated createWebhook(String url, String description) throws Exception {
    MvcResult created =
        mockMvc
            .perform(
                post("/api/v1/webhooks")
                    .with(token(org, "ADMIN"))
                    .header(IDEMPOTENCY, "webhook-" + UUID.randomUUID())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json(
                            Map.of(
                                "url", url,
                                "description", description,
                                "eventTypes", List.of("PurchaseOrderIssued")))))
            .andExpect(status().isCreated())
            .andReturn();
    String body = created.getResponse().getContentAsString();
    return new WebhookCreated(
        UUID.fromString(JsonPath.read(body, "$.subscription.id")),
        JsonPath.read(body, "$.signingSecret"));
  }

  private HttpServer startWebhookServer(int fixedStatus, ReceivedRequest received)
      throws Exception {
    return startWebhookServer(fixedStatus, received, new AtomicInteger());
  }

  private HttpServer startWebhookServer(
      int fixedStatus, ReceivedRequest received, AtomicInteger calls) throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/hook",
        exchange -> {
          int call = calls.incrementAndGet();
          received.body.set(
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          received.contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
          received.eventType.set(exchange.getRequestHeaders().getFirst("X-PAMS-Event"));
          received.timestamp.set(exchange.getRequestHeaders().getFirst("X-PAMS-Timestamp"));
          received.signature.set(exchange.getRequestHeaders().getFirst("X-PAMS-Signature"));
          int status = fixedStatus == 0 ? (call == 1 ? 500 : 200) : fixedStatus;
          byte[] response = "ok".getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(status, response.length);
          exchange.getResponseBody().write(response);
          exchange.close();
        });
    server.start();
    return server;
  }

  private String expectedSignature(String secret, long timestamp, String body) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    return "sha256="
        + HexFormat.of()
            .formatHex(mac.doFinal((timestamp + "." + body).getBytes(StandardCharsets.UTF_8)));
  }

  private record WebhookCreated(UUID id, String secret) {}

  private static final class ReceivedRequest {
    private final AtomicReference<String> body = new AtomicReference<>();
    private final AtomicReference<String> contentType = new AtomicReference<>();
    private final AtomicReference<String> eventType = new AtomicReference<>();
    private final AtomicReference<String> timestamp = new AtomicReference<>();
    private final AtomicReference<String> signature = new AtomicReference<>();
  }

  private org.springframework.test.web.servlet.ResultActions recordReceipt(
      UUID poId, String etag, UUID lineId, String quantity, String key) throws Exception {
    return mockMvc.perform(
        post("/api/v1/purchase-orders/{id}/receipts", poId)
            .with(tokenSubject("officer-user", org, "PROCUREMENT_OFFICER"))
            .header("If-Match", etag)
            .header(IDEMPOTENCY, key)
            .contentType(MediaType.APPLICATION_JSON)
            .content(receiptJson(lineId, quantity)));
  }

  private String receiptJson(UUID lineId, String quantity) throws Exception {
    return json(
        Map.of(
            "receivedDate",
            LocalDate.now().toString(),
            "deliveryNoteReference",
            "DN-1",
            "notes",
            "Delivered in good condition",
            "lines",
            List.of(Map.of("purchaseOrderLineId", lineId.toString(), "quantity", quantity))));
  }

  private MvcResult issue(MvcResult created) throws Exception {
    UUID poId = UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.id"));
    String etag = created.getResponse().getHeader("ETag");
    return mockMvc
        .perform(
            post("/api/v1/purchase-orders/{id}/issue", poId)
                .with(tokenSubject("officer-user", org, "PROCUREMENT_OFFICER"))
                .header("If-Match", etag)
                .header(IDEMPOTENCY, "issue-" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk())
        .andReturn();
  }

  private MvcResult createPurchaseOrder(UUID requestId) throws Exception {
    return mockMvc
        .perform(
            post("/api/v1/purchase-requests/{id}/purchase-orders", requestId)
                .with(tokenSubject("officer-user", org, "PROCUREMENT_OFFICER"))
                .header(IDEMPOTENCY, "po-" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "vendorId", vendor.toString(),
                            "paymentTerms", "Net 30",
                            "deliveryDate", LocalDate.now().plusDays(7).toString()))))
        .andExpect(status().isCreated())
        .andReturn();
  }

  private UUID createApprovedRequest(String title, String unitPrice) throws Exception {
    return createApprovedRequest(title, unitPrice, 1);
  }

  private UUID createApprovedRequest(String title, String unitPrice, int quantity)
      throws Exception {
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
                                title,
                                "justification",
                                "Test",
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
                                        "Item",
                                        "quantity",
                                        quantity,
                                        "unit",
                                        "EA",
                                        "unitPrice",
                                        new java.math.BigDecimal(unitPrice)))))))
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

  private void createQuorumPurchaseOrderPolicy() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/approval-policies")
                .with(token(org, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "name",
                            "PO quorum approval",
                            "entityType",
                            "PURCHASE_ORDER",
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
                                    24,
                                    "stage",
                                    1,
                                    "minApprovals",
                                    2),
                                Map.of(
                                    "name",
                                    "Finance",
                                    "approverType",
                                    "ROLE",
                                    "approverRole",
                                    "FINANCE",
                                    "slaHours",
                                    24,
                                    "stage",
                                    1,
                                    "minApprovals",
                                    2),
                                Map.of(
                                    "name",
                                    "Admin",
                                    "approverType",
                                    "ROLE",
                                    "approverRole",
                                    "ADMIN",
                                    "slaHours",
                                    24,
                                    "stage",
                                    1,
                                    "minApprovals",
                                    2))))))
        .andExpect(status().isCreated());
  }

  private void createParallelPurchaseOrderPolicy() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/approval-policies")
                .with(token(org, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "name",
                            "PO parallel approval",
                            "entityType",
                            "PURCHASE_ORDER",
                            "amountMin",
                            0,
                            "steps",
                            List.of(
                                Map.of(
                                    "name", "Approver",
                                    "approverType", "ROLE",
                                    "approverRole", "APPROVER",
                                    "slaHours", 24,
                                    "stage", 1),
                                Map.of(
                                    "name", "Finance",
                                    "approverType", "ROLE",
                                    "approverRole", "FINANCE",
                                    "slaHours", 24,
                                    "stage", 1),
                                Map.of(
                                    "name", "Admin",
                                    "approverType", "ROLE",
                                    "approverRole", "ADMIN",
                                    "slaHours", 24,
                                    "stage", 2))))))
        .andExpect(status().isCreated());
  }

  private void createPurchaseOrderPolicy() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/approval-policies")
                .with(token(org, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "name",
                            "PO approval",
                            "entityType",
                            "PURCHASE_ORDER",
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
                            "PO policy",
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

  private UUID createVendor(String name, String taxId, String email) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/vendors")
                    .with(token(org, "ADMIN"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json(
                            Map.of(
                                "name", name,
                                "taxId", taxId,
                                "contactEmail", email))))
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
                            "departmentId", department.toString(),
                            "fiscalYear", YEAR,
                            "allocated", Map.of("amount", "100000.00", "currency", "TZS")))))
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
