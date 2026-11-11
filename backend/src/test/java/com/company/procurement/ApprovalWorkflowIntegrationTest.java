package com.company.procurement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.procurement.approval.application.ApprovalService;
import com.company.procurement.shared.tenant.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.Instant;
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
import org.springframework.jdbc.core.JdbcTemplate;
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
class ApprovalWorkflowIntegrationTest {

  private static final UUID BOOTSTRAP_ORG = UUID.fromString("99999999-9999-9999-9999-999999999999");
  private static final int YEAR = LocalDate.now(ZoneOffset.UTC).getYear();
  private static final String IDEMPOTENCY = "Idempotency-Key";

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private ApprovalService approvalService;

  private UUID org;
  private UUID department;

  @BeforeEach
  void setUp() throws Exception {
    org = createOrganization("approval-org");
    department = createDepartment(org, "Finance");
    createBudget("100000.00");
  }

  @Test
  void approvesThroughAllStepsAndRecordsAuditEvents() throws Exception {
    UUID policyId = createPolicy("Two-step", "APPROVER", "FINANCE");
    UUID requestId = createRequest("Laptops", "500.00");

    MvcResult timeline = timeline(requestId);
    assertThat(
            (Integer) JsonPath.read(timeline.getResponse().getContentAsString(), "$.policyVersion"))
        .isEqualTo(1);
    UUID firstStep =
        UUID.fromString(
            JsonPath.read(timeline.getResponse().getContentAsString(), "$.steps[0].id"));
    UUID secondStep =
        UUID.fromString(
            JsonPath.read(timeline.getResponse().getContentAsString(), "$.steps[1].id"));

    mockMvc
        .perform(
            post("/api/v1/approvals/{stepId}/approve", firstStep)
                .with(tokenSubject("approver-user", org, "APPROVER"))
                .header(IDEMPOTENCY, "approve-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
        .andExpect(jsonPath("$.currentSequence").value(2));

    mockMvc
        .perform(
            post("/api/v1/approvals/{stepId}/approve", secondStep)
                .with(tokenSubject("finance-user", org, "FINANCE"))
                .header(IDEMPOTENCY, "approve-2")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"));

    mockMvc
        .perform(
            get("/api/v1/purchase-requests/{id}", requestId)
                .with(tokenSubject("requesting-user", org, "REQUESTER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"))
        .andExpect(jsonPath("$.approvalPolicyId").value(policyId.toString()));

    List<String> actions =
        jdbcTemplate.queryForList(
            "select action from audit_event where aggregate_id = ?::uuid",
            String.class,
            requestId.toString());
    assertThat(actions).contains("request_submitted", "approval_step_approved", "request_approved");
  }

  @Test
  void parallelStageWaitsForEveryApproverBeforeAdvancing() throws Exception {
    createPolicyBody(
        "Parallel stage",
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
                "stage", 2)));
    UUID requestId = createRequest("Parallel request", "500.00");
    MvcResult timeline = timeline(requestId);
    String body = timeline.getResponse().getContentAsString();
    UUID approverStep = stepIdForRole(requestId, "APPROVER");
    UUID financeStep = stepIdForRole(requestId, "FINANCE");
    UUID adminStep = stepIdForRole(requestId, "ADMIN");
    assertThat((Integer) JsonPath.read(body, "$.currentSequence")).isEqualTo(1);

    mockMvc
        .perform(
            post("/api/v1/approvals/{stepId}/approve", approverStep)
                .with(tokenSubject("approver-user", org, "APPROVER"))
                .header(IDEMPOTENCY, "parallel-approver")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
        .andExpect(jsonPath("$.currentSequence").value(1));

    mockMvc
        .perform(
            post("/api/v1/approvals/{stepId}/approve", financeStep)
                .with(tokenSubject("finance-user", org, "FINANCE"))
                .header(IDEMPOTENCY, "parallel-finance")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
        .andExpect(jsonPath("$.currentSequence").value(2));

    mockMvc
        .perform(
            post("/api/v1/approvals/{stepId}/approve", adminStep)
                .with(token(org, "ADMIN"))
                .header(IDEMPOTENCY, "parallel-admin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"));
  }

  @Test
  void adminCanApproveAnyRoleStepAsFallback() throws Exception {
    createPolicy("Admin fallback", "APPROVER", "FINANCE");
    UUID requestId = createRequest("Admin fallback request", "500.00");
    MvcResult timeline = timeline(requestId);
    String body = timeline.getResponse().getContentAsString();
    UUID firstStep = UUID.fromString(JsonPath.read(body, "$.steps[0].id"));
    UUID secondStep = UUID.fromString(JsonPath.read(body, "$.steps[1].id"));

    mockMvc
        .perform(
            post("/api/v1/approvals/{stepId}/approve", firstStep)
                .with(tokenSubject("approver-user", org, "APPROVER"))
                .header(IDEMPOTENCY, "admin-fallback-first")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/v1/approvals/inbox").with(token(org, "ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(secondStep.toString()));

    mockMvc
        .perform(
            post("/api/v1/approvals/{stepId}/approve", secondStep)
                .with(token(org, "ADMIN"))
                .header(IDEMPOTENCY, "admin-fallback-second")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"));
  }

  @Test
  void deletingPolicyHidesItButPreservesExistingHistory() throws Exception {
    createPolicy("Disposable", "APPROVER");
    UUID requestId = createRequest("Policy history", "100.00");
    MvcResult policies =
        mockMvc
            .perform(get("/api/v1/approval-policies").with(token(org, "ADMIN")))
            .andExpect(status().isOk())
            .andReturn();
    UUID policyId =
        UUID.fromString(
            JsonPath.read(policies.getResponse().getContentAsString(), "$.content[0].id"));

    mockMvc
        .perform(delete("/api/v1/approval-policies/{id}", policyId).with(token(org, "ADMIN")))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/v1/approval-policies").with(token(org, "ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
    mockMvc
        .perform(
            get("/api/v1/purchase-requests/{id}/approval", requestId).with(token(org, "ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.steps[0].status").value("PENDING"));
  }

  @Test
  void policyListIncludesStepsWithoutLazyLoadingFailures() throws Exception {
    createPolicy("Listed", "APPROVER");

    mockMvc
        .perform(get("/api/v1/approval-policies").with(token(org, "ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].name").value("Listed"))
        .andExpect(jsonPath("$.content[0].steps[0].approverRole").value("APPROVER"));
  }

  @Test
  void selfProfileAndAvatarUnlockDelegation() throws Exception {
    UUID delegate = createUserProfile("delegate-self", null);
    mockMvc
        .perform(
            put("/api/v1/users/me")
                .with(tokenSubject("self-user", org, "APPROVER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(
                        Map.of("displayName", "Self User", "departmentId", department.toString()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.displayName").value("Self User"))
        .andExpect(jsonPath("$.hasAvatar").value(false));

    MockMultipartFile avatar =
        new MockMultipartFile("file", "avatar.png", "image/png", new byte[] {1, 2, 3, 4});
    mockMvc
        .perform(
            multipart("/api/v1/users/me/avatar")
                .file(avatar)
                .with(tokenSubject("self-user", org, "APPROVER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hasAvatar").value(true));
    mockMvc
        .perform(get("/api/v1/users/me/avatar").with(tokenSubject("self-user", org, "APPROVER")))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", "image/png"));

    mockMvc
        .perform(
            post("/api/v1/delegations")
                .with(tokenSubject("self-user", org, "APPROVER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "delegateUserId", delegate.toString(),
                            "startsAt", Instant.now().minusSeconds(60).toString(),
                            "endsAt", Instant.now().plusSeconds(3600).toString()))))
        .andExpect(status().isCreated());
  }

  @Test
  void actionsAndPermissionsAreServerDrivenByStatusOwnershipAndDatabaseRules() throws Exception {
    createPolicy("Actions", "APPROVER");
    UUID draftId = createDraft("Draft actions", "100.00");

    MvcResult draftActions =
        mockMvc
            .perform(
                get("/api/v1/purchase-requests/{id}/actions", draftId)
                    .with(tokenSubject("requesting-user", org, "REQUESTER")))
            .andExpect(status().isOk())
            .andReturn();
    assertThat(
            (List<String>)
                JsonPath.read(draftActions.getResponse().getContentAsString(), "$.actions"))
        .contains("EDIT", "DELETE", "SUBMIT", "CANCEL");

    MvcResult adminDraftActions =
        mockMvc
            .perform(
                get("/api/v1/purchase-requests/{id}/actions", draftId).with(token(org, "ADMIN")))
            .andExpect(status().isOk())
            .andReturn();
    assertThat(
            (List<String>)
                JsonPath.read(adminDraftActions.getResponse().getContentAsString(), "$.actions"))
        .doesNotContain("EDIT", "DELETE", "SUBMIT", "CANCEL");

    UUID requestId = createRequest("Approved actions", "100.00");
    UUID stepId = firstStepId(requestId);
    mockMvc
        .perform(
            post("/api/v1/approvals/{stepId}/approve", stepId)
                .with(tokenSubject("approver-user", org, "APPROVER"))
                .header(IDEMPOTENCY, "actions-approve")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk());

    MvcResult approvedActions =
        mockMvc
            .perform(
                get("/api/v1/purchase-requests/{id}/actions", requestId).with(token(org, "ADMIN")))
            .andExpect(status().isOk())
            .andReturn();
    assertThat(
            (List<String>)
                JsonPath.read(approvedActions.getResponse().getContentAsString(), "$.actions"))
        .containsExactly("CREATE_PO");

    MvcResult requesterActions =
        mockMvc
            .perform(
                get("/api/v1/purchase-requests/{id}/actions", requestId)
                    .with(tokenSubject("requesting-user", org, "REQUESTER")))
            .andExpect(status().isOk())
            .andReturn();
    assertThat(
            (List<String>)
                JsonPath.read(requesterActions.getResponse().getContentAsString(), "$.actions"))
        .doesNotContain("EDIT", "CANCEL", "CREATE_PO");

    MvcResult permissions =
        mockMvc
            .perform(
                get("/api/v1/me/permissions").with(tokenSubject("approver-user", org, "APPROVER")))
            .andExpect(status().isOk())
            .andReturn();
    assertThat(
            (List<String>)
                JsonPath.read(permissions.getResponse().getContentAsString(), "$.permissions"))
        .contains("approval:act:APPROVER")
        .doesNotContain("purchase_order:issue");
  }

  private UUID createDraft(String title, String unitPrice) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/purchase-requests")
                    .with(tokenSubject("requesting-user", org, "REQUESTER"))
                    .header(IDEMPOTENCY, "draft-" + UUID.randomUUID())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody(title, List.of(line("Laptop", "1", unitPrice)))))
            .andExpect(status().isCreated())
            .andReturn();
    return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
  }

  @Test
  void rejectRequiresCommentAndReleasesBudget() throws Exception {
    createPolicy("One-step", "APPROVER");
    UUID requestId = createRequest("Rejected", "300.00");
    UUID stepId = firstStepId(requestId);

    mockMvc
        .perform(
            post("/api/v1/approvals/{stepId}/reject", stepId)
                .with(tokenSubject("approver-user", org, "APPROVER"))
                .header(IDEMPOTENCY, "reject-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"comment\":\"\"}"))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(
            post("/api/v1/approvals/{stepId}/reject", stepId)
                .with(tokenSubject("approver-user", org, "APPROVER"))
                .header(IDEMPOTENCY, "reject-2")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"comment\":\"No budget priority\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("REJECTED"));

    mockMvc
        .perform(
            get("/api/v1/purchase-requests/{id}", requestId)
                .with(tokenSubject("requesting-user", org, "REQUESTER")))
        .andExpect(jsonPath("$.status").value("REJECTED"));

    Map<String, Object> budget =
        jdbcTemplate.queryForMap(
            "select reserved_amount from budget where organization_id = ?::uuid", org.toString());
    assertThat((BigDecimal) budget.get("reserved_amount")).isEqualByComparingTo("0.0000");
  }

  @Test
  void requestChangesThenResubmitCreatesFreshInstance() throws Exception {
    createPolicy("Two-step", "APPROVER", "FINANCE");
    UUID requestId = createRequest("Changes", "100.00");
    UUID stepId = firstStepId(requestId);

    mockMvc
        .perform(
            post("/api/v1/approvals/{stepId}/request-changes", stepId)
                .with(tokenSubject("approver-user", org, "APPROVER"))
                .header(IDEMPOTENCY, "changes-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"comment\":\"Add a quote\"}"))
        .andExpect(status().isOk());

    MvcResult current =
        mockMvc
            .perform(
                get("/api/v1/purchase-requests/{id}", requestId)
                    .with(tokenSubject("requesting-user", org, "REQUESTER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CHANGES_REQUESTED"))
            .andReturn();
    String etag = current.getResponse().getHeader("ETag");

    MvcResult updated =
        mockMvc
            .perform(
                put("/api/v1/purchase-requests/{id}", requestId)
                    .with(tokenSubject("requesting-user", org, "REQUESTER"))
                    .header("If-Match", etag)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        requestBody("Changes updated", List.of(line("Laptop", "1", "150.00")))))
            .andExpect(status().isOk())
            .andReturn();
    String updatedEtag = updated.getResponse().getHeader("ETag");

    mockMvc
        .perform(
            post("/api/v1/purchase-requests/{id}/submit", requestId)
                .with(tokenSubject("requesting-user", org, "REQUESTER"))
                .header("If-Match", updatedEtag)
                .header(IDEMPOTENCY, "resubmit-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("IN_APPROVAL"));

    Map<String, Object> budget =
        jdbcTemplate.queryForMap(
            "select reserved_amount from budget where organization_id = ?::uuid", org.toString());
    assertThat((BigDecimal) budget.get("reserved_amount")).isEqualByComparingTo("150.0000");
  }

  @Test
  void approvalLimitIsEnforced() throws Exception {
    createPolicy("Role step", "APPROVER");
    createUserProfile("limited-approver", "50.00");
    UUID requestId = createRequest("Expensive", "500.00");
    UUID stepId = firstStepId(requestId);

    mockMvc
        .perform(
            post("/api/v1/approvals/{stepId}/approve", stepId)
                .with(tokenSubject("limited-approver", org, "APPROVER"))
                .header(IDEMPOTENCY, "limit-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("business-rule-violation"));
  }

  @Test
  void delegateCanApproveOnBehalfOfAssignee() throws Exception {
    UUID delegateProfile = createUserProfile("delegate-user", null);
    UUID assigneeProfile = createUserProfile("assignee-user", null);
    createUserPolicy("User step", assigneeProfile);
    UUID requestId = createRequest("Delegated", "100.00");
    UUID stepId = firstStepId(requestId);

    mockMvc
        .perform(
            post("/api/v1/delegations")
                .with(tokenSubject("assignee-user", org, "APPROVER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "delegateUserId", delegateProfile.toString(),
                            "startsAt", Instant.now().minusSeconds(60).toString(),
                            "endsAt", Instant.now().plusSeconds(3600).toString()))))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/v1/approvals/{stepId}/approve", stepId)
                .with(tokenSubject("delegate-user", org, "APPROVER"))
                .header(IDEMPOTENCY, "delegate-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"));
  }

  @Test
  void escalationRemindsThenAdvancesToNextStep() throws Exception {
    createPolicy("Escalating", 1, "APPROVER", "FINANCE");
    UUID requestId = createRequest("Overdue", "100.00");
    UUID firstStep = firstStepId(requestId);
    jdbcTemplate.update(
        "update approval_step set sla_due_at = now() - interval '2 hours' where id = ?::uuid",
        firstStep.toString());

    TenantContext.set(org);
    try {
      approvalService.runEscalation(Instant.now());
      String reminder =
          jdbcTemplate.queryForObject(
              "select reminder_sent_at::text from approval_step where id = ?::uuid",
              String.class,
              firstStep.toString());
      assertThat(reminder).isNotNull();

      approvalService.runEscalation(Instant.now().plusSeconds(60));
      String status =
          jdbcTemplate.queryForObject(
              "select status from approval_step where id = ?::uuid",
              String.class,
              firstStep.toString());
      assertThat(status).isEqualTo("SKIPPED");
    } finally {
      TenantContext.clear();
    }

    mockMvc
        .perform(
            get("/api/v1/purchase-requests/{id}/approval", requestId).with(token(org, "ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currentSequence").value(2))
        .andExpect(jsonPath("$.steps[0].status").value("SKIPPED"))
        .andExpect(jsonPath("$.steps[0].escalatedAt").exists());
  }

  @Test
  void policyUpdateCreatesNewVersionAndSnapshotRemains() throws Exception {
    UUID policyId = createPolicy("Versioned", "APPROVER");
    UUID requestId = createRequest("Snapshot", "100.00");

    MvcResult updated =
        mockMvc
            .perform(
                put("/api/v1/approval-policies/{id}", policyId)
                    .with(token(org, "ADMIN"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json(
                            Map.of(
                                "name",
                                "Versioned",
                                "amountMin",
                                0,
                                "steps",
                                List.of(
                                    Map.of(
                                        "name", "Approver v2",
                                        "approverType", "ROLE",
                                        "approverRole", "APPROVER",
                                        "slaHours", 12))))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.policyVersion").value(2))
            .andReturn();
    UUID newPolicyId =
        UUID.fromString(JsonPath.read(updated.getResponse().getContentAsString(), "$.id"));
    assertThat(newPolicyId).isNotEqualTo(policyId);

    mockMvc
        .perform(
            get("/api/v1/purchase-requests/{id}/approval", requestId).with(token(org, "ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.policyVersion").value(1));
  }

  @Test
  void bulkApprovesSelectedCurrentSteps() throws Exception {
    createPolicy("Bulk approval", "APPROVER");
    UUID first = createRequest("Bulk one", "100.00");
    UUID second = createRequest("Bulk two", "200.00");
    UUID firstStep = firstStepId(first);
    UUID secondStep = firstStepId(second);

    mockMvc
        .perform(
            post("/api/v1/approvals/bulk-approve")
                .with(tokenSubject("approver-user", org, "APPROVER"))
                .header(IDEMPOTENCY, "bulk-approve-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "ids",
                            List.of(firstStep.toString(), secondStep.toString()),
                            "comment",
                            "Batch approved"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.processed").value(2));

    mockMvc
        .perform(
            get("/api/v1/purchase-requests/{id}", first)
                .with(tokenSubject("requesting-user", org, "REQUESTER")))
        .andExpect(jsonPath("$.status").value("APPROVED"));
    mockMvc
        .perform(
            get("/api/v1/purchase-requests/{id}", second)
                .with(tokenSubject("requesting-user", org, "REQUESTER")))
        .andExpect(jsonPath("$.status").value("APPROVED"));
  }

  @Test
  void requesterCannotApproveEvenWithApproverRole() throws Exception {
    createPolicy("Self", "APPROVER");
    createUserProfile("requester-user", null);
    UUID requestId = createRequestAs("requester-user", "Self approval", "100.00");
    UUID stepId = firstStepId(requestId);

    mockMvc
        .perform(
            post("/api/v1/approvals/{stepId}/approve", stepId)
                .with(tokenSubject("requester-user", org, "REQUESTER", "APPROVER"))
                .header(IDEMPOTENCY, "self-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden());
  }

  private MvcResult timeline(UUID requestId) throws Exception {
    return mockMvc
        .perform(
            get("/api/v1/purchase-requests/{id}/approval", requestId).with(token(org, "ADMIN")))
        .andExpect(status().isOk())
        .andReturn();
  }

  private UUID stepIdForRole(UUID requestId, String role) {
    return jdbcTemplate.queryForObject(
        """
        select s.id
          from approval_step s
          join approval_instance i on i.id = s.instance_id
         where i.purchase_request_id = ? and s.assignee_role = ?
         limit 1
        """,
        UUID.class,
        requestId,
        role);
  }

  private UUID firstStepId(UUID requestId) throws Exception {
    return UUID.fromString(
        JsonPath.read(timeline(requestId).getResponse().getContentAsString(), "$.steps[0].id"));
  }

  private UUID createRequest(String title, String unitPrice) throws Exception {
    return createRequestAs("requesting-user", title, unitPrice);
  }

  private UUID createRequestAs(String subject, String title, String unitPrice) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/purchase-requests")
                    .with(tokenSubject(subject, org, "REQUESTER"))
                    .header(IDEMPOTENCY, "create-" + UUID.randomUUID())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody(title, List.of(line("Laptop", "1", unitPrice)))))
            .andExpect(status().isCreated())
            .andReturn();
    UUID requestId =
        UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    mockMvc
        .perform(
            post("/api/v1/purchase-requests/{id}/submit", requestId)
                .with(tokenSubject(subject, org, "REQUESTER"))
                .header("If-Match", "\"0\"")
                .header(IDEMPOTENCY, "submit-" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk());
    return requestId;
  }

  private String requestBody(String title, List<Map<String, Object>> lines) throws Exception {
    return json(
        Map.of(
            "title",
            title,
            "justification",
            "Test",
            "departmentId",
            department.toString(),
            "neededBy",
            LocalDate.now().plusDays(10).toString(),
            "currency",
            "TZS",
            "budgetExceptionRequested",
            false,
            "lines",
            lines));
  }

  private Map<String, Object> line(String description, String quantity, String unitPrice) {
    return Map.of(
        "description",
        description,
        "quantity",
        new BigDecimal(quantity),
        "unit",
        "EA",
        "unitPrice",
        new BigDecimal(unitPrice));
  }

  private UUID createPolicy(String name, String... roles) throws Exception {
    return createPolicy(name, 24, roles);
  }

  private UUID createPolicy(String name, int slaHours, String... roles) throws Exception {
    List<Map<String, Object>> steps = new java.util.ArrayList<>();
    for (String role : roles) {
      steps.add(
          Map.of(
              "name", role,
              "approverType", "ROLE",
              "approverRole", role,
              "slaHours", slaHours));
    }
    return createPolicyBody(name, steps);
  }

  private void createUserPolicy(String name, UUID assigneeUserId) throws Exception {
    createPolicyBody(
        name,
        List.of(
            Map.of(
                "name",
                "Assigned",
                "approverType",
                "USER",
                "approverUserId",
                assigneeUserId.toString(),
                "slaHours",
                24)));
  }

  private UUID createPolicyBody(String name, List<Map<String, Object>> steps) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/approval-policies")
                    .with(token(org, "ADMIN"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json(
                            Map.of(
                                "name", name,
                                "amountMin", 0,
                                "steps", steps))))
            .andExpect(status().isCreated())
            .andReturn();
    return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
  }

  private UUID createUserProfile(String subject, String approvalLimit) throws Exception {
    Map<String, Object> body =
        new java.util.HashMap<>(
            Map.of(
                "keycloakSubject",
                subject,
                "email",
                subject + "@example.com",
                "displayName",
                subject,
                "departmentId",
                department.toString()));
    if (approvalLimit != null) {
      body.put("approvalLimit", Map.of("amount", approvalLimit, "currency", "TZS"));
    }
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/users")
                    .with(token(org, "ADMIN"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(body)))
            .andExpect(status().isCreated())
            .andReturn();
    return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
  }

  private void createBudget(String amount) throws Exception {
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
                            "allocated", Map.of("amount", amount, "currency", "TZS")))))
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
