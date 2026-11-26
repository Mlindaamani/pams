package com.company.procurement.subscription.repositories;

import com.company.procurement.subscription.domain.TenantSubscription;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaTenantSubscriptionRepository extends JpaRepository<TenantSubscription, UUID> {

  Optional<TenantSubscription> findByOrganizationId(UUID organizationId);

  Optional<TenantSubscription> findByStripeSubscriptionId(String stripeSubscriptionId);

  List<TenantSubscription> findByStripeCustomerId(String stripeCustomerId);

  List<TenantSubscription> findByStatusAndBillingSourceAndTrialEndsAtIsNotNull(
      String status, String billingSource);

  List<TenantSubscription> findByStatusInAndPaymentFailedNotifiedAtIsNull(
      Collection<String> statuses);

  List<TenantSubscription> findByStatusAndCanceledNotifiedAtIsNull(String status);

  @Query(
      """
      select ts.planCode as planCode,
             ts.status as status,
             ts.trialEndsAt as trialEndsAt,
             ts.currentPeriodEnd as currentPeriodEnd,
             p.maxUsers as maxUsers,
             p.maxDepartments as maxDepartments,
             p.maxRequestsMonth as maxRequestsMonth,
             p.maxStorageMb as maxStorageMb
        from TenantSubscription ts
        join SubscriptionPlan p on p.code = ts.planCode
       where ts.organizationId = :organizationId
      """)
  Optional<EntitlementsProjection> findEntitlements(@Param("organizationId") UUID organizationId);

  @Query(
      """
      select count(ts) from TenantSubscription ts
       join SubscriptionPlanFeature pf on pf.id.planCode = ts.planCode
       where ts.organizationId = :organizationId and pf.id.featureCode = :featureCode
      """)
  long countWithFeature(
      @Param("organizationId") UUID organizationId, @Param("featureCode") String featureCode);

  @Query(
      """
      select pf.id.featureCode
        from TenantSubscription ts
        join SubscriptionPlanFeature pf on pf.id.planCode = ts.planCode
        join SubscriptionFeature f on f.code = pf.id.featureCode
       where ts.organizationId = :organizationId
       order by f.sortOrder, f.code
      """)
  List<String> findFeatureCodes(@Param("organizationId") UUID organizationId);

  interface EntitlementsProjection {
    String getPlanCode();

    String getStatus();

    Instant getTrialEndsAt();

    Instant getCurrentPeriodEnd();

    Integer getMaxUsers();

    Integer getMaxDepartments();

    Integer getMaxRequestsMonth();

    Integer getMaxStorageMb();
  }
}
