package com.company.procurement.webhook.infrastructure;

import com.company.procurement.webhook.domain.WebhookDelivery;
import com.company.procurement.webhook.domain.WebhookDeliveryRepository;
import com.company.procurement.webhook.domain.WebhookDeliveryStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
public class WebhookDeliveryRepositoryAdapter implements WebhookDeliveryRepository {

  private final JpaWebhookDeliveryRepository jpa;

  public WebhookDeliveryRepositoryAdapter(JpaWebhookDeliveryRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public WebhookDelivery save(WebhookDelivery delivery) {
    return jpa.save(delivery);
  }

  @Override
  public Optional<WebhookDelivery> findByIdAndOrganizationId(UUID id, UUID organizationId) {
    return jpa.findByIdAndOrganizationId(id, organizationId);
  }

  @Override
  public List<WebhookDelivery> findDue(Instant now, int limit) {
    return jpa.findDue(WebhookDeliveryStatus.PENDING, now, PageRequest.of(0, limit));
  }

  @Override
  public List<WebhookDelivery> findBySubscription(
      UUID organizationId, UUID subscriptionId, int limit) {
    return jpa.findByOrganizationIdAndSubscriptionIdOrderByCreatedAtDesc(
        organizationId, subscriptionId, PageRequest.of(0, limit));
  }

  @Override
  public boolean existsBySubscriptionAndEvent(UUID subscriptionId, UUID eventId) {
    return jpa.existsBySubscriptionIdAndEventId(subscriptionId, eventId);
  }
}
