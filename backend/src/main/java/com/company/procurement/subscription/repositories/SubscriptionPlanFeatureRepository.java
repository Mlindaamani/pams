package com.company.procurement.subscription.repositories;

import com.company.procurement.subscription.domain.SubscriptionPlanFeature;
import java.util.Collection;
import java.util.List;

/** Persistence port for plan-feature assignments. */
public interface SubscriptionPlanFeatureRepository {

  List<PlanFeatureRow> findFeaturesForPlans(Collection<String> planCodes);

  List<SubscriptionPlanFeature> saveAll(List<SubscriptionPlanFeature> assignments);

  void deleteByPlanCode(String planCode);

  record PlanFeatureRow(
      String planCode,
      String code,
      String name,
      String description,
      String category,
      boolean active,
      int sortOrder) {}
}
