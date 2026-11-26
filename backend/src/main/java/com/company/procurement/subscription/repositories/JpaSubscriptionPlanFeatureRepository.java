package com.company.procurement.subscription.repositories;

import com.company.procurement.subscription.domain.SubscriptionPlanFeature;
import com.company.procurement.subscription.domain.SubscriptionPlanFeatureId;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaSubscriptionPlanFeatureRepository
    extends JpaRepository<SubscriptionPlanFeature, SubscriptionPlanFeatureId> {

  @Query(
      """
      select pf.id.planCode as planCode,
             f.code as code,
             f.name as name,
             f.description as description,
             f.category as category,
             f.active as active,
             f.sortOrder as sortOrder
        from SubscriptionPlanFeature pf
        join SubscriptionFeature f on f.code = pf.id.featureCode
       where pf.id.planCode in :planCodes
       order by f.sortOrder, f.code
      """)
  List<PlanFeatureProjection> findFeaturesForPlans(
      @Param("planCodes") Collection<String> planCodes);

  @Modifying
  @Query("delete from SubscriptionPlanFeature pf where pf.id.planCode = :planCode")
  int deleteByPlanCode(@Param("planCode") String planCode);

  interface PlanFeatureProjection {
    String getPlanCode();

    String getCode();

    String getName();

    String getDescription();

    String getCategory();

    boolean isActive();

    int getSortOrder();
  }
}
