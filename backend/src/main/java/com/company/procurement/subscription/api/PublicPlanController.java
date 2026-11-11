package com.company.procurement.subscription.api;

import com.company.procurement.subscription.application.SubscriptionFeatureService;
import com.company.procurement.subscription.application.SubscriptionService;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/plans")
public class PublicPlanController {

  private final SubscriptionService subscriptions;

  public PublicPlanController(SubscriptionService subscriptions) {
    this.subscriptions = subscriptions;
  }

  @GetMapping
  public List<PublicPlan> plans() {
    return subscriptions.plans().stream()
        .map(
            plan ->
                new PublicPlan(
                    plan.code(),
                    plan.name(),
                    plan.description(),
                    plan.monthlyPrice(),
                    plan.annualPrice(),
                    plan.currency(),
                    plan.trialDays(),
                    plan.maxUsers(),
                    plan.maxDepartments(),
                    plan.maxRequestsMonth(),
                    plan.maxStorageMb(),
                    plan.features().stream()
                        .map(SubscriptionFeatureService.FeatureView::name)
                        .toList()))
        .toList();
  }

  public record PublicPlan(
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
      List<String> features) {}
}
