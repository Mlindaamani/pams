package com.company.procurement.subscription.api;

import com.company.procurement.subscription.application.SubscriptionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(
    name = "Subscriptions",
    description = "SaaS plans, tenant billing and Stripe test-mode checkout")
public class SubscriptionController {

  private final SubscriptionService subscriptions;

  public SubscriptionController(SubscriptionService subscriptions) {
    this.subscriptions = subscriptions;
  }

  @GetMapping("/me/features")
  public FeatureEntitlements myFeatures() {
    return new FeatureEntitlements(subscriptions.currentFeatureCodes());
  }

  @GetMapping("/subscription/plans")
  @PreAuthorize("@permissions.has('subscription:read')")
  public List<SubscriptionService.PlanView> plans() {
    return subscriptions.plans();
  }

  @GetMapping("/subscription")
  @PreAuthorize("@permissions.has('subscription:read')")
  public SubscriptionService.SubscriptionView current() {
    return subscriptions.current();
  }

  @PostMapping("/subscription/checkout")
  @PreAuthorize("@permissions.has('subscription:manage')")
  public CheckoutResponse checkout(@Valid @RequestBody CheckoutRequest request) {
    SubscriptionService.CheckoutResult result =
        subscriptions.checkoutCurrent(request.planCode(), request.interval());
    return new CheckoutResponse(result.url(), result.updated());
  }

  @PostMapping("/subscription/portal")
  @PreAuthorize("@permissions.has('subscription:manage')")
  public CheckoutResponse portal() {
    return new CheckoutResponse(subscriptions.portal(), false);
  }

  @GetMapping("/platform/subscriptions")
  @PreAuthorize("@permissions.has('platform:tenant_read')")
  public List<SubscriptionService.SubscriptionSummary> summaries() {
    return subscriptions.summaries();
  }

  @PostMapping("/subscription/change-preview")
  @PreAuthorize("@permissions.has('subscription:manage')")
  public SubscriptionService.ChangePreview changePreview(
      @Valid @RequestBody CheckoutRequest request) {
    return subscriptions.previewChange(
        com.company.procurement.shared.tenant.TenantContext.require(),
        request.planCode(),
        request.interval());
  }

  @GetMapping("/platform/subscriptions/reconciliation")
  @PreAuthorize("@permissions.has('platform:tenant_read')")
  public SubscriptionService.ReconciliationReport reconciliation() {
    return subscriptions.reconciliationAudit();
  }

  @GetMapping("/platform/subscriptions/plans")
  @PreAuthorize("@permissions.has('platform:tenant_read')")
  public List<SubscriptionService.PlanView> platformPlans() {
    return subscriptions.plans();
  }

  @GetMapping("/platform/subscriptions/metrics")
  @PreAuthorize("@permissions.has('platform:analytics_read')")
  public SubscriptionService.BillingMetrics metrics() {
    return subscriptions.billingMetrics();
  }

  @GetMapping("/platform/tenants/{id}/subscription")
  @PreAuthorize("@permissions.has('platform:tenant_read')")
  public SubscriptionService.SubscriptionView tenant(@PathVariable UUID id) {
    return subscriptions.forTenant(id, false);
  }

  @PostMapping("/platform/tenants/{id}/subscription/checkout")
  @PreAuthorize("@permissions.has('platform:tenant_manage')")
  public CheckoutResponse platformCheckout(
      @PathVariable UUID id, @Valid @RequestBody CheckoutRequest request) {
    SubscriptionService.CheckoutResult result =
        subscriptions.checkout(id, request.planCode(), request.interval());
    return new CheckoutResponse(result.url(), result.updated());
  }

  public record CheckoutRequest(@NotBlank String planCode, @NotBlank String interval) {}

  public record CheckoutResponse(String url, boolean updated) {}

  public record FeatureEntitlements(List<String> features) {}
}
