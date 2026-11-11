package com.company.procurement.webhook.application;

import com.company.procurement.shared.domain.OutboxEvent;
import com.company.procurement.shared.outbox.OutboxEventHandler;
import com.company.procurement.webhook.domain.WebhookDelivery;
import com.company.procurement.webhook.domain.WebhookDeliveryRepository;
import com.company.procurement.webhook.domain.WebhookEventTypes;
import com.company.procurement.webhook.domain.WebhookSubscriptionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class WebhookOutboxHandler implements OutboxEventHandler {

  private final WebhookSubscriptionRepository subscriptions;
  private final WebhookDeliveryRepository deliveries;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  public WebhookOutboxHandler(
      WebhookSubscriptionRepository subscriptions,
      WebhookDeliveryRepository deliveries,
      ObjectMapper objectMapper,
      Clock clock) {
    this.subscriptions = subscriptions;
    this.deliveries = deliveries;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  @Override
  public boolean supports(String eventType) {
    return WebhookEventTypes.ALL.contains(eventType);
  }

  @Override
  public void handle(OutboxEvent event, Map<String, Object> payload) {
    String envelope = envelope(event, payload);
    subscriptions.findActiveByOrganizationId(event.getOrganizationId()).stream()
        .filter(subscription -> subscription.subscribesTo(event.getEventType()))
        .filter(
            subscription ->
                !deliveries.existsBySubscriptionAndEvent(subscription.getId(), event.getId()))
        .forEach(
            subscription ->
                deliveries.save(
                    new WebhookDelivery(
                        event.getOrganizationId(),
                        subscription.getId(),
                        event.getId(),
                        event.getEventType(),
                        envelope,
                        clock.instant())));
  }

  private String envelope(OutboxEvent event, Map<String, Object> payload) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("id", event.getId().toString());
    body.put("type", event.getEventType());
    body.put("createdAt", clock.instant().toString());
    body.put("data", payload);
    try {
      return objectMapper.writeValueAsString(body);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Could not serialize webhook payload", ex);
    }
  }
}
