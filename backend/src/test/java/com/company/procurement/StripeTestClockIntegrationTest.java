package com.company.procurement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.company.procurement.subscription.application.SubscriptionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Opt-in real Stripe test-clock regression. Run with:
 *
 * <pre>
 * STRIPE_TEST_CLOCK_E2E=true STRIPE_SECRET_KEY=sk_test_... \
 *   ./backend/mvnw -f backend/pom.xml -Dtest=StripeTestClockIntegrationTest test
 * </pre>
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@EnabledIfEnvironmentVariable(named = "STRIPE_TEST_CLOCK_E2E", matches = "true")
class StripeTestClockIntegrationTest {

  private static final String STRIPE_API = "https://api.stripe.com/v1";

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private SubscriptionService subscriptions;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private Clock clock;

  @Value("${pams.stripe.secret-key:}")
  private String stripeSecretKey;

  @Value("${STRIPE_TEST_CLOCK_PRICE_ID:}")
  private String stripeClockPriceId;

  private final HttpClient http = HttpClient.newHttpClient();

  @Test
  void reconcileTracksStripeTestClockRenewal() throws Exception {
    Assumptions.assumeTrue(
        stripeSecretKey != null && !stripeSecretKey.isBlank(),
        "STRIPE_SECRET_KEY is required for the Stripe test-clock suite");

    UUID organizationId = UUID.randomUUID();
    String stripeClockId = null;
    String stripeCustomerId = null;
    String stripeSubscriptionId = null;
    try {
      String slug = "stripe-clock-" + organizationId.toString().substring(0, 8);
      jdbcTemplate.update(
          "insert into organization (id, name, slug, created_at, created_by, updated_at, updated_by) values (?, 'Stripe Clock Tenant', ?, now(), 'test', now(), 'test')",
          organizationId,
          slug);
      Map<String, Object> plan =
          jdbcTemplate.queryForMap(
              "select monthly_price, annual_price, currency, stripe_price_monthly from subscription_plan where code = 'GROWTH'");
      String priceId =
          stripeClockPriceId == null || stripeClockPriceId.isBlank()
              ? (String) plan.get("stripe_price_monthly")
              : stripeClockPriceId;
      assertNotNull(
          priceId, "Set STRIPE_TEST_CLOCK_PRICE_ID or configure GROWTH.stripe_price_monthly");

      JsonNode clockNode =
          stripe(
              "POST",
              "/test_helpers/test_clocks",
              Map.of(
                  "frozen_time",
                  String.valueOf(clock.instant().getEpochSecond()),
                  "name",
                  "pams-regression-" + slug));
      stripeClockId = clockNode.path("id").asText();
      JsonNode customer =
          stripe("POST", "/customers", Map.of("name", slug, "test_clock", stripeClockId));
      stripeCustomerId = customer.path("id").asText();
      JsonNode paymentMethod =
          stripe(
              "POST", "/payment_methods/pm_card_visa/attach", Map.of("customer", stripeCustomerId));
      stripe(
          "POST",
          "/customers/" + stripeCustomerId,
          Map.of("invoice_settings[default_payment_method]", paymentMethod.path("id").asText()));
      Map<String, String> subscriptionForm = new LinkedHashMap<>();
      subscriptionForm.put("customer", stripeCustomerId);
      subscriptionForm.put("items[0][price]", priceId);
      subscriptionForm.put("metadata[organizationId]", organizationId.toString());
      subscriptionForm.put("metadata[planCode]", "GROWTH");
      subscriptionForm.put("metadata[billingInterval]", "MONTHLY");
      JsonNode stripeSubscription = stripe("POST", "/subscriptions", subscriptionForm);
      stripeSubscriptionId = stripeSubscription.path("id").asText();
      long firstPeriodEnd = stripeSubscription.path("current_period_end").asLong();

      jdbcTemplate.update(
          """
          insert into tenant_subscription
              (organization_id, plan_code, status, billing_interval, stripe_customer_id,
               stripe_subscription_id, current_period_start, current_period_end,
               cancel_at_period_end, created_at, created_by, updated_at, updated_by, version,
               billing_source, monthly_price_snapshot, annual_price_snapshot, currency_snapshot,
               stripe_price_id, last_stripe_sync_at, activated_at)
          values (?, 'GROWTH', 'ACTIVE', 'MONTHLY', ?, ?, ?, ?, false, now(), 'test', now(), 'test', 0,
                  'STRIPE', ?, ?, ?, ?, now(), now())
          """,
          organizationId,
          stripeCustomerId,
          stripeSubscriptionId,
          java.sql.Timestamp.from(
              Instant.ofEpochSecond(stripeSubscription.path("current_period_start").asLong())),
          java.sql.Timestamp.from(Instant.ofEpochSecond(firstPeriodEnd)),
          plan.get("monthly_price"),
          plan.get("annual_price"),
          plan.get("currency"),
          priceId);

      SubscriptionService.SubscriptionView initial = subscriptions.reconcile(organizationId);
      assertEquals("GROWTH", initial.plan().code());
      assertEquals("ACTIVE", initial.status());
      assertEquals("STRIPE", initial.billingSource());

      long advanced = firstPeriodEnd + 86_400;
      stripe(
          "POST",
          "/test_helpers/test_clocks/" + stripeClockId + "/advance",
          Map.of("frozen_time", String.valueOf(advanced)));
      long renewedPeriodEnd = awaitRenewedPeriod(stripeSubscriptionId, firstPeriodEnd);

      SubscriptionService.SubscriptionView reconciled = subscriptions.reconcile(organizationId);
      Instant localPeriodEnd = reconciled.currentPeriodEnd();
      assertNotNull(localPeriodEnd);
      assertTrue(
          Math.abs(localPeriodEnd.getEpochSecond() - renewedPeriodEnd) <= 2,
          "local period end should follow the Stripe test-clock renewal");
    } finally {
      if (stripeSubscriptionId != null) {
        quietly("DELETE", "/subscriptions/" + stripeSubscriptionId);
      }
      if (stripeCustomerId != null) {
        quietly("DELETE", "/customers/" + stripeCustomerId);
      }
      if (stripeClockId != null) {
        quietly("DELETE", "/test_helpers/test_clocks/" + stripeClockId);
      }
      jdbcTemplate.update(
          "delete from tenant_subscription where organization_id = ?", organizationId);
      jdbcTemplate.update("delete from organization where id = ?", organizationId);
    }
  }

  private long awaitRenewedPeriod(String subscriptionId, long previousPeriodEnd)
      throws InterruptedException {
    for (int attempt = 0; attempt < 20; attempt++) {
      JsonNode subscription = stripe("GET", "/subscriptions/" + subscriptionId, null);
      long periodEnd = subscription.path("current_period_end").asLong();
      if (periodEnd > previousPeriodEnd) return periodEnd;
      Thread.sleep(1500);
    }
    throw new AssertionError("Stripe test clock did not advance the subscription period");
  }

  private JsonNode stripe(String method, String path, Map<String, String> form) {
    try {
      String body = form == null ? "" : encode(form);
      HttpRequest.Builder builder =
          HttpRequest.newBuilder(URI.create(STRIPE_API + path))
              .timeout(java.time.Duration.ofSeconds(30))
              .header("Authorization", "Bearer " + stripeSecretKey);
      if ("GET".equals(method)) {
        builder.GET();
      } else if ("DELETE".equals(method)) {
        builder.DELETE();
      } else {
        builder
            .header("Content-Type", "application/x-www-form-urlencoded")
            .method(method, HttpRequest.BodyPublishers.ofString(body));
      }
      HttpResponse<String> response =
          http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      JsonNode node = objectMapper.readTree(response.body());
      if (response.statusCode() >= 400) {
        throw new AssertionError(
            "Stripe %s %s failed: %s"
                .formatted(method, path, node.path("error").path("message").asText()));
      }
      return node;
    } catch (AssertionError ex) {
      throw ex;
    } catch (Exception ex) {
      throw new IllegalStateException("Stripe test-clock request failed", ex);
    }
  }

  private void quietly(String method, String path) {
    try {
      stripe(method, path, null);
    } catch (RuntimeException | AssertionError ignored) {
      // Test cleanup should not hide the assertion result.
    }
  }

  private static String encode(Map<String, String> values) {
    return values.entrySet().stream()
        .map(
            entry ->
                URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)
                    + "="
                    + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
        .collect(Collectors.joining("&"));
  }
}
