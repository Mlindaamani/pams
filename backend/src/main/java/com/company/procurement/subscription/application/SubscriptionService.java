package com.company.procurement.subscription.application;

import com.company.procurement.shared.config.StripeProperties;
import com.company.procurement.shared.error.BusinessRuleException;
import com.company.procurement.shared.error.ResourceNotFoundException;
import com.company.procurement.shared.tenant.TenantContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Manages subscriptions, Stripe synchronization and billing analytics. */
@Service
public class SubscriptionService {

  private static final String STRIPE_API = "https://api.stripe.com/v1";

  private final JdbcTemplate jdbc;
  private final ObjectMapper objectMapper;
  private final StripeProperties stripe;
  private final SubscriptionFeatureService featureService;
  private final Clock clock;
  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  public SubscriptionService(
      JdbcTemplate jdbc,
      ObjectMapper objectMapper,
      StripeProperties stripe,
      SubscriptionFeatureService featureService,
      Clock clock) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
    this.stripe = stripe;
    this.featureService = featureService;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public List<PlanView> plans() {
    return loadPlans("select * from subscription_plan where active = true order by sort_order");
  }

  @Transactional(readOnly = true)
  public List<PlanView> allPlans() {
    return loadPlans("select * from subscription_plan order by sort_order, code");
  }

  @Transactional
  public PlanView createPlan(PlanCommand command) {
    String code = normalizePlanCode(command.code());
    if (!jdbc.queryForList("select 1 from subscription_plan where code = ?", code).isEmpty()) {
      throw new BusinessRuleException("Subscription plan %s already exists".formatted(code));
    }
    validatePlan(command);
    List<SubscriptionFeatureService.FeatureView> features =
        featureService.resolveForAssignment(command.featureCodes(), Set.of());
    jdbc.update(
        """
            insert into subscription_plan
                (code, name, description, monthly_price, annual_price, currency, trial_days,
                 max_users, max_departments, max_requests_month, max_storage_mb, active,
                 sort_order, stripe_price_monthly, stripe_price_annual, created_at, updated_at)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())
            """,
        code,
        command.name().trim(),
        command.description().trim(),
        command.monthlyPrice(),
        command.annualPrice(),
        command.currency().trim().toUpperCase(),
        command.trialDays(),
        command.maxUsers(),
        command.maxDepartments(),
        command.maxRequestsMonth(),
        command.maxStorageMb(),
        command.active(),
        command.sortOrder(),
        blankToNull(command.stripePriceMonthly()),
        blankToNull(command.stripePriceAnnual()));
    replacePlanFeatures(code, features);
    return planByCode(code);
  }

  @Transactional
  public PlanView updatePlan(String code, PlanCommand command) {
    PlanView existing = planByCode(code);
    validatePlan(command);
    Set<String> currentlyAssigned =
        existing.features().stream()
            .map(SubscriptionFeatureService.FeatureView::code)
            .collect(java.util.stream.Collectors.toSet());
    List<SubscriptionFeatureService.FeatureView> features =
        featureService.resolveForAssignment(command.featureCodes(), currentlyAssigned);
    jdbc.update(
        """
            update subscription_plan
               set name = ?, description = ?, monthly_price = ?, annual_price = ?, currency = ?,
                   trial_days = ?, max_users = ?, max_departments = ?, max_requests_month = ?,
                   max_storage_mb = ?, active = ?, sort_order = ?,
                   stripe_price_monthly = ?, stripe_price_annual = ?, updated_at = now()
             where code = ?
            """,
        command.name().trim(),
        command.description().trim(),
        command.monthlyPrice(),
        command.annualPrice(),
        command.currency().trim().toUpperCase(),
        command.trialDays(),
        command.maxUsers(),
        command.maxDepartments(),
        command.maxRequestsMonth(),
        command.maxStorageMb(),
        command.active(),
        command.sortOrder(),
        blankToNull(command.stripePriceMonthly()),
        blankToNull(command.stripePriceAnnual()),
        code);
    replacePlanFeatures(code, features);
    return planByCode(code);
  }

  @Transactional
  public void deactivatePlan(String code) {
    planByCode(code);
    jdbc.update(
        "update subscription_plan set active = false, updated_at = now() where code = ?", code);
  }

  private static void validatePlan(PlanCommand command) {
    if (command.name() == null || command.name().isBlank()) {
      throw new BusinessRuleException("Plan name is required");
    }
    if (command.description() == null || command.description().isBlank()) {
      throw new BusinessRuleException("Plan description is required");
    }
    if (command.monthlyPrice() == null || command.monthlyPrice().signum() < 0) {
      throw new BusinessRuleException("Monthly price must be zero or positive");
    }
    if (command.annualPrice() == null || command.annualPrice().signum() < 0) {
      throw new BusinessRuleException("Annual price must be zero or positive");
    }
    if (command.currency() == null || !command.currency().matches("(?i)[A-Z]{3}")) {
      throw new BusinessRuleException("Currency must be a three-letter ISO code");
    }
    if (command.trialDays() < 0 || command.trialDays() > 365) {
      throw new BusinessRuleException("Trial days must be between 0 and 365");
    }
    if (command.sortOrder() < 0) {
      throw new BusinessRuleException("Sort order must be zero or positive");
    }
  }

  private static String normalizePlanCode(String code) {
    String normalized = code == null ? "" : code.trim().toUpperCase();
    if (!normalized.matches("[A-Z0-9_]{2,30}")) {
      throw new BusinessRuleException(
          "Plan code must be 2-30 uppercase letters, numbers or underscores");
    }
    return normalized;
  }

  private List<PlanView> loadPlans(String sql, Object... args) {
    List<String> codes = jdbc.query(sql, (rs, row) -> rs.getString("code"), args);
    Map<String, List<SubscriptionFeatureService.FeatureView>> features =
        featureService.featuresForPlans(codes);
    return jdbc.query(
        sql, (rs, row) -> plan(rs, features.getOrDefault(rs.getString("code"), List.of())), args);
  }

  private void replacePlanFeatures(
      String planCode, List<SubscriptionFeatureService.FeatureView> features) {
    jdbc.update("delete from subscription_plan_feature where plan_code = ?", planCode);
    for (SubscriptionFeatureService.FeatureView feature : features) {
      jdbc.update(
          "insert into subscription_plan_feature (plan_code, feature_code) values (?, ?)",
          planCode,
          feature.code());
    }
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  @Transactional(readOnly = true)
  public SubscriptionView current() {
    return forTenant(TenantContext.require(), true);
  }

  @Transactional(readOnly = true)
  public List<String> currentFeatureCodes() {
    UUID organizationId = TenantContext.require();
    return jdbc.queryForList(
        """
        select pf.feature_code
          from tenant_subscription ts
          join subscription_plan_feature pf on pf.plan_code = ts.plan_code
          join subscription_feature f on f.code = pf.feature_code
         where ts.organization_id = ?
         order by f.sort_order, f.code
        """,
        String.class,
        organizationId);
  }

  @Transactional(readOnly = true)
  public SubscriptionView forTenant(UUID organizationId, boolean includeUsage) {
    Map<String, Object> row =
        jdbc
            .queryForList(
                "select * from tenant_subscription where organization_id = ?", organizationId)
            .stream()
            .findFirst()
            .orElseThrow(
                () ->
                    new ResourceNotFoundException("No subscription is configured for this tenant"));
    PlanView plan = planByCode((String) row.get("plan_code"));
    Usage usage = includeUsage ? usage(organizationId) : null;
    return new SubscriptionView(
        organizationId,
        plan,
        (String) row.get("status"),
        (String) row.get("billing_interval"),
        (String) row.get("billing_source"),
        instant(row.get("trial_ends_at")),
        instant(row.get("current_period_start")),
        instant(row.get("current_period_end")),
        (Boolean) row.get("cancel_at_period_end"),
        (Integer) row.get("seats"),
        usage,
        stripeConfigured(plan));
  }

  @Transactional(readOnly = true)
  public List<SubscriptionSummary> summaries() {
    return jdbc.query(
        """
            select ts.organization_id, o.name, ts.plan_code, p.name as plan_name, ts.status,
                   ts.billing_interval, ts.billing_source, ts.trial_ends_at, ts.current_period_end,
                   ts.cancel_at_period_end, ts.activated_at,
                   ts.monthly_price_snapshot, ts.annual_price_snapshot, ts.currency_snapshot,
                   tp.status as provisioning_status, tp.last_error as provisioning_error
              from tenant_subscription ts
              join organization o on o.id = ts.organization_id
              join subscription_plan p on p.code = ts.plan_code
              left join tenant_provisioning tp on tp.organization_id = ts.organization_id
             order by o.name
            """,
        (rs, row) -> {
          BigDecimal monthlyPrice = rs.getBigDecimal("monthly_price_snapshot");
          BigDecimal annualPrice = rs.getBigDecimal("annual_price_snapshot");
          String interval = rs.getString("billing_interval");
          String status = rs.getString("status");
          return new SubscriptionSummary(
              rs.getObject("organization_id", UUID.class),
              rs.getString("name"),
              rs.getString("plan_code"),
              rs.getString("plan_name"),
              status,
              interval,
              rs.getString("billing_source"),
              rs.getTimestamp("trial_ends_at") == null
                  ? null
                  : rs.getTimestamp("trial_ends_at").toInstant(),
              rs.getTimestamp("current_period_end") == null
                  ? null
                  : rs.getTimestamp("current_period_end").toInstant(),
              rs.getBoolean("cancel_at_period_end"),
              rs.getTimestamp("activated_at") == null
                  ? null
                  : rs.getTimestamp("activated_at").toInstant(),
              mrrFor(status, interval, monthlyPrice, annualPrice),
              monthlyPrice,
              annualPrice,
              rs.getString("currency_snapshot"),
              rs.getString("provisioning_status"),
              rs.getString("provisioning_error"));
        });
  }

  @Transactional
  public void startTrial(
      UUID organizationId, String planCode, String adminEmail, String adminName) {
    PlanView plan = planByCode(planCode == null ? "GROWTH" : planCode);
    Instant now = clock.instant();
    jdbc.update(
        """
            insert into tenant_subscription
                (organization_id, plan_code, status, billing_interval, trial_ends_at,
                 billing_source, monthly_price_snapshot, annual_price_snapshot, currency_snapshot,
                 stripe_price_id, created_at, created_by, updated_at, updated_by)
            values (?, ?, 'TRIALING', 'MONTHLY', ?, 'TRIAL', ?, ?, ?, ?, ?, 'platform-admin', ?, 'platform-admin')
            on conflict (organization_id) do nothing
            """,
        organizationId,
        plan.code(),
        java.sql.Timestamp.from(now.plus(Duration.ofDays(plan.trialDays()))),
        plan.monthlyPrice(),
        plan.annualPrice(),
        plan.currency(),
        plan.stripePriceMonthly(),
        java.sql.Timestamp.from(now),
        java.sql.Timestamp.from(now));
    if (adminEmail != null && !adminEmail.isBlank()) {
      jdbc.update(
          """
              insert into tenant_provisioning
                  (organization_id, admin_email, admin_name, status, created_at, updated_at)
              values (?, ?, ?, 'PENDING', ?, ?)
              on conflict (organization_id) do nothing
              """,
          organizationId,
          adminEmail,
          adminName == null ? "" : adminName,
          java.sql.Timestamp.from(now),
          java.sql.Timestamp.from(now));
    }
  }

  @Transactional
  public CheckoutResult checkoutCurrent(String planCode, String interval) {
    return checkout(TenantContext.require(), planCode, interval);
  }

  @Transactional
  public CheckoutResult checkout(UUID organizationId, String planCode, String interval) {
    requireStripe();
    PlanView plan = planByCode(planCode);
    String normalizedInterval = normalizeInterval(interval);
    String priceId =
        normalizedInterval.equals("ANNUAL") ? plan.stripePriceAnnual() : plan.stripePriceMonthly();
    if (priceId == null || priceId.isBlank()) {
      throw new BusinessRuleException("Stripe price is not configured for this plan");
    }
    Map<String, Object> subscription = subscriptionRow(organizationId);
    String stripeSubscriptionId = (String) subscription.get("stripe_subscription_id");
    boolean activeStripeSubscription =
        stripeSubscriptionId != null
            && !stripeSubscriptionId.isBlank()
            && !"CANCELED".equals(subscription.get("status"));
    if (activeStripeSubscription) {
      updateStripeSubscription(
          organizationId, stripeSubscriptionId, plan, normalizedInterval, priceId);
      updatePlanTerms(organizationId, plan, normalizedInterval, priceId);
      jdbc.update(
          "update tenant_subscription set billing_source = 'STRIPE', last_stripe_sync_at = now(), updated_at = now() where organization_id = ?",
          organizationId);
      return new CheckoutResult(null, true);
    }
    String customerId = (String) subscription.get("stripe_customer_id");
    if (customerId == null || customerId.isBlank()) {
      customerId = createCustomer(organizationId);
      jdbc.update(
          "update tenant_subscription set stripe_customer_id = ?, updated_at = now() where organization_id = ?",
          customerId,
          organizationId);
    }
    Map<String, String> form = new java.util.LinkedHashMap<>();
    form.put("mode", "subscription");
    form.put("customer", customerId);
    form.put("client_reference_id", organizationId.toString());
    form.put("success_url", stripe.successUrl());
    form.put("cancel_url", stripe.cancelUrl());
    form.put("line_items[0][price]", priceId);
    form.put("line_items[0][quantity]", "1");
    form.put("subscription_data[metadata][organizationId]", organizationId.toString());
    form.put("subscription_data[metadata][planCode]", plan.code());
    form.put("subscription_data[metadata][billingInterval]", normalizedInterval);
    JsonNode session = post("/checkout/sessions", form);
    return new CheckoutResult(session.path("url").asText(), false);
  }

  @Transactional(readOnly = true)
  public ChangePreview previewChange(UUID organizationId, String planCode, String billingInterval) {
    PlanView plan = planByCode(planCode);
    String interval = normalizeInterval(billingInterval);
    Map<String, Object> row = subscriptionRow(organizationId);
    if (!isStripeBacked(row)) {
      BigDecimal next = interval.equals("ANNUAL") ? plan.annualPrice() : plan.monthlyPrice();
      return new ChangePreview(
          plan.code(),
          plan.name(),
          interval,
          (String) row.get("billing_source"),
          false,
          BigDecimal.ZERO,
          plan.currency(),
          null,
          next,
          List.of());
    }
    requireStripe();
    String priceId =
        interval.equals("ANNUAL") ? plan.stripePriceAnnual() : plan.stripePriceMonthly();
    if (priceId == null || priceId.isBlank()) {
      throw new BusinessRuleException("Stripe price is not configured for this plan");
    }
    String stripeSubscriptionId = (String) row.get("stripe_subscription_id");
    JsonNode stripeSubscription = get("/subscriptions/" + stripeSubscriptionId);
    JsonNode items = stripeSubscription.path("items").path("data");
    if (!items.isArray() || items.isEmpty()) {
      throw new BusinessRuleException("Stripe subscription has no billable item");
    }
    Map<String, String> query = new java.util.LinkedHashMap<>();
    query.put("subscription", stripeSubscriptionId);
    query.put("subscription_items[0][id]", items.get(0).path("id").asText());
    query.put("subscription_items[0][price]", priceId);
    query.put("subscription_proration_behavior", "create_prorations");
    JsonNode invoice = get("/invoices/upcoming", query);
    List<PreviewLine> lines = new ArrayList<>();
    for (JsonNode line : invoice.path("lines").path("data")) {
      lines.add(
          new PreviewLine(
              line.path("description").asText(""),
              cents(line.path("amount").asLong(0)),
              line.path("proration").asBoolean(false)));
    }
    BigDecimal next = interval.equals("ANNUAL") ? plan.annualPrice() : plan.monthlyPrice();
    return new ChangePreview(
        plan.code(),
        plan.name(),
        interval,
        "STRIPE",
        true,
        cents(invoice.path("amount_due").asLong(0)),
        invoice.path("currency").asText(plan.currency()).toUpperCase(),
        epoch(invoice.path("next_payment_attempt")),
        next,
        lines);
  }

  @Transactional
  public String portal() {
    requireStripe();
    UUID organizationId = TenantContext.require();
    Map<String, Object> subscription =
        jdbc
            .queryForList(
                "select * from tenant_subscription where organization_id = ?", organizationId)
            .stream()
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("No subscription is configured"));
    String customerId = (String) subscription.get("stripe_customer_id");
    if (customerId == null || customerId.isBlank()) {
      throw new BusinessRuleException("Start a subscription before opening the billing portal");
    }
    Map<String, String> form = new java.util.LinkedHashMap<>();
    form.put("customer", customerId);
    form.put("return_url", stripe.portalReturnUrl());
    return post("/billing_portal/sessions", form).path("url").asText();
  }

  @Transactional
  public void handleWebhook(String payload, String signature) {
    requireStripe();
    verifySignature(payload, signature);
    try {
      JsonNode event = objectMapper.readTree(payload);
      String eventId = event.path("id").asText();
      String eventType = event.path("type").asText();
      int inserted =
          jdbc.update(
              "insert into stripe_event (event_id, event_type, processed_at, payload) values (?, ?, ?, ?::jsonb) on conflict do nothing",
              eventId,
              eventType,
              java.sql.Timestamp.from(clock.instant()),
              payload);
      if (inserted == 0) return;
      JsonNode object = event.path("data").path("object");
      switch (eventType) {
        case "checkout.session.completed" -> applyCheckout(object);
        case "customer.subscription.created",
            "customer.subscription.updated",
            "customer.subscription.paused",
            "customer.subscription.resumed" ->
            applySubscription(object, "");
        case "customer.subscription.deleted" -> cancelByStripeId(object.path("id").asText());
        case "invoice.payment_failed", "invoice.payment_action_required" ->
            markPastDue(object.path("customer").asText());
        case "invoice.paid", "invoice.payment_succeeded" ->
            markPaid(object.path("customer").asText());
        case "checkout.session.expired" -> {
          // Checkout sessions carry no subscription yet; nothing local to change.
        }
        default -> {
          // Ignore unrelated Stripe events.
        }
      }
    } catch (BusinessRuleException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new IllegalStateException("Could not process Stripe webhook", ex);
    }
  }

  private void applyCheckout(JsonNode object) {
    String organizationId = object.path("client_reference_id").asText();
    if (organizationId.isBlank()) return;
    jdbc.update(
        """
            update tenant_subscription
               set stripe_customer_id = coalesce(nullif(?, ''), stripe_customer_id),
                   stripe_subscription_id = coalesce(nullif(?, ''), stripe_subscription_id),
                   last_stripe_sync_at = now(), updated_at = now()
             where organization_id = ?::uuid
            """,
        object.path("customer").asText(),
        object.path("subscription").asText(),
        organizationId);
  }

  private void applySubscription(JsonNode object, String fallbackOrganizationId) {
    String stripeSubscriptionId = object.path("id").asText();
    String organizationId =
        object.path("metadata").path("organizationId").asText(fallbackOrganizationId);
    String status = mapStatus(object.path("status").asText());
    String planCode = object.path("metadata").path("planCode").asText("GROWTH");
    String interval = object.path("metadata").path("billingInterval").asText("MONTHLY");
    Instant periodStart = epoch(object.path("current_period_start"));
    Instant periodEnd = epoch(object.path("current_period_end"));
    boolean cancel = object.path("cancel_at_period_end").asBoolean(false);
    String stripePriceId = "";
    JsonNode items = object.path("items").path("data");
    if (items.isArray() && !items.isEmpty()) {
      stripePriceId = items.get(0).path("price").path("id").asText("");
    }
    if (!organizationId.isBlank()) {
      PlanView plan = planByCode(planCode);
      jdbc.update(
          """
              update tenant_subscription
                 set plan_code = ?, status = ?, billing_interval = ?, stripe_subscription_id = ?,
                     stripe_customer_id = coalesce(nullif(?, ''), stripe_customer_id),
                     billing_source = 'STRIPE',
                     monthly_price_snapshot = ?, annual_price_snapshot = ?, currency_snapshot = ?,
                     stripe_price_id = coalesce(nullif(?, ''), stripe_price_id),
                     current_period_start = ?, current_period_end = ?, cancel_at_period_end = ?,
                     activated_at = case when ? = 'ACTIVE' then coalesce(activated_at, now())
                                         else activated_at end,
                     last_stripe_sync_at = now(), updated_at = now()
               where organization_id = ?::uuid
              """,
          planCode,
          status,
          interval,
          stripeSubscriptionId,
          object.path("customer").asText(),
          plan.monthlyPrice(),
          plan.annualPrice(),
          plan.currency(),
          stripePriceId,
          periodStart == null ? null : java.sql.Timestamp.from(periodStart),
          periodEnd == null ? null : java.sql.Timestamp.from(periodEnd),
          cancel,
          status,
          organizationId);
    } else {
      jdbc.update(
          """
              update tenant_subscription
                 set status = ?, current_period_start = ?, current_period_end = ?,
                     cancel_at_period_end = ?, billing_source = 'STRIPE',
                     stripe_price_id = coalesce(nullif(?, ''), stripe_price_id),
                     last_stripe_sync_at = now(), updated_at = now()
               where stripe_subscription_id = ?
              """,
          status,
          periodStart == null ? null : java.sql.Timestamp.from(periodStart),
          periodEnd == null ? null : java.sql.Timestamp.from(periodEnd),
          cancel,
          stripePriceId,
          stripeSubscriptionId);
    }
  }

  private void cancelByStripeId(String stripeSubscriptionId) {
    jdbc.update(
        "update tenant_subscription set status = 'CANCELED', last_stripe_sync_at = now(), updated_at = now() where stripe_subscription_id = ?",
        stripeSubscriptionId);
  }

  private void markPastDue(String customerId) {
    jdbc.update(
        "update tenant_subscription set status = 'PAST_DUE', last_stripe_sync_at = now(), updated_at = now() where stripe_customer_id = ?",
        customerId);
  }

  private void markPaid(String customerId) {
    jdbc.update(
        """
        update tenant_subscription
           set status = case when status in ('PAST_DUE', 'UNPAID') then 'ACTIVE' else status end,
               payment_failed_notified_at = null,
               last_stripe_sync_at = now(), updated_at = now()
         where stripe_customer_id = ?
        """,
        customerId);
  }

  private boolean isStripeBacked(Map<String, Object> row) {
    String source = (String) row.get("billing_source");
    String subscriptionId = (String) row.get("stripe_subscription_id");
    return "STRIPE".equals(source) && subscriptionId != null && !subscriptionId.isBlank();
  }

  private void updatePlanTerms(
      UUID organizationId, PlanView plan, String interval, String stripePriceId) {
    jdbc.update(
        """
        update tenant_subscription
           set plan_code = ?, billing_interval = ?, monthly_price_snapshot = ?,
               annual_price_snapshot = ?, currency_snapshot = ?, stripe_price_id = ?,
               updated_at = now()
         where organization_id = ?
        """,
        plan.code(),
        interval,
        plan.monthlyPrice(),
        plan.annualPrice(),
        plan.currency(),
        stripePriceId,
        organizationId);
  }

  private void updateStripeSubscription(
      UUID organizationId,
      String stripeSubscriptionId,
      PlanView plan,
      String interval,
      String priceId) {
    JsonNode existing = get("/subscriptions/" + stripeSubscriptionId);
    JsonNode items = existing.path("items").path("data");
    if (!items.isArray() || items.isEmpty()) {
      throw new BusinessRuleException("Stripe subscription has no billable item");
    }
    Map<String, String> form = new java.util.LinkedHashMap<>();
    form.put("items[0][id]", items.get(0).path("id").asText());
    form.put("items[0][price]", priceId);
    form.put("proration_behavior", "create_prorations");
    form.put("metadata[organizationId]", organizationId.toString());
    form.put("metadata[planCode]", plan.code());
    form.put("metadata[billingInterval]", interval);
    post("/subscriptions/" + stripeSubscriptionId, form);
  }

  private JsonNode get(String path) {
    return get(path, Map.of());
  }

  private JsonNode get(String path, Map<String, String> query) {
    try {
      String suffix = query == null || query.isEmpty() ? "" : "?" + encodeForm(query);
      HttpRequest request =
          HttpRequest.newBuilder(URI.create(STRIPE_API + path + suffix))
              .timeout(Duration.ofSeconds(20))
              .header("Authorization", "Bearer " + stripe.secretKey())
              .GET()
              .build();
      HttpResponse<String> response =
          http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      JsonNode node = objectMapper.readTree(response.body());
      if (response.statusCode() >= 400) {
        throw new BusinessRuleException(
            "Stripe request failed: " + node.path("error").path("message").asText("unknown error"));
      }
      return node;
    } catch (BusinessRuleException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new IllegalStateException("Could not reach Stripe", ex);
    }
  }

  private JsonNode delete(String path) {
    try {
      HttpRequest request =
          HttpRequest.newBuilder(URI.create(STRIPE_API + path))
              .timeout(Duration.ofSeconds(20))
              .header("Authorization", "Bearer " + stripe.secretKey())
              .DELETE()
              .build();
      HttpResponse<String> response =
          http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      JsonNode node = objectMapper.readTree(response.body());
      if (response.statusCode() >= 400) {
        throw new BusinessRuleException(
            "Stripe request failed: " + node.path("error").path("message").asText("unknown error"));
      }
      return node;
    } catch (BusinessRuleException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new IllegalStateException("Could not reach Stripe", ex);
    }
  }

  private String createCustomer(UUID organizationId) {
    List<Map<String, Object>> rows =
        jdbc.queryForList("select name from organization where id = ?", organizationId);
    String name = rows.isEmpty() ? "Tenant" : (String) rows.get(0).get("name");
    Map<String, String> form = new java.util.LinkedHashMap<>();
    form.put("name", name);
    form.put("metadata[organizationId]", organizationId.toString());
    return post("/customers", form).path("id").asText();
  }

  private JsonNode post(String path, Map<String, String> form) {
    try {
      String body = encodeForm(form);
      HttpRequest request =
          HttpRequest.newBuilder(URI.create(STRIPE_API + path))
              .timeout(Duration.ofSeconds(20))
              .header("Authorization", "Bearer " + stripe.secretKey())
              .header("Content-Type", "application/x-www-form-urlencoded")
              .POST(HttpRequest.BodyPublishers.ofString(body))
              .build();
      HttpResponse<String> response =
          http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      JsonNode node = objectMapper.readTree(response.body());
      if (response.statusCode() >= 400) {
        throw new BusinessRuleException(
            "Stripe request failed: " + node.path("error").path("message").asText("unknown error"));
      }
      return node;
    } catch (BusinessRuleException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new IllegalStateException("Could not reach Stripe", ex);
    }
  }

  private static String encodeForm(Map<String, String> form) {
    return form.entrySet().stream()
        .map(
            entry ->
                URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)
                    + "="
                    + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
        .collect(java.util.stream.Collectors.joining("&"));
  }

  private static BigDecimal cents(long value) {
    return BigDecimal.valueOf(value, 2);
  }

  private void verifySignature(String payload, String signature) {
    if (signature == null || signature.isBlank()) {
      throw new BusinessRuleException("Stripe signature is required");
    }
    String timestamp = null;
    List<String> signatures = new ArrayList<>();
    for (String part : signature.split(",")) {
      String[] pair = part.split("=", 2);
      if (pair.length != 2) continue;
      if ("t".equals(pair[0])) timestamp = pair[1];
      if ("v1".equals(pair[0])) signatures.add(pair[1]);
    }
    if (timestamp == null || signatures.isEmpty()) {
      throw new BusinessRuleException("Stripe signature is invalid");
    }
    long age = Math.abs(clock.instant().getEpochSecond() - Long.parseLong(timestamp));
    if (age > 300) {
      throw new BusinessRuleException("Stripe signature has expired");
    }
    String expected = hmac(stripe.webhookSecret(), timestamp + "." + payload);
    boolean valid =
        signatures.stream()
            .anyMatch(
                candidate ->
                    MessageDigest.isEqual(
                        expected.getBytes(StandardCharsets.UTF_8),
                        candidate.getBytes(StandardCharsets.UTF_8)));
    if (!valid) {
      throw new BusinessRuleException("Stripe signature is invalid");
    }
  }

  private static String hmac(String secret, String payload) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception ex) {
      throw new IllegalStateException("Could not verify Stripe signature", ex);
    }
  }

  @Transactional(readOnly = true)
  public SubscriptionSummary summary(UUID organizationId) {
    return summaries().stream()
        .filter(summary -> summary.organizationId().equals(organizationId))
        .findFirst()
        .orElseThrow(
            () -> new ResourceNotFoundException("No subscription is configured for this tenant"));
  }

  @Transactional
  public BillingMetrics billingMetrics() {
    Instant now = clock.instant();
    localDateSnapshot(LocalDate.now(clock));
    List<SubscriptionSummary> rows = summaries();
    BigDecimal activeMrr =
        rows.stream()
            .filter(row -> "ACTIVE".equals(row.status()))
            .map(SubscriptionSummary::mrr)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal atRiskMrr =
        rows.stream()
            .filter(row -> "PAST_DUE".equals(row.status()) || "UNPAID".equals(row.status()))
            .map(SubscriptionSummary::mrr)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal trialPipelineMrr =
        rows.stream()
            .filter(row -> "TRIALING".equals(row.status()))
            .map(row -> planMrr(row.billingInterval(), row.monthlyPrice(), row.annualPrice()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    long payingTenants = rows.stream().filter(row -> "ACTIVE".equals(row.status())).count();
    long trialingTenants = rows.stream().filter(row -> "TRIALING".equals(row.status())).count();
    long pastDueTenants =
        rows.stream()
            .filter(row -> "PAST_DUE".equals(row.status()) || "UNPAID".equals(row.status()))
            .count();
    long canceledTenants = rows.stream().filter(row -> "CANCELED".equals(row.status())).count();
    long newPayingTenants30d =
        rows.stream()
            .filter(row -> "ACTIVE".equals(row.status()))
            .filter(
                row ->
                    row.activatedAt() != null
                        && !row.activatedAt().isBefore(now.minus(Duration.ofDays(30))))
            .count();

    Map<String, PlanRevenue> planRevenue = new LinkedHashMap<>();
    for (SubscriptionSummary row : rows) {
      if (!"ACTIVE".equals(row.status())) {
        continue;
      }
      planRevenue.compute(
          row.planCode(),
          (code, current) ->
              current == null
                  ? new PlanRevenue(code, row.planName(), 1, row.mrr(), row.currency())
                  : new PlanRevenue(
                      code,
                      row.planName(),
                      current.tenants() + 1,
                      current.mrr().add(row.mrr()),
                      row.currency()));
    }

    List<SnapshotPoint> trend =
        jdbc.query(
            """
            select snapshot_date, mrr, arr, paying_tenants, trialing_tenants,
                   past_due_tenants, canceled_tenants
              from subscription_mrr_snapshot
             order by snapshot_date
            """,
            (rs, row) ->
                new SnapshotPoint(
                    rs.getObject("snapshot_date", LocalDate.class),
                    rs.getBigDecimal("mrr"),
                    rs.getBigDecimal("arr"),
                    rs.getInt("paying_tenants"),
                    rs.getInt("trialing_tenants"),
                    rs.getInt("past_due_tenants"),
                    rs.getInt("canceled_tenants")));

    SnapshotPoint baseline =
        trend.stream()
            .filter(point -> !point.date().isAfter(LocalDate.now(clock).minusDays(30)))
            .max(Comparator.comparing(SnapshotPoint::date))
            .orElse(null);
    BigDecimal change30d = baseline == null ? null : activeMrr.subtract(baseline.mrr());
    BigDecimal changePercent30d =
        baseline == null || baseline.mrr().signum() == 0
            ? null
            : change30d
                .multiply(BigDecimal.valueOf(100))
                .divide(baseline.mrr(), 1, java.math.RoundingMode.HALF_UP);

    List<SubscriptionSummary> renewals =
        rows.stream()
            .filter(row -> "ACTIVE".equals(row.status()))
            .filter(row -> row.currentPeriodEnd() != null)
            .filter(row -> !row.currentPeriodEnd().isAfter(now.plus(Duration.ofDays(30))))
            .sorted(Comparator.comparing(SubscriptionSummary::currentPeriodEnd))
            .toList();

    return new BillingMetrics(
        now,
        activeMrr,
        activeMrr.multiply(BigDecimal.valueOf(12)),
        payingTenants == 0
            ? BigDecimal.ZERO
            : activeMrr.divide(
                BigDecimal.valueOf(payingTenants), 2, java.math.RoundingMode.HALF_UP),
        atRiskMrr,
        trialPipelineMrr,
        change30d,
        changePercent30d,
        payingTenants,
        trialingTenants,
        pastDueTenants,
        canceledTenants,
        newPayingTenants30d,
        planRevenue.values().stream().toList(),
        trend,
        renewals,
        rows);
  }

  @Transactional
  public void recordMrrSnapshot() {
    localDateSnapshot(LocalDate.now(clock));
  }

  @Transactional
  public SubscriptionView changePlan(UUID organizationId, String planCode, String billingInterval) {
    PlanView plan = planByCode(planCode);
    String interval = normalizeInterval(billingInterval);
    Map<String, Object> row = subscriptionRow(organizationId);
    String priceId =
        interval.equals("ANNUAL") ? plan.stripePriceAnnual() : plan.stripePriceMonthly();
    if (isStripeBacked(row)) {
      if (priceId == null || priceId.isBlank()) {
        throw new BusinessRuleException("Stripe price is not configured for this plan");
      }
      updateStripeSubscription(
          organizationId, (String) row.get("stripe_subscription_id"), plan, interval, priceId);
    }
    updatePlanTerms(organizationId, plan, interval, priceId);
    if (isStripeBacked(row)) {
      jdbc.update(
          "update tenant_subscription set last_stripe_sync_at = now(), updated_at = now() where organization_id = ?",
          organizationId);
    }
    return forTenant(organizationId, false);
  }

  @Transactional
  public SubscriptionView extendTrial(UUID organizationId, int days) {
    if (days < 1 || days > 365) {
      throw new BusinessRuleException("Trial extension must be between 1 and 365 days");
    }
    Map<String, Object> row = subscriptionRow(organizationId);
    if (!"TRIALING".equals(row.get("status"))) {
      throw new BusinessRuleException("Only a trialing subscription can be extended");
    }
    if (isStripeBacked(row)) {
      throw new BusinessRuleException(
          "This trial is managed by Stripe; extend it from the Stripe dashboard");
    }
    jdbc.update(
        """
            update tenant_subscription
               set trial_ends_at = greatest(coalesce(trial_ends_at, now()), now())
                                   + (? * interval '1 day'),
                   updated_at = now()
             where organization_id = ?
            """,
        days,
        organizationId);
    return forTenant(organizationId, false);
  }

  @Transactional
  public SubscriptionView activate(UUID organizationId) {
    Map<String, Object> row = subscriptionRow(organizationId);
    if (isStripeBacked(row)) {
      throw new BusinessRuleException(
          "This subscription is billed by Stripe; activation must come from Stripe");
    }
    String interval = normalizeInterval((String) row.get("billing_interval"));
    Instant now = clock.instant();
    Instant periodEnd =
        now.plus(interval.equals("ANNUAL") ? Duration.ofDays(365) : Duration.ofDays(30));
    jdbc.update(
        """
            update tenant_subscription
               set status = 'ACTIVE', billing_source = 'MANUAL', trial_ends_at = null,
                   cancel_at_period_end = false, activated_at = coalesce(activated_at, now()),
                   current_period_start = ?, current_period_end = ?, updated_at = now()
             where organization_id = ?
            """,
        java.sql.Timestamp.from(now),
        java.sql.Timestamp.from(periodEnd),
        organizationId);
    return forTenant(organizationId, false);
  }

  @Transactional
  public SubscriptionView cancel(UUID organizationId, boolean atPeriodEnd) {
    Map<String, Object> row = subscriptionRow(organizationId);
    if (isStripeBacked(row)) {
      String stripeSubscriptionId = (String) row.get("stripe_subscription_id");
      if (atPeriodEnd) {
        Map<String, String> form = new java.util.LinkedHashMap<>();
        form.put("cancel_at_period_end", "true");
        post("/subscriptions/" + stripeSubscriptionId, form);
      } else {
        delete("/subscriptions/" + stripeSubscriptionId);
      }
    }
    if (atPeriodEnd) {
      jdbc.update(
          "update tenant_subscription set cancel_at_period_end = true, last_stripe_sync_at = case when billing_source = 'STRIPE' then now() else last_stripe_sync_at end, updated_at = now() where organization_id = ?",
          organizationId);
    } else {
      jdbc.update(
          """
              update tenant_subscription
                 set status = 'CANCELED', cancel_at_period_end = false, updated_at = now()
               where organization_id = ?
              """,
          organizationId);
    }
    return forTenant(organizationId, false);
  }

  @Transactional
  public SubscriptionView reactivate(UUID organizationId) {
    Map<String, Object> row = subscriptionRow(organizationId);
    if (isStripeBacked(row)) {
      if ("CANCELED".equals(row.get("status"))) {
        throw new BusinessRuleException(
            "This Stripe subscription has ended and cannot be reactivated");
      }
      Map<String, String> form = new java.util.LinkedHashMap<>();
      form.put("cancel_at_period_end", "false");
      post("/subscriptions/" + row.get("stripe_subscription_id"), form);
      jdbc.update(
          "update tenant_subscription set cancel_at_period_end = false, last_stripe_sync_at = now(), updated_at = now() where organization_id = ?",
          organizationId);
      return forTenant(organizationId, false);
    }
    return activate(organizationId);
  }

  @Transactional(readOnly = true)
  public ReconciliationReport reconciliationAudit() {
    List<SubscriptionSummary> stripeRows =
        summaries().stream().filter(row -> "STRIPE".equals(row.billingSource())).toList();
    List<ReconciliationIssue> issues = new ArrayList<>();
    for (SubscriptionSummary summary : stripeRows) {
      Map<String, Object> local = subscriptionRow(summary.organizationId());
      String stripeSubscriptionId = (String) local.get("stripe_subscription_id");
      if (stripeSubscriptionId == null || stripeSubscriptionId.isBlank()) {
        issues.add(
            new ReconciliationIssue(
                summary.organizationId(),
                summary.organizationName(),
                null,
                List.of("Missing Stripe subscription ID"),
                null));
        continue;
      }
      try {
        JsonNode stripeSubscription = get("/subscriptions/" + stripeSubscriptionId);
        List<String> differences = new ArrayList<>();
        String expectedPlan = stripeSubscription.path("metadata").path("planCode").asText("");
        String expectedInterval =
            stripeSubscription.path("metadata").path("billingInterval").asText("");
        String expectedStatus = mapStatus(stripeSubscription.path("status").asText());
        String expectedPriceId = "";
        JsonNode items = stripeSubscription.path("items").path("data");
        if (items.isArray() && !items.isEmpty()) {
          expectedPriceId = items.get(0).path("price").path("id").asText("");
        }
        if (!expectedPlan.isBlank() && !expectedPlan.equals(summary.planCode())) {
          differences.add("planCode " + summary.planCode() + " -> " + expectedPlan);
        }
        if (!expectedInterval.isBlank() && !expectedInterval.equals(summary.billingInterval())) {
          differences.add(
              "billingInterval " + summary.billingInterval() + " -> " + expectedInterval);
        }
        if (!expectedStatus.equals(summary.status())) {
          differences.add("status " + summary.status() + " -> " + expectedStatus);
        }
        String localPriceId = (String) local.get("stripe_price_id");
        if (!expectedPriceId.isBlank()
            && !expectedPriceId.equals(localPriceId == null ? "" : localPriceId)) {
          differences.add("stripePriceId " + localPriceId + " -> " + expectedPriceId);
        }
        Instant expectedPeriodEnd = epoch(stripeSubscription.path("current_period_end"));
        Instant localPeriodEnd = summary.currentPeriodEnd();
        if (expectedPeriodEnd != null
            && localPeriodEnd != null
            && Math.abs(Duration.between(expectedPeriodEnd, localPeriodEnd).toMinutes()) > 1) {
          differences.add("currentPeriodEnd " + localPeriodEnd + " -> " + expectedPeriodEnd);
        }
        if (!differences.isEmpty()) {
          issues.add(
              new ReconciliationIssue(
                  summary.organizationId(),
                  summary.organizationName(),
                  stripeSubscriptionId,
                  differences,
                  null));
        }
      } catch (RuntimeException ex) {
        issues.add(
            new ReconciliationIssue(
                summary.organizationId(),
                summary.organizationName(),
                stripeSubscriptionId,
                List.of(),
                ex.getMessage()));
      }
    }
    return new ReconciliationReport(clock.instant(), stripeRows.size(), issues.size(), issues);
  }

  @Transactional
  public SubscriptionView reconcile(UUID organizationId) {
    requireStripe();
    Map<String, Object> row = subscriptionRow(organizationId);
    if (!isStripeBacked(row)) {
      throw new BusinessRuleException("Only Stripe-backed subscriptions can be reconciled");
    }
    JsonNode stripeSubscription = get("/subscriptions/" + row.get("stripe_subscription_id"));
    applySubscription(stripeSubscription, organizationId.toString());
    return forTenant(organizationId, false);
  }

  private void localDateSnapshot(LocalDate date) {
    BillingSummary summary = currentBillingSummary();
    jdbc.update(
        """
            insert into subscription_mrr_snapshot
                (snapshot_date, mrr, arr, paying_tenants, trialing_tenants,
                 past_due_tenants, canceled_tenants, created_at, created_by)
            values (?, ?, ?, ?, ?, ?, ?, now(), 'system')
            on conflict (snapshot_date) do update
               set mrr = excluded.mrr,
                   arr = excluded.arr,
                   paying_tenants = excluded.paying_tenants,
                   trialing_tenants = excluded.trialing_tenants,
                   past_due_tenants = excluded.past_due_tenants,
                   canceled_tenants = excluded.canceled_tenants
            """,
        date,
        summary.mrr(),
        summary.arr(),
        summary.payingTenants(),
        summary.trialingTenants(),
        summary.pastDueTenants(),
        summary.canceledTenants());
  }

  private BillingSummary currentBillingSummary() {
    Map<String, Object> row =
        jdbc.queryForMap(
            """
            select coalesce(sum(case when ts.status = 'ACTIVE' then
                       case when ts.billing_interval = 'ANNUAL'
                            then ts.annual_price_snapshot / 12
                            else ts.monthly_price_snapshot end
                     else 0 end), 0) as mrr,
                   count(*) filter (where ts.status = 'ACTIVE') as paying,
                   count(*) filter (where ts.status = 'TRIALING') as trialing,
                   count(*) filter (where ts.status in ('PAST_DUE','UNPAID')) as past_due,
                   count(*) filter (where ts.status = 'CANCELED') as canceled
              from tenant_subscription ts
            """);
    BigDecimal mrr = decimal(row.get("mrr"));
    return new BillingSummary(
        mrr,
        mrr.multiply(BigDecimal.valueOf(12)),
        longValue(row.get("paying")),
        longValue(row.get("trialing")),
        longValue(row.get("past_due")),
        longValue(row.get("canceled")));
  }

  private Map<String, Object> subscriptionRow(UUID organizationId) {
    return jdbc
        .queryForList("select * from tenant_subscription where organization_id = ?", organizationId)
        .stream()
        .findFirst()
        .orElseThrow(
            () -> new ResourceNotFoundException("No subscription is configured for this tenant"));
  }

  private void updateSubscriptionRow(String sql, Object... args) {
    int updated = jdbc.update(sql, args);
    if (updated == 0) {
      throw new ResourceNotFoundException("No subscription is configured for this tenant");
    }
  }

  private static String normalizeInterval(String interval) {
    return "ANNUAL".equalsIgnoreCase(interval) ? "ANNUAL" : "MONTHLY";
  }

  private static BigDecimal mrrFor(
      String status, String interval, BigDecimal monthly, BigDecimal annual) {
    if (!"ACTIVE".equals(status) && !"PAST_DUE".equals(status) && !"UNPAID".equals(status)) {
      return BigDecimal.ZERO;
    }
    return planMrr(interval, monthly, annual);
  }

  private static BigDecimal planMrr(String interval, BigDecimal monthly, BigDecimal annual) {
    return "ANNUAL".equals(interval)
        ? annual.divide(BigDecimal.valueOf(12), 4, java.math.RoundingMode.HALF_UP)
        : monthly;
  }

  private static BigDecimal decimal(Object value) {
    if (value instanceof BigDecimal decimal) {
      return decimal;
    }
    return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString());
  }

  private static long longValue(Object value) {
    return value instanceof Number number ? number.longValue() : 0L;
  }

  private PlanView planByCode(String code) {
    return loadPlans("select * from subscription_plan where code = ?", code).stream()
        .findFirst()
        .orElseThrow(
            () -> new ResourceNotFoundException("Subscription plan %s not found".formatted(code)));
  }

  private PlanView plan(
      java.sql.ResultSet rs, List<SubscriptionFeatureService.FeatureView> features)
      throws java.sql.SQLException {
    return new PlanView(
        rs.getString("code"),
        rs.getString("name"),
        rs.getString("description"),
        rs.getBigDecimal("monthly_price"),
        rs.getBigDecimal("annual_price"),
        rs.getString("currency"),
        rs.getInt("trial_days"),
        (Integer) rs.getObject("max_users"),
        (Integer) rs.getObject("max_departments"),
        (Integer) rs.getObject("max_requests_month"),
        (Integer) rs.getObject("max_storage_mb"),
        features,
        rs.getString("stripe_price_monthly"),
        rs.getString("stripe_price_annual"),
        rs.getBoolean("active"),
        rs.getInt("sort_order"));
  }

  private Usage usage(UUID organizationId) {
    long users =
        count(
            "select count(*) from user_profile where organization_id = ? and deleted_at is null and active = true",
            organizationId);
    long departments =
        count(
            "select count(*) from department where organization_id = ? and deleted_at is null",
            organizationId);
    long requests =
        count(
            "select count(*) from purchase_request where organization_id = ? and date_trunc('month', created_at) = date_trunc('month', now())",
            organizationId);
    Long storage =
        jdbc.queryForObject(
            "select coalesce(sum(size_bytes), 0) from request_attachment where organization_id = ?",
            Long.class,
            organizationId);
    return new Usage(users, departments, requests, (storage == null ? 0 : storage) / (1024 * 1024));
  }

  private long count(String sql, Object... args) {
    Long value = jdbc.queryForObject(sql, Long.class, args);
    return value == null ? 0 : value;
  }

  private boolean stripeConfigured(PlanView plan) {
    return stripe.configured()
        && plan.stripePriceMonthly() != null
        && !plan.stripePriceMonthly().isBlank();
  }

  private void requireStripe() {
    if (!stripe.configured()) {
      throw new BusinessRuleException("Stripe is not configured");
    }
  }

  private static Instant instant(Object value) {
    return value instanceof java.sql.Timestamp timestamp ? timestamp.toInstant() : null;
  }

  private static Instant epoch(JsonNode node) {
    long seconds = node.asLong(0);
    return seconds > 0 ? Instant.ofEpochSecond(seconds) : null;
  }

  private static String mapStatus(String status) {
    return switch (status) {
      case "trialing" -> "TRIALING";
      case "active" -> "ACTIVE";
      case "past_due", "paused" -> "PAST_DUE";
      case "unpaid" -> "UNPAID";
      case "canceled" -> "CANCELED";
      case "incomplete", "incomplete_expired" -> "INCOMPLETE";
      default -> "ACTIVE";
    };
  }

  public record PlanView(
      String code,
      String name,
      String description,
      BigDecimal monthlyPrice,
      BigDecimal annualPrice,
      String currency,
      int trialDays,
      Integer maxUsers,
      Integer maxDepartments,
      Integer maxRequestsMonth,
      Integer maxStorageMb,
      List<SubscriptionFeatureService.FeatureView> features,
      String stripePriceMonthly,
      String stripePriceAnnual,
      boolean active,
      int sortOrder) {}

  public record PlanCommand(
      String code,
      String name,
      String description,
      BigDecimal monthlyPrice,
      BigDecimal annualPrice,
      String currency,
      int trialDays,
      Integer maxUsers,
      Integer maxDepartments,
      Integer maxRequestsMonth,
      Integer maxStorageMb,
      List<String> featureCodes,
      boolean active,
      int sortOrder,
      String stripePriceMonthly,
      String stripePriceAnnual) {}

  public record CheckoutResult(String url, boolean updated) {}

  public record ReconciliationReport(
      Instant generatedAt, int checked, int mismatched, List<ReconciliationIssue> issues) {}

  public record ReconciliationIssue(
      UUID organizationId,
      String organizationName,
      String stripeSubscriptionId,
      List<String> differences,
      String error) {}

  public record ChangePreview(
      String planCode,
      String planName,
      String billingInterval,
      String billingSource,
      boolean proration,
      BigDecimal amountDue,
      String currency,
      Instant nextPaymentAt,
      BigDecimal nextBillingAmount,
      List<PreviewLine> lines) {}

  public record PreviewLine(String description, BigDecimal amount, boolean proration) {}

  public record Usage(long users, long departments, long requestsThisMonth, long storageMb) {}

  public record SubscriptionView(
      UUID organizationId,
      PlanView plan,
      String status,
      String billingInterval,
      String billingSource,
      Instant trialEndsAt,
      Instant currentPeriodStart,
      Instant currentPeriodEnd,
      boolean cancelAtPeriodEnd,
      Integer seats,
      Usage usage,
      boolean checkoutAvailable) {}

  public record SubscriptionSummary(
      UUID organizationId,
      String organizationName,
      String planCode,
      String planName,
      String status,
      String billingInterval,
      String billingSource,
      Instant trialEndsAt,
      Instant currentPeriodEnd,
      boolean cancelAtPeriodEnd,
      Instant activatedAt,
      BigDecimal mrr,
      BigDecimal monthlyPrice,
      BigDecimal annualPrice,
      String currency,
      String provisioningStatus,
      String provisioningError) {}

  public record BillingMetrics(
      Instant generatedAt,
      BigDecimal mrr,
      BigDecimal arr,
      BigDecimal arpu,
      BigDecimal atRiskMrr,
      BigDecimal trialPipelineMrr,
      BigDecimal mrrChange30d,
      BigDecimal mrrChangePercent30d,
      long payingTenants,
      long trialingTenants,
      long pastDueTenants,
      long canceledTenants,
      long newPayingTenants30d,
      List<PlanRevenue> planRevenue,
      List<SnapshotPoint> mrrTrend,
      List<SubscriptionSummary> renewals,
      List<SubscriptionSummary> tenants) {}

  public record PlanRevenue(
      String planCode, String planName, long tenants, BigDecimal mrr, String currency) {}

  public record SnapshotPoint(
      LocalDate date,
      BigDecimal mrr,
      BigDecimal arr,
      int payingTenants,
      int trialingTenants,
      int pastDueTenants,
      int canceledTenants) {}

  private record BillingSummary(
      BigDecimal mrr,
      BigDecimal arr,
      long payingTenants,
      long trialingTenants,
      long pastDueTenants,
      long canceledTenants) {}
}
