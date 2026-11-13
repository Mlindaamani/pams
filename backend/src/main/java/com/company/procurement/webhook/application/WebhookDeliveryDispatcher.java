package com.company.procurement.webhook.application;

import com.company.procurement.shared.config.WebhookProperties;
import com.company.procurement.webhook.domain.WebhookDelivery;
import com.company.procurement.webhook.domain.WebhookDeliveryRepository;
import com.company.procurement.webhook.domain.WebhookSubscription;
import com.company.procurement.webhook.domain.WebhookSubscriptionRepository;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class WebhookDeliveryDispatcher {

  private static final Logger log = LoggerFactory.getLogger(WebhookDeliveryDispatcher.class);

  private final WebhookDeliveryRepository deliveries;
  private final WebhookSubscriptionRepository subscriptions;
  private final WebhookHttpSender sender;
  private final WebhookProperties properties;
  private final Clock clock;

  public WebhookDeliveryDispatcher(
      WebhookDeliveryRepository deliveries,
      WebhookSubscriptionRepository subscriptions,
      WebhookHttpSender sender,
      WebhookProperties properties,
      Clock clock) {
    this.deliveries = deliveries;
    this.subscriptions = subscriptions;
    this.sender = sender;
    this.properties = properties;
    this.clock = clock;
  }

  public int dispatchBatch() {
    List<WebhookDelivery> due = deliveries.findDue(clock.instant(), properties.batchSize());
    int succeeded = 0;
    for (WebhookDelivery delivery : due) {
      WebhookSubscription subscription =
          subscriptions
              .findByIdAndOrganizationId(delivery.getSubscriptionId(), delivery.getOrganizationId())
              .orElse(null);
      if (subscription == null || !subscription.isActive()) {
        delivery.recordFailure(
            clock.instant(),
            0,
            null,
            "Webhook subscription is inactive or missing",
            0,
            subscription == null ? properties.maxAttempts() : subscription.getMaxAttempts());
        deliveries.save(delivery);
        continue;
      }
      WebhookHttpSender.Attempt attempt = sender.send(subscription, delivery);
      if (attempt.success()) {
        delivery.markSucceeded(
            clock.instant(), attempt.status(), attempt.body(), attempt.durationMs());
        subscription.markDeliverySuccess(clock.instant());
        succeeded++;
      } else {
        delivery.recordFailure(
            clock.instant(),
            attempt.status(),
            attempt.body(),
            attempt.error(),
            attempt.durationMs(),
            subscription.getMaxAttempts());
        subscription.markDeliveryFailure(clock.instant());
        log.warn(
            "Webhook delivery {} for {} failed (attempt {})",
            delivery.getId(),
            delivery.getEventType(),
            delivery.getAttempts());
      }
      deliveries.save(delivery);
      subscriptions.save(subscription);
    }
    return succeeded;
  }
}
