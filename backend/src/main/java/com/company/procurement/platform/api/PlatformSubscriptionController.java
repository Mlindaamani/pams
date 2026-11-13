package com.company.procurement.platform.api;

import com.company.procurement.platform.application.PlatformAuditService;
import com.company.procurement.subscription.application.SubscriptionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST API for platform-side tenant subscription administration. */
@RestController
@RequestMapping("/api/v1/platform/tenants/{id}/subscription")
@PreAuthorize("@permissions.has('platform:tenant_manage')")
@Tag(
    name = "Platform subscriptions",
    description = "Platform-side tenant subscription administration")
public class PlatformSubscriptionController {

  private final SubscriptionService subscriptions;
  private final PlatformAuditService audit;

  public PlatformSubscriptionController(
      SubscriptionService subscriptions, PlatformAuditService audit) {
    this.subscriptions = subscriptions;
    this.audit = audit;
  }

  @PostMapping("/change-plan")
  public SubscriptionService.SubscriptionView changePlan(
      @PathVariable UUID id, @Valid @RequestBody ChangePlanRequest request) {
    SubscriptionService.SubscriptionView view =
        subscriptions.changePlan(id, request.planCode(), request.billingInterval());
    audit.record(
        "tenant_subscription_plan_changed",
        id,
        Map.of("planCode", request.planCode(), "billingInterval", request.billingInterval()));
    return view;
  }

  @PostMapping("/extend-trial")
  public SubscriptionService.SubscriptionView extendTrial(
      @PathVariable UUID id, @Valid @RequestBody ExtendTrialRequest request) {
    SubscriptionService.SubscriptionView view = subscriptions.extendTrial(id, request.days());
    audit.record("tenant_subscription_trial_extended", id, Map.of("days", request.days()));
    return view;
  }

  @PostMapping("/activate")
  public SubscriptionService.SubscriptionView activate(@PathVariable UUID id) {
    SubscriptionService.SubscriptionView view = subscriptions.activate(id);
    audit.record("tenant_subscription_activated", id, Map.of());
    return view;
  }

  @PostMapping("/cancel")
  public SubscriptionService.SubscriptionView cancel(
      @PathVariable UUID id, @Valid @RequestBody CancelRequest request) {
    SubscriptionService.SubscriptionView view = subscriptions.cancel(id, request.atPeriodEnd());
    audit.record("tenant_subscription_canceled", id, Map.of("atPeriodEnd", request.atPeriodEnd()));
    return view;
  }

  @PostMapping("/reconcile")
  public SubscriptionService.SubscriptionView reconcile(@PathVariable UUID id) {
    SubscriptionService.SubscriptionView view = subscriptions.reconcile(id);
    audit.record("tenant_subscription_reconciled", id, Map.of());
    return view;
  }

  @PostMapping("/reactivate")
  public SubscriptionService.SubscriptionView reactivate(@PathVariable UUID id) {
    SubscriptionService.SubscriptionView view = subscriptions.reactivate(id);
    audit.record("tenant_subscription_reactivated", id, Map.of());
    return view;
  }

  public record ChangePlanRequest(@NotBlank String planCode, @NotBlank String billingInterval) {}

  public record ExtendTrialRequest(@Min(1) @Max(365) int days) {}

  public record CancelRequest(boolean atPeriodEnd) {}
}
