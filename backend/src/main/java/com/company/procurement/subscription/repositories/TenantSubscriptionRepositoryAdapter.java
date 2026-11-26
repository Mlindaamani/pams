package com.company.procurement.subscription.repositories;

import com.company.procurement.subscription.domain.TenantSubscription;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class TenantSubscriptionRepositoryAdapter implements TenantSubscriptionRepository {

  private final JpaTenantSubscriptionRepository jpa;

  public TenantSubscriptionRepositoryAdapter(JpaTenantSubscriptionRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public Optional<TenantSubscription> findByOrganizationId(UUID organizationId) {
    return jpa.findByOrganizationId(organizationId);
  }

  @Override
  public Optional<TenantSubscription> findByStripeSubscriptionId(String stripeSubscriptionId) {
    return jpa.findByStripeSubscriptionId(stripeSubscriptionId);
  }

  @Override
  public List<TenantSubscription> findByStripeCustomerId(String stripeCustomerId) {
    return jpa.findByStripeCustomerId(stripeCustomerId);
  }

  @Override
  public List<TenantSubscription> findAll() {
    return jpa.findAll();
  }

  @Override
  public List<TenantSubscription> findTrialsReadyForNotifications() {
    return jpa.findByStatusAndBillingSourceAndTrialEndsAtIsNotNull("TRIALING", "TRIAL");
  }

  @Override
  public List<TenantSubscription> findPastDueReadyForNotifications() {
    return jpa.findByStatusInAndPaymentFailedNotifiedAtIsNull(List.of("PAST_DUE", "UNPAID"));
  }

  @Override
  public List<TenantSubscription> findCanceledReadyForNotifications() {
    return jpa.findByStatusAndCanceledNotifiedAtIsNull("CANCELED");
  }

  @Override
  public Optional<SubscriptionEntitlements> entitlements(UUID organizationId) {
    return jpa.findEntitlements(organizationId)
        .map(
            row ->
                new SubscriptionEntitlements(
                    row.getPlanCode(),
                    row.getStatus(),
                    row.getTrialEndsAt(),
                    row.getCurrentPeriodEnd(),
                    row.getMaxUsers(),
                    row.getMaxDepartments(),
                    row.getMaxRequestsMonth(),
                    row.getMaxStorageMb()));
  }

  @Override
  public boolean hasFeature(UUID organizationId, String featureCode) {
    return jpa.countWithFeature(organizationId, featureCode) > 0;
  }

  @Override
  public List<String> featureCodes(UUID organizationId) {
    return jpa.findFeatureCodes(organizationId);
  }

  @Override
  public TenantSubscription save(TenantSubscription subscription) {
    return jpa.save(subscription);
  }
}
