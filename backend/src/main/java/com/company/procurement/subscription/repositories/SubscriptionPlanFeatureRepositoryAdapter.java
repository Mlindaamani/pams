package com.company.procurement.subscription.repositories;

import com.company.procurement.subscription.domain.SubscriptionPlanFeature;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class SubscriptionPlanFeatureRepositoryAdapter implements SubscriptionPlanFeatureRepository {

  private final JpaSubscriptionPlanFeatureRepository jpa;

  public SubscriptionPlanFeatureRepositoryAdapter(JpaSubscriptionPlanFeatureRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public List<PlanFeatureRow> findFeaturesForPlans(Collection<String> planCodes) {
    return jpa.findFeaturesForPlans(planCodes).stream()
        .map(
            row ->
                new PlanFeatureRow(
                    row.getPlanCode(),
                    row.getCode(),
                    row.getName(),
                    row.getDescription(),
                    row.getCategory(),
                    row.isActive(),
                    row.getSortOrder()))
        .toList();
  }

  @Override
  public List<SubscriptionPlanFeature> saveAll(List<SubscriptionPlanFeature> assignments) {
    return jpa.saveAll(assignments);
  }

  @Override
  public void deleteByPlanCode(String planCode) {
    jpa.deleteByPlanCode(planCode);
  }
}
