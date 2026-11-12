package com.company.procurement.webhook.infrastructure;

import com.company.procurement.webhook.domain.WebhookDelivery;
import com.company.procurement.webhook.domain.WebhookDeliveryRepository;
import com.company.procurement.webhook.domain.WebhookDeliveryStatus;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
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
  public Page<WebhookDelivery> search(
      UUID organizationId,
      UUID subscriptionId,
      WebhookDeliveryStatus status,
      String eventType,
      Pageable pageable) {
    return jpa.findAll(specification(organizationId, subscriptionId, status, eventType), pageable);
  }

  @Override
  public long countBySubscription(UUID organizationId, UUID subscriptionId) {
    return jpa.countByOrganizationIdAndSubscriptionId(organizationId, subscriptionId);
  }

  @Override
  public boolean existsBySubscriptionAndEvent(UUID subscriptionId, UUID eventId) {
    return jpa.existsBySubscriptionIdAndEventId(subscriptionId, eventId);
  }

  private static Specification<WebhookDelivery> specification(
      UUID organizationId, UUID subscriptionId, WebhookDeliveryStatus status, String eventType) {
    return (root, query, cb) -> {
      var predicates = new ArrayList<Predicate>();
      predicates.add(cb.equal(root.get("organizationId"), organizationId));
      if (subscriptionId != null) {
        predicates.add(cb.equal(root.get("subscriptionId"), subscriptionId));
      }
      if (status != null) {
        predicates.add(cb.equal(root.get("status"), status));
      }
      if (eventType != null && !eventType.isBlank()) {
        predicates.add(cb.equal(root.get("eventType"), eventType));
      }
      return cb.and(predicates.toArray(Predicate[]::new));
    };
  }
}
