package com.company.procurement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.procurement.budget.application.BudgetService;
import com.company.procurement.budget.domain.BudgetExceededException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
      "DELETE FROM idempotency_record",
      "DELETE FROM request_attachment",
      "DELETE FROM purchase_request_line",
      "DELETE FROM purchase_request",
      "DELETE FROM budget",
      "DELETE FROM user_profile",
      "DELETE FROM vendor",
      "DELETE FROM catalog_item",
      "DELETE FROM department",
      "DELETE FROM tenant_provisioning",
      "DELETE FROM tenant_subscription",
      "DELETE FROM stripe_event",
      "DELETE FROM outbox_event",
      "DELETE FROM organization"
    },
    executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class BudgetConcurrencyIntegrationTest {

  private static final UUID BOOTSTRAP_ORG = UUID.fromString("99999999-9999-9999-9999-999999999999");
  private static final int YEAR = LocalDate.now(ZoneOffset.UTC).getYear();

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private BudgetService budgetService;

  private UUID org;
  private UUID department;
  private UUID budgetId;

  @BeforeEach
  void setUp() throws Exception {
    org = createOrganization("budget-org");
    department = createDepartment(org, "Finance");
    budgetId = createBudget(org, department, "100.00");
  }

  @Test
  void concurrentReservationsCannotOverspend() throws Exception {
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    Callable<Boolean> attempt =
        () -> {
          start.await();
          try {
            budgetService.reserveForDepartment(
                org, department, YEAR, new BigDecimal("80.00"), "TZS");
            return true;
          } catch (BudgetExceededException ex) {
            return false;
          }
        };

    Future<Boolean> first = executor.submit(attempt);
    Future<Boolean> second = executor.submit(attempt);
    start.countDown();

    boolean firstResult = first.get(30, TimeUnit.SECONDS);
    boolean secondResult = second.get(30, TimeUnit.SECONDS);
    executor.shutdownNow();

    assertThat(firstResult ^ secondResult).as("exactly one reservation should succeed").isTrue();

    mockMvc
        .perform(get("/api/v1/budgets/{id}", budgetId).with(token(org, "FINANCE")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reserved.amount").value("80.0000"));

    budgetService.release(org, budgetId, new BigDecimal("80.00"));

    mockMvc
        .perform(get("/api/v1/budgets/{id}", budgetId).with(token(org, "FINANCE")))
        .andExpect(jsonPath("$.reserved.amount").value("0.0000"));
  }

  @Test
  void releaseCannotDropBelowZero() {
    assertThatThrownBy(() -> budgetService.release(org, budgetId, new BigDecimal("10.00")))
        .isInstanceOf(RuntimeException.class);
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

  private RequestPostProcessor token(UUID organizationId, String... roles) {
    return jwt()
        .jwt(
            jwt ->
                jwt.subject("test-user")
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
