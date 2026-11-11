package com.company.procurement.platform.api;

import com.company.procurement.platform.api.dto.SubscriptionPlanRequest;
import com.company.procurement.platform.application.PlatformAuditService;
import com.company.procurement.subscription.application.SubscriptionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/subscription-plans")
@Tag(name = "Platform subscription plans", description = "Create and manage SaaS plans")
public class PlatformPlanController {

  private final SubscriptionService subscriptions;
  private final PlatformAuditService audit;

  public PlatformPlanController(SubscriptionService subscriptions, PlatformAuditService audit) {
    this.subscriptions = subscriptions;
    this.audit = audit;
  }

  @GetMapping
  @PreAuthorize("@permissions.has('platform:tenant_read')")
  public List<SubscriptionService.PlanView> list() {
    return subscriptions.allPlans();
  }

  @PostMapping
  @PreAuthorize("@permissions.has('platform:tenant_manage')")
  public SubscriptionService.PlanView create(@Valid @RequestBody SubscriptionPlanRequest request) {
    SubscriptionService.PlanView created =
        subscriptions.createPlan(command(request, request.code()));
    audit.record(
        "subscription_plan_created",
        null,
        Map.of("planCode", created.code(), "name", created.name()));
    return created;
  }

  @PutMapping("/{code}")
  @PreAuthorize("@permissions.has('platform:tenant_manage')")
  public SubscriptionService.PlanView update(
      @PathVariable String code, @Valid @RequestBody SubscriptionPlanRequest request) {
    SubscriptionService.PlanView updated = subscriptions.updatePlan(code, command(request, code));
    audit.record(
        "subscription_plan_updated",
        null,
        Map.of("planCode", updated.code(), "name", updated.name()));
    return updated;
  }

  @DeleteMapping("/{code}")
  @PreAuthorize("@permissions.has('platform:tenant_manage')")
  public ResponseEntity<Void> deactivate(@PathVariable String code) {
    subscriptions.deactivatePlan(code);
    audit.record("subscription_plan_deactivated", null, Map.of("planCode", code));
    return ResponseEntity.noContent().build();
  }

  private static SubscriptionService.PlanCommand command(
      SubscriptionPlanRequest request, String code) {
    return new SubscriptionService.PlanCommand(
        code,
        request.name(),
        request.description(),
        request.monthlyPrice(),
        request.annualPrice(),
        request.currency(),
        request.trialDays(),
        request.maxUsers(),
        request.maxDepartments(),
        request.maxRequestsMonth(),
        request.maxStorageMb(),
        request.featureCodes(),
        request.active(),
        request.sortOrder(),
        request.stripePriceMonthly(),
        request.stripePriceAnnual());
  }
}
