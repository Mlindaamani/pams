package com.company.procurement.subscription.repositories;

import com.company.procurement.subscription.domain.SubscriptionPlan;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class SubscriptionPlanRepositoryAdapter implements SubscriptionPlanRepository {

  private final JpaSubscriptionPlanRepository jpa;

  public SubscriptionPlanRepositoryAdapter(JpaSubscriptionPlanRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public List<SubscriptionPlan> findActiveOrdered() {
    return jpa.findByActiveTrueOrderBySortOrderAsc();
  }

  @Override
  public List<SubscriptionPlan> findAllOrdered() {
    return jpa.findAllByOrderBySortOrderAscCodeAsc();
  }

  @Override
  public Optional<SubscriptionPlan> findById(String code) {
    return jpa.findById(code);
  }

  @Override
  public SubscriptionPlan save(SubscriptionPlan plan) {
    return jpa.save(plan);
  }

  @Override
  public boolean existsById(String code) {
    return jpa.existsById(code);
  }
}
