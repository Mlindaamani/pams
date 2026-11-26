package com.company.procurement.subscription.repositories;

import com.company.procurement.subscription.domain.TenantSubscription;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence port for tenant subscriptions. */
public interface TenantSubscriptionRepository {

  Optional<TenantSubscription> findByOrganizationId(UUID organizationId);

  Optional<TenantSubscription> findByStripeSubscriptionId(String stripeSubscriptionId);

  List<TenantSubscription> findByStripeCustomerId(String stripeCustomerId);

  List<TenantSubscription> findAll();

  List<TenantSubscription> findTrialsReadyForNotifications();

  List<TenantSubscription> findPastDueReadyForNotifications();

  List<TenantSubscription> findCanceledReadyForNotifications();

  Optional<SubscriptionEntitlements> entitlements(UUID organizationId);

  boolean hasFeature(UUID organizationId, String featureCode);

  List<String> featureCodes(UUID organizationId);

  TenantSubscription save(TenantSubscription subscription);

  record SubscriptionEntitlements(
      String planCode,
      String status,
      Instant trialEndsAt,
      Instant currentPeriodEnd,
      Integer maxUsers,
      Integer maxDepartments,
      Integer maxRequestsMonth,
      Integer maxStorageMb) {}
}
