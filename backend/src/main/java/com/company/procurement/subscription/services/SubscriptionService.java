package com.company.procurement.subscription.services;

import com.company.procurement.shared.config.StripeProperties;
import com.company.procurement.shared.error.BusinessRuleException;
import com.company.procurement.shared.error.ResourceNotFoundException;
import com.company.procurement.shared.tenant.OrganizationNamePort;
import com.company.procurement.shared.tenant.TenantContext;
import com.company.procurement.shared.tenant.TenantProvisioningPort;
import com.company.procurement.shared.usage.TenantUsagePort;
import com.company.procurement.subscription.domain.SubscriptionMrrSnapshot;
import com.company.procurement.subscription.domain.SubscriptionPlan;
import com.company.procurement.subscription.domain.TenantSubscription;
import com.company.procurement.subscription.repositories.StripeEventRepository;
import com.company.procurement.subscription.repositories.SubscriptionMrrSnapshotRepository;
import com.company.procurement.subscription.repositories.SubscriptionPlanFeatureRepository;
import com.company.procurement.subscription.repositories.SubscriptionPlanRepository;
import com.company.procurement.subscription.repositories.TenantSubscriptionRepository;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Manages subscriptions, Stripe synchronization and billing analytics. */
@Service
public class SubscriptionService {

  private static final String STRIPE_API = "https://api.stripe.com/v1";

  private final SubscriptionPlanRepository plans;
  private final SubscriptionFeatureService featureService;
  private final SubscriptionPlanFeatureRepository planFeatures;
  private final TenantSubscriptionRepository subscriptions;
  private final SubscriptionMrrSnapshotRepository snapshots;
  private final StripeEventRepository stripeEvents;
  private final OrganizationNamePort organizationNames;
  private final TenantProvisioningPort provisionings;
  private final TenantUsagePort usage;
  private final ObjectMapper objectMapper;
  private final StripeProperties stripe;
  private final Clock clock;
  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  public SubscriptionService(
      SubscriptionPlanRepository plans,
      SubscriptionFeatureService featureService,
      SubscriptionPlanFeatureRepository planFeatures,
      TenantSubscriptionRepository subscriptions,
      SubscriptionMrrSnapshotRepository snapshots,
      StripeEventRepository stripeEvents,
      OrganizationNamePort organizationNames,
      TenantProvisioningPort provisionings,
      TenantUsagePort usage,
      ObjectMapper objectMapper,
      StripeProperties stripe,
      Clock clock) {
    this.plans = plans;
    this.featureService = featureService;
    this.planFeatures = planFeatures;
    this.subscriptions = subscriptions;
    this.snapshots = snapshots;
    this.stripeEvents = stripeEvents;
    this.organizationNames = organizationNames;
    this.provisionings = provisionings;
    this.usage = usage;
    this.objectMapper = objectMapper;
    this.stripe = stripe;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public List<PlanView> plans() {
    return views(plans.findActiveOrdered());
  }

  @Transactional(readOnly = true)
  public List<PlanView> allPlans() {
    return views(plans.findAllOrdered());
  }

  @Transactional
  public PlanView createPlan(PlanCommand command) {
    String code = normalizePlanCode(command.code());
    if (plans.existsById(code)) {
      throw new BusinessRuleException("Subscription plan %s already exists".formatted(code));
    }
    validatePlan(command);
    List<SubscriptionFeatureService.FeatureView> features =
        featureService.resolveForAssignment(command.featureCodes(), Set.of());
    SubscriptionPlan plan =
        plans.save(
            new SubscriptionPlan(
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
                blankToNull(command.stripePriceAnnual())));
    replacePlanFeatures(plan.getCode(), features);
    return planByCode(plan.getCode());
  }

  @Transactional
  public PlanView updatePlan(String code, PlanCommand command) {
    SubscriptionPlan plan = requirePlan(code);
    validatePlan(command);
    Set<String> currentlyAssigned =
        planFeatures.findFeaturesForPlans(List.of(plan.getCode())).stream()
            .map(SubscriptionPlanFeatureRepository.PlanFeatureRow::code)
            .collect(java.util.stream.Collectors.toSet());
    List<SubscriptionFeatureService.FeatureView> features =
        featureService.resolveForAssignment(command.featureCodes(), currentlyAssigned);
    plan.update(
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
    plans.save(plan);
    replacePlanFeatures(plan.getCode(), features);
    return planByCode(plan.getCode());
  }

  @Transactional
  public void deactivatePlan(String code) {
    SubscriptionPlan plan = requirePlan(code);
    plan.deactivate();
    plans.save(plan);
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

  private List<PlanView> views(List<SubscriptionPlan> entities) {
    if (entities.isEmpty()) return List.of();
    Map<String, List<SubscriptionFeatureService.FeatureView>> features =
        featureService.featuresForPlans(entities.stream().map(SubscriptionPlan::getCode).toList());
    return entities.stream()
        .map(plan -> planView(plan, features.getOrDefault(plan.getCode(), List.of())))
        .toList();
  }

  private void replacePlanFeatures(
      String planCode, List<SubscriptionFeatureService.FeatureView> features) {
    planFeatures.deleteByPlanCode(planCode);
    if (!features.isEmpty()) {
      planFeatures.saveAll(
          features.stream()
              .map(
                  feature ->
                      new com.company.procurement.subscription.domain.SubscriptionPlanFeature(
                          planCode, feature.code()))
              .toList());
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
    return subscriptions.featureCodes(TenantContext.require());
  }

  @Transactional(readOnly = true)
  public SubscriptionView forTenant(UUID organizationId, boolean includeUsage) {
    TenantSubscription subscription = requireSubscription(organizationId);
    PlanView plan = planByCode(subscription.getPlanCode());
    Usage tenantUsage = includeUsage ? usage(organizationId) : null;
    return new SubscriptionView(
        organizationId,
        plan,
        subscription.getStatus(),
        subscription.getBillingInterval(),
        subscription.getBillingSource(),
        subscription.getTrialEndsAt(),
        subscription.getCurrentPeriodStart(),
        subscription.getCurrentPeriodEnd(),
        subscription.isCancelAtPeriodEnd(),
        subscription.getSeats(),
        tenantUsage,
        stripeConfigured(plan));
  }

  @Transactional(readOnly = true)
  public List<SubscriptionSummary> summaries() {
    List<TenantSubscription> all = subscriptions.findAll();
    if (all.isEmpty()) return List.of();
    List<UUID> organizationIds = all.stream().map(TenantSubscription::getOrganizationId).toList();
    Map<UUID, String> organizationNamesById = organizationNames.names(organizationIds);
    Map<UUID, TenantProvisioningPort.ProvisioningState> provisioningStates =
        provisionings.states(organizationIds);
    Map<String, SubscriptionPlan> plansByCode =
        plans.findAllOrdered().stream()
            .collect(java.util.stream.Collectors.toMap(SubscriptionPlan::getCode, plan -> plan));
    return all.stream()
        .map(
            subscription -> {
              SubscriptionPlan plan = plansByCode.get(subscription.getPlanCode());
              TenantProvisioningPort.ProvisioningState provisioning =
                  provisioningStates.get(subscription.getOrganizationId());
              String organizationName =
                  organizationNamesById.getOrDefault(
                      subscription.getOrganizationId(),
                      subscription.getOrganizationId().toString());
              BigDecimal monthly = subscription.getMonthlyPriceSnapshot();
              BigDecimal annual = subscription.getAnnualPriceSnapshot();
              return new SubscriptionSummary(
                  subscription.getOrganizationId(),
                  organizationName,
                  subscription.getPlanCode(),
                  plan == null ? subscription.getPlanCode() : plan.getName(),
                  subscription.getStatus(),
                  subscription.getBillingInterval(),
                  subscription.getBillingSource(),
                  subscription.getTrialEndsAt(),
                  subscription.getCurrentPeriodEnd(),
                  subscription.isCancelAtPeriodEnd(),
                  subscription.getActivatedAt(),
                  mrrFor(
                      subscription.getStatus(), subscription.getBillingInterval(), monthly, annual),
                  monthly,
                  annual,
                  subscription.getCurrencySnapshot(),
                  provisioning == null ? null : provisioning.status(),
                  provisioning == null ? null : provisioning.lastError());
            })
        .sorted(Comparator.comparing(SubscriptionSummary::organizationName))
        .toList();
  }

  @Transactional
  public void startTrial(
      UUID organizationId, String planCode, String adminEmail, String adminName) {
    if (subscriptions.findByOrganizationId(organizationId).isPresent()) {
      return;
    }
    SubscriptionPlan plan = requirePlan(planCode == null ? "GROWTH" : planCode);
    subscriptions.save(new TenantSubscription(organizationId, plan, clock.instant()));
    provisionings.ensurePending(organizationId, adminEmail, adminName);
  }

  @Transactional
  public CheckoutResult checkoutCurrent(String planCode, String interval) {
    return checkout(TenantContext.require(), planCode, interval);
  }

  @Transactional
  public CheckoutResult checkout(UUID organizationId, String planCode, String interval) {
    requireStripe();
    SubscriptionPlan plan = requirePlan(planCode);
    String normalizedInterval = normalizeInterval(interval);
    String priceId =
        normalizedInterval.equals("ANNUAL")
            ? plan.getStripePriceAnnual()
            : plan.getStripePriceMonthly();
    if (priceId == null || priceId.isBlank()) {
      throw new BusinessRuleException("Stripe price is not configured for this plan");
    }
    TenantSubscription subscription = requireSubscription(organizationId);
    String stripeSubscriptionId = subscription.getStripeSubscriptionId();
    boolean activeStripeSubscription =
        stripeSubscriptionId != null
            && !stripeSubscriptionId.isBlank()
            && !"CANCELED".equals(subscription.getStatus());
    if (activeStripeSubscription) {
      updateStripeSubscription(subscription, plan, normalizedInterval, priceId);
      subscription.applyTerms(
          plan.getCode(),
          normalizedInterval,
          plan.getMonthlyPrice(),
          plan.getAnnualPrice(),
          plan.getCurrency(),
          priceId);
      subscription.markStripeBilling(clock.instant());
      subscriptions.save(subscription);
      return new CheckoutResult(null, true);
    }
    String customerId = subscription.getStripeCustomerId();
    if (customerId == null || customerId.isBlank()) {
      customerId = createCustomer(organizationId);
      subscription.setStripeCustomerId(customerId);
      subscriptions.save(subscription);
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
    form.put("subscription_data[metadata][planCode]", plan.getCode());
    form.put("subscription_data[metadata][billingInterval]", normalizedInterval);
    JsonNode session = post("/checkout/sessions", form);
    return new CheckoutResult(session.path("url").asText(), false);
  }

  @Transactional(readOnly = true)
  public ChangePreview previewChange(UUID organizationId, String planCode, String billingInterval) {
    SubscriptionPlan plan = requirePlan(planCode);
    String interval = normalizeInterval(billingInterval);
    TenantSubscription subscription = requireSubscription(organizationId);
    if (!isStripeBacked(subscription)) {
      BigDecimal next = interval.equals("ANNUAL") ? plan.getAnnualPrice() : plan.getMonthlyPrice();
      return new ChangePreview(
          plan.getCode(),
          plan.getName(),
          interval,
          subscription.getBillingSource(),
          false,
          BigDecimal.ZERO,
          plan.getCurrency(),
          null,
          next,
          List.of());
    }
    requireStripe();
    String priceId =
        interval.equals("ANNUAL") ? plan.getStripePriceAnnual() : plan.getStripePriceMonthly();
    if (priceId == null || priceId.isBlank()) {
      throw new BusinessRuleException("Stripe price is not configured for this plan");
    }
    String stripeSubscriptionId = subscription.getStripeSubscriptionId();
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
    BigDecimal next = interval.equals("ANNUAL") ? plan.getAnnualPrice() : plan.getMonthlyPrice();
    return new ChangePreview(
        plan.getCode(),
        plan.getName(),
        interval,
        "STRIPE",
        true,
        cents(invoice.path("amount_due").asLong(0)),
        invoice.path("currency").asText(plan.getCurrency()).toUpperCase(),
        epoch(invoice.path("next_payment_attempt")),
        next,
        lines);
  }

  @Transactional
  public String portal() {
    requireStripe();
    UUID organizationId = TenantContext.require();
    TenantSubscription subscription = requireSubscription(organizationId);
    String customerId = subscription.getStripeCustomerId();
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
      if (!stripeEvents.recordIfAbsent(eventId, eventType, clock.instant(), payload)) {
        return;
      }
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
    subscriptions
        .findByOrganizationId(UUID.fromString(organizationId))
        .ifPresent(
            subscription -> {
              subscription.linkStripeCheckout(
                  object.path("customer").asText(),
                  object.path("subscription").asText(),
                  clock.instant());
              subscriptions.save(subscription);
            });
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
    String resolvedPriceId = stripePriceId;
    if (!organizationId.isBlank()) {
      SubscriptionPlan plan = requirePlan(planCode);
      subscriptions
          .findByOrganizationId(UUID.fromString(organizationId))
          .ifPresent(
              subscription -> {
                subscription.applyStripeSubscription(
                    plan.getCode(),
                    status,
                    interval,
                    stripeSubscriptionId,
                    object.path("customer").asText(),
                    plan.getMonthlyPrice(),
                    plan.getAnnualPrice(),
                    plan.getCurrency(),
                    resolvedPriceId,
                    periodStart,
                    periodEnd,
                    cancel,
                    clock.instant());
                subscriptions.save(subscription);
              });
    } else {
      subscriptions
          .findByStripeSubscriptionId(stripeSubscriptionId)
          .ifPresent(
              subscription -> {
                subscription.applyStripeStatus(
                    status, periodStart, periodEnd, cancel, resolvedPriceId, clock.instant());
                subscriptions.save(subscription);
              });
    }
  }

  private void cancelByStripeId(String stripeSubscriptionId) {
    subscriptions
        .findByStripeSubscriptionId(stripeSubscriptionId)
        .ifPresent(
            subscription -> {
              subscription.cancelByStripe(clock.instant());
              subscriptions.save(subscription);
            });
  }

  private void markPastDue(String customerId) {
    for (TenantSubscription subscription : subscriptions.findByStripeCustomerId(customerId)) {
      subscription.markPastDue(clock.instant());
      subscriptions.save(subscription);
    }
  }

  private void markPaid(String customerId) {
    for (TenantSubscription subscription : subscriptions.findByStripeCustomerId(customerId)) {
      subscription.markPaid(clock.instant());
      subscriptions.save(subscription);
    }
  }

  private boolean isStripeBacked(TenantSubscription subscription) {
    return "STRIPE".equals(subscription.getBillingSource())
        && subscription.getStripeSubscriptionId() != null
        && !subscription.getStripeSubscriptionId().isBlank();
  }

  private void updatePlanTerms(
      TenantSubscription subscription,
      SubscriptionPlan plan,
      String interval,
      String stripePriceId) {
    subscription.applyTerms(
        plan.getCode(),
        interval,
        plan.getMonthlyPrice(),
        plan.getAnnualPrice(),
        plan.getCurrency(),
        stripePriceId);
    subscriptions.save(subscription);
  }

  private void updateStripeSubscription(
      TenantSubscription subscription, SubscriptionPlan plan, String interval, String priceId) {
    JsonNode existing = get("/subscriptions/" + subscription.getStripeSubscriptionId());
    JsonNode items = existing.path("items").path("data");
    if (!items.isArray() || items.isEmpty()) {
      throw new BusinessRuleException("Stripe subscription has no billable item");
    }
    Map<String, String> form = new java.util.LinkedHashMap<>();
    form.put("items[0][id]", items.get(0).path("id").asText());
    form.put("items[0][price]", priceId);
    form.put("proration_behavior", "create_prorations");
    form.put("metadata[organizationId]", subscription.getOrganizationId().toString());
    form.put("metadata[planCode]", plan.getCode());
    form.put("metadata[billingInterval]", interval);
    post("/subscriptions/" + subscription.getStripeSubscriptionId(), form);
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
    String name = organizationNames.name(organizationId).orElse("Tenant");
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
        snapshots.findAllOrdered().stream()
            .map(
                snapshot ->
                    new SnapshotPoint(
                        snapshot.getSnapshotDate(),
                        snapshot.getMrr(),
                        snapshot.getArr(),
                        snapshot.getPayingTenants(),
                        snapshot.getTrialingTenants(),
                        snapshot.getPastDueTenants(),
                        snapshot.getCanceledTenants()))
            .toList();

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
    SubscriptionPlan plan = requirePlan(planCode);
    String interval = normalizeInterval(billingInterval);
    TenantSubscription subscription = requireSubscription(organizationId);
    String priceId =
        interval.equals("ANNUAL") ? plan.getStripePriceAnnual() : plan.getStripePriceMonthly();
    if (isStripeBacked(subscription)) {
      if (priceId == null || priceId.isBlank()) {
        throw new BusinessRuleException("Stripe price is not configured for this plan");
      }
      updateStripeSubscription(subscription, plan, interval, priceId);
    }
    updatePlanTerms(subscription, plan, interval, priceId);
    if (isStripeBacked(subscription)) {
      subscription.markStripeBilling(clock.instant());
      subscriptions.save(subscription);
    }
    return forTenant(organizationId, false);
  }

  @Transactional
  public SubscriptionView extendTrial(UUID organizationId, int days) {
    if (days < 1 || days > 365) {
      throw new BusinessRuleException("Trial extension must be between 1 and 365 days");
    }
    TenantSubscription subscription = requireSubscription(organizationId);
    if (!"TRIALING".equals(subscription.getStatus())) {
      throw new BusinessRuleException("Only a trialing subscription can be extended");
    }
    if (isStripeBacked(subscription)) {
      throw new BusinessRuleException(
          "This trial is managed by Stripe; extend it from the Stripe dashboard");
    }
    subscription.extendTrial(days, clock.instant());
    subscriptions.save(subscription);
    return forTenant(organizationId, false);
  }

  @Transactional
  public SubscriptionView activate(UUID organizationId) {
    TenantSubscription subscription = requireSubscription(organizationId);
    if (isStripeBacked(subscription)) {
      throw new BusinessRuleException(
          "This subscription is billed by Stripe; activation must come from Stripe");
    }
    String interval = normalizeInterval(subscription.getBillingInterval());
    Instant now = clock.instant();
    Instant periodEnd =
        now.plus(interval.equals("ANNUAL") ? Duration.ofDays(365) : Duration.ofDays(30));
    subscription.activateManual(now, periodEnd);
    subscriptions.save(subscription);
    return forTenant(organizationId, false);
  }

  @Transactional
  public SubscriptionView cancel(UUID organizationId, boolean atPeriodEnd) {
    TenantSubscription subscription = requireSubscription(organizationId);
    if (isStripeBacked(subscription)) {
      String stripeSubscriptionId = subscription.getStripeSubscriptionId();
      if (atPeriodEnd) {
        Map<String, String> form = new java.util.LinkedHashMap<>();
        form.put("cancel_at_period_end", "true");
        post("/subscriptions/" + stripeSubscriptionId, form);
      } else {
        delete("/subscriptions/" + stripeSubscriptionId);
      }
    }
    if (atPeriodEnd) {
      subscription.cancelAtPeriodEnd();
      if (isStripeBacked(subscription)) {
        subscription.markStripeBilling(clock.instant());
      }
    } else {
      subscription.cancelNow();
    }
    subscriptions.save(subscription);
    return forTenant(organizationId, false);
  }

  @Transactional
  public SubscriptionView reactivate(UUID organizationId) {
    TenantSubscription subscription = requireSubscription(organizationId);
    if (isStripeBacked(subscription)) {
      if ("CANCELED".equals(subscription.getStatus())) {
        throw new BusinessRuleException(
            "This Stripe subscription has ended and cannot be reactivated");
      }
      Map<String, String> form = new java.util.LinkedHashMap<>();
      form.put("cancel_at_period_end", "false");
      post("/subscriptions/" + subscription.getStripeSubscriptionId(), form);
      subscription.clearCancelAtPeriodEnd();
      subscription.markStripeBilling(clock.instant());
      subscriptions.save(subscription);
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
      TenantSubscription local =
          subscriptions.findByOrganizationId(summary.organizationId()).orElse(null);
      String stripeSubscriptionId = local == null ? null : local.getStripeSubscriptionId();
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
        String localPriceId = local.getStripePriceId();
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
    TenantSubscription subscription = requireSubscription(organizationId);
    if (!isStripeBacked(subscription)) {
      throw new BusinessRuleException("Only Stripe-backed subscriptions can be reconciled");
    }
    JsonNode stripeSubscription = get("/subscriptions/" + subscription.getStripeSubscriptionId());
    applySubscription(stripeSubscription, organizationId.toString());
    return forTenant(organizationId, false);
  }

  private void localDateSnapshot(LocalDate date) {
    BillingSummary summary = currentBillingSummary();
    SubscriptionMrrSnapshot snapshot =
        snapshots
            .findByDate(date)
            .orElseGet(
                () ->
                    new SubscriptionMrrSnapshot(
                        date,
                        summary.mrr(),
                        summary.arr(),
                        (int) summary.payingTenants(),
                        (int) summary.trialingTenants(),
                        (int) summary.pastDueTenants(),
                        (int) summary.canceledTenants(),
                        clock.instant()));
    snapshot.update(
        summary.mrr(),
        summary.arr(),
        (int) summary.payingTenants(),
        (int) summary.trialingTenants(),
        (int) summary.pastDueTenants(),
        (int) summary.canceledTenants());
    snapshots.save(snapshot);
  }

  private BillingSummary currentBillingSummary() {
    List<TenantSubscription> all = subscriptions.findAll();
    BigDecimal mrr = BigDecimal.ZERO;
    long paying = 0;
    long trialing = 0;
    long pastDue = 0;
    long canceled = 0;
    for (TenantSubscription subscription : all) {
      if ("ACTIVE".equals(subscription.getStatus())) {
        mrr =
            mrr.add(
                planMrr(
                    subscription.getBillingInterval(),
                    subscription.getMonthlyPriceSnapshot(),
                    subscription.getAnnualPriceSnapshot()));
        paying++;
      } else if ("TRIALING".equals(subscription.getStatus())) {
        trialing++;
      } else if ("PAST_DUE".equals(subscription.getStatus())
          || "UNPAID".equals(subscription.getStatus())) {
        pastDue++;
      } else if ("CANCELED".equals(subscription.getStatus())) {
        canceled++;
      }
    }
    return new BillingSummary(
        mrr, mrr.multiply(BigDecimal.valueOf(12)), paying, trialing, pastDue, canceled);
  }

  private TenantSubscription requireSubscription(UUID organizationId) {
    return subscriptions
        .findByOrganizationId(organizationId)
        .orElseThrow(
            () -> new ResourceNotFoundException("No subscription is configured for this tenant"));
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
    SubscriptionPlan plan = requirePlan(code);
    return planView(
        plan,
        featureService
            .featuresForPlans(List.of(plan.getCode()))
            .getOrDefault(plan.getCode(), List.of()));
  }

  private SubscriptionPlan requirePlan(String code) {
    return plans
        .findById(code)
        .orElseThrow(
            () -> new ResourceNotFoundException("Subscription plan %s not found".formatted(code)));
  }

  private static PlanView planView(
      SubscriptionPlan plan, List<SubscriptionFeatureService.FeatureView> features) {
    return new PlanView(
        plan.getCode(),
        plan.getName(),
        plan.getDescription(),
        plan.getMonthlyPrice(),
        plan.getAnnualPrice(),
        plan.getCurrency(),
        plan.getTrialDays(),
        plan.getMaxUsers(),
        plan.getMaxDepartments(),
        plan.getMaxRequestsMonth(),
        plan.getMaxStorageMb(),
        features,
        plan.getStripePriceMonthly(),
        plan.getStripePriceAnnual(),
        plan.isActive(),
        plan.getSortOrder());
  }

  private Usage usage(UUID organizationId) {
    TenantUsagePort.UsageSnapshot snapshot = usage.usage(organizationId);
    return new Usage(
        snapshot.users(),
        snapshot.departments(),
        snapshot.requestsThisMonth(),
        snapshot.storageBytes() / (1024 * 1024));
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
