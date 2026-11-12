package com.company.procurement.webhook.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface WebhookDeliveryRepository {

  WebhookDelivery save(WebhookDelivery delivery);

  Optional<WebhookDelivery> findByIdAndOrganizationId(UUID id, UUID organizationId);

  List<WebhookDelivery> findDue(Instant now, int limit);

  List<WebhookDelivery> findBySubscription(UUID organizationId, UUID subscriptionId, int limit);

  Page<WebhookDelivery> search(
      UUID organizationId,
      UUID subscriptionId,
      WebhookDeliveryStatus status,
      String eventType,
      Pageable pageable);

  long countBySubscription(UUID organizationId, UUID subscriptionId);

  boolean existsBySubscriptionAndEvent(UUID subscriptionId, UUID eventId);
}
