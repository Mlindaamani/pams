package com.company.procurement.webhook.repositories;

import com.company.procurement.webhook.domain.WebhookSubscription;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class WebhookSubscriptionRepositoryAdapter implements WebhookSubscriptionRepository {

  private final JpaWebhookSubscriptionRepository jpa;

  public WebhookSubscriptionRepositoryAdapter(JpaWebhookSubscriptionRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public WebhookSubscription save(WebhookSubscription subscription) {
    return jpa.save(subscription);
  }

  @Override
  public Optional<WebhookSubscription> findByIdAndOrganizationId(UUID id, UUID organizationId) {
    return jpa.findByIdAndOrganizationId(id, organizationId);
  }

  @Override
  public List<WebhookSubscription> findAllByOrganizationId(UUID organizationId) {
    return jpa.findAllByOrganizationIdOrderByCreatedAtDesc(organizationId);
  }

  @Override
  public List<WebhookSubscription> findActiveByOrganizationId(UUID organizationId) {
    return jpa.findAllByOrganizationIdAndActiveTrue(organizationId);
  }

  @Override
  public void delete(WebhookSubscription subscription) {
    jpa.delete(subscription);
  }

  @Override
  public boolean existsByUrl(UUID organizationId, String url) {
    return jpa.existsByOrganizationIdAndUrlIgnoreCase(organizationId, url.trim());
  }
}
