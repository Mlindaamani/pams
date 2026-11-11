package com.company.procurement.webhook.infrastructure;

import com.company.procurement.webhook.domain.WebhookSubscription;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaWebhookSubscriptionRepository extends JpaRepository<WebhookSubscription, UUID> {

  Optional<WebhookSubscription> findByIdAndOrganizationId(UUID id, UUID organizationId);

  List<WebhookSubscription> findAllByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);

  List<WebhookSubscription> findAllByOrganizationIdAndActiveTrue(UUID organizationId);

  boolean existsByOrganizationIdAndUrlIgnoreCase(UUID organizationId, String url);
}
