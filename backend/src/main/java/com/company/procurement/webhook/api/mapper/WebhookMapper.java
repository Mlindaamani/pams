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
    return new WebhookSubscriptionResponse(
        subscription.getId(),
        subscription.getUrl(),
        subscription.getDescription(),
        List.copyOf(subscription.eventTypeSet()),
        subscription.isActive(),
        subscription.getLastSuccessAt(),
        subscription.getLastFailureAt(),
        subscription.getCreatedAt(),
        subscription.getUpdatedAt(),
        subscription.getVersion());
  }

  public WebhookDeliveryResponse toResponse(WebhookDelivery delivery) {
    return new WebhookDeliveryResponse(
        delivery.getId(),
        delivery.getSubscriptionId(),
        delivery.getEventId(),
        delivery.getEventType(),
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
