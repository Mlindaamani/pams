package com.company.procurement.subscription.services;

import com.company.procurement.shared.outbox.OutboxEventTypes;
import com.company.procurement.shared.services.OutboxService;
import com.company.procurement.shared.tenant.OrganizationNamePort;
import com.company.procurement.shared.tenant.TenantContext;
import com.company.procurement.shared.tenant.TenantProvisioningPort;
import com.company.procurement.subscription.domain.SubscriptionPlan;
import com.company.procurement.subscription.domain.TenantSubscription;
import com.company.procurement.subscription.repositories.SubscriptionPlanRepository;
import com.company.procurement.subscription.repositories.TenantSubscriptionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Runs trial, dunning and cancellation lifecycle notices. */
@Service
public class SubscriptionLifecycleService {

  private final TenantSubscriptionRepository subscriptions;
  private final SubscriptionPlanRepository plans;
  private final OrganizationNamePort organizationNames;
  private final TenantProvisioningPort provisionings;
  private final OutboxService outbox;
  private final Clock clock;

  public SubscriptionLifecycleService(
      TenantSubscriptionRepository subscriptions,
      SubscriptionPlanRepository plans,
      OrganizationNamePort organizationNames,
      TenantProvisioningPort provisionings,
      OutboxService outbox,
      Clock clock) {
    this.subscriptions = subscriptions;
    this.plans = plans;
    this.organizationNames = organizationNames;
    this.provisionings = provisionings;
    this.outbox = outbox;
    this.clock = clock;
  }

  public void runLifecycleNotifications() {
    Instant now = clock.instant();
    processTrials(now);
    processPastDue(now);
    processCanceled(now);
  }

  private void processTrials(Instant now) {
    List<TenantSubscription> trials = subscriptions.findTrialsReadyForNotifications();
    for (TenantSubscription trial : trials) {
      Instant trialEndsAt = trial.getTrialEndsAt();
      if (trialEndsAt == null) continue;
      if (!trialEndsAt.isAfter(now)) {
        trial.expireTrial(now);
        subscriptions.save(trial);
        notify(trial, OutboxEventTypes.SUBSCRIPTION_TRIAL_EXPIRED, "Expired", trialEndsAt);
        continue;
      }
      Duration remaining = Duration.between(now, trialEndsAt);
      if (remaining.toHours() <= 24 && trial.getTrialReminder1dSentAt() == null) {
        trial.markTrialReminder1d(now);
        subscriptions.save(trial);
        notify(trial, OutboxEventTypes.SUBSCRIPTION_TRIAL_ENDING, "Trial ending", trialEndsAt);
      } else if (remaining.toHours() <= 7 * 24
          && remaining.toHours() > 24
          && trial.getTrialReminder7dSentAt() == null) {
        trial.markTrialReminder7d(now);
        subscriptions.save(trial);
        notify(trial, OutboxEventTypes.SUBSCRIPTION_TRIAL_ENDING, "Trial ending", trialEndsAt);
      }
    }
  }

  private void processPastDue(Instant now) {
    for (TenantSubscription subscription : subscriptions.findPastDueReadyForNotifications()) {
      subscription.markPaymentFailedNotified(now);
      subscriptions.save(subscription);
      notify(
          subscription,
          OutboxEventTypes.SUBSCRIPTION_PAYMENT_FAILED,
          "Payment failed",
          subscription.getCurrentPeriodEnd());
    }
  }

  private void processCanceled(Instant now) {
    for (TenantSubscription subscription : subscriptions.findCanceledReadyForNotifications()) {
      subscription.markCanceledNotified(now);
      subscriptions.save(subscription);
      notify(
          subscription,
          OutboxEventTypes.SUBSCRIPTION_CANCELED,
          "Canceled",
          subscription.getCurrentPeriodEnd());
    }
  }

  private void notify(
      TenantSubscription subscription, String eventType, String statusLabel, Instant date) {
    UUID organizationId = subscription.getOrganizationId();
    TenantProvisioningPort.NotificationContact contact =
        provisionings.notificationContact(organizationId).orElse(null);
    if (contact == null || contact.email() == null || contact.email().isBlank()) return;
    String tenantName = organizationNames.name(organizationId).orElse("your organization");
    String planName =
        plans
            .findById(subscription.getPlanCode())
            .map(SubscriptionPlan::getName)
            .orElse(subscription.getPlanCode());
    TenantContext.set(organizationId);
    try {
      outbox.enqueue(
          eventType,
          "TenantSubscription",
          subscription.getId(),
          Map.of(
              "email",
              contact.email(),
              "recipientName",
              contact.name() == null ? "Administrator" : contact.name(),
              "tenantName",
              tenantName,
              "planName",
              planName,
              "statusLabel",
              statusLabel,
              "dateLabel",
              date == null ? "—" : date.toString()));
    } finally {
      TenantContext.clear();
    }
  }
}
