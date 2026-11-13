package com.company.procurement.webhook.api.mapper;

import com.company.procurement.webhook.api.dto.WebhookDeliveryResponse;
import com.company.procurement.webhook.api.dto.WebhookSubscriptionResponse;
import com.company.procurement.webhook.domain.WebhookDelivery;
import com.company.procurement.webhook.domain.WebhookSubscription;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class WebhookMapper {

  public WebhookSubscriptionResponse toResponse(WebhookSubscription subscription) {
    return toResponse(subscription, 0);
  }

  public WebhookSubscriptionResponse toResponse(
      WebhookSubscription subscription, long deliveryCount) {
    return new WebhookSubscriptionResponse(
        subscription.getId(),
        subscription.getName(),
        subscription.getUrl(),
        subscription.getDescription(),
        List.copyOf(subscription.eventTypeSet()),
        subscription.isActive(),
        subscription.getLastSuccessAt(),
        subscription.getLastFailureAt(),
        subscription.getSigningMethod(),
        subscription.getTimeoutSeconds(),
        subscription.getMaxAttempts(),
        deliveryCount,
        subscription.getCreatedAt(),
        subscription.getUpdatedAt(),
        subscription.getVersion());
  }

  public WebhookDeliveryResponse toResponse(WebhookDelivery delivery) {
    return toResponse(delivery, null, null);
  }

  public WebhookDeliveryResponse toResponse(WebhookDelivery delivery, String subscriptionUrl) {
    return toResponse(delivery, null, subscriptionUrl);
  }

  public WebhookDeliveryResponse toResponse(
      WebhookDelivery delivery, String subscriptionName, String subscriptionUrl) {
    return new WebhookDeliveryResponse(
        delivery.getId(),
        delivery.getSubscriptionId(),
        subscriptionName,
        subscriptionUrl,
        delivery.getEventId(),
        delivery.getEventType(),
        delivery.getPayload(),
        delivery.getStatus(),
        delivery.getAttempts(),
        delivery.getNextAttemptAt(),
        delivery.getLastAttemptAt(),
        delivery.getResponseStatus(),
        delivery.getResponseBody(),
        delivery.getErrorMessage(),
        delivery.getDurationMs(),
        delivery.getCreatedAt());
  }
}
