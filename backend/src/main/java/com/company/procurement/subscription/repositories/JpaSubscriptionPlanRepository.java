package com.company.procurement.subscription.repositories;

import com.company.procurement.subscription.domain.SubscriptionPlan;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaSubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, String> {

  List<SubscriptionPlan> findByActiveTrueOrderBySortOrderAsc();

  List<SubscriptionPlan> findAllByOrderBySortOrderAscCodeAsc();
}
