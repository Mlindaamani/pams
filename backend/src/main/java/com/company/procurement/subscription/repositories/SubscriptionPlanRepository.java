package com.company.procurement.subscription.repositories;

import com.company.procurement.subscription.domain.SubscriptionPlan;
import java.util.List;
import java.util.Optional;

/** Persistence port for subscription plans. */
public interface SubscriptionPlanRepository {

  List<SubscriptionPlan> findActiveOrdered();

  List<SubscriptionPlan> findAllOrdered();

  Optional<SubscriptionPlan> findById(String code);

  SubscriptionPlan save(SubscriptionPlan plan);

  boolean existsById(String code);
}
