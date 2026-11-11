package com.company.procurement.webhook.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WebhookDeliveryRepository {

  WebhookDelivery save(WebhookDelivery delivery);

  Optional<WebhookDelivery> findByIdAndOrganizationId(UUID id, UUID organizationId);

  List<WebhookDelivery> findDue(Instant now, int limit);

  List<WebhookDelivery> findBySubscription(UUID organizationId, UUID subscriptionId, int limit);

  boolean existsBySubscriptionAndEvent(UUID subscriptionId, UUID eventId);
}
