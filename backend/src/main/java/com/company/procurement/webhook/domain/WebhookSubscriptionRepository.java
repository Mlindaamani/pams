package com.company.procurement.webhook.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WebhookSubscriptionRepository {

  WebhookSubscription save(WebhookSubscription subscription);

  Optional<WebhookSubscription> findByIdAndOrganizationId(UUID id, UUID organizationId);

  List<WebhookSubscription> findAllByOrganizationId(UUID organizationId);

  List<WebhookSubscription> findActiveByOrganizationId(UUID organizationId);

  void delete(WebhookSubscription subscription);

  boolean existsByUrl(UUID organizationId, String url);
}
