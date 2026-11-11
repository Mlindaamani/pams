package com.company.procurement.webhook.application;

import com.company.procurement.audit.application.AuditEventService;
import com.company.procurement.shared.error.ConflictException;
import com.company.procurement.shared.error.PreconditionFailedException;
import com.company.procurement.shared.error.ResourceNotFoundException;
import com.company.procurement.shared.tenant.TenantContext;
import com.company.procurement.webhook.domain.WebhookDelivery;
import com.company.procurement.webhook.domain.WebhookDeliveryRepository;
import com.company.procurement.webhook.domain.WebhookSubscription;
import com.company.procurement.webhook.domain.WebhookSubscriptionRepository;
import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WebhookService {

  private final WebhookSubscriptionRepository subscriptions;
  private final WebhookDeliveryRepository deliveries;
  private final WebhookSecretGenerator secrets;
  private final AuditEventService audit;
  private final Clock clock;

  public WebhookService(
      WebhookSubscriptionRepository subscriptions,
      WebhookDeliveryRepository deliveries,
      WebhookSecretGenerator secrets,
      AuditEventService audit,
      Clock clock) {
    this.subscriptions = subscriptions;
    this.deliveries = deliveries;
    this.secrets = secrets;
    this.audit = audit;
    this.clock = clock;
  }

  @Transactional
  public CreatedSubscription create(CreateWebhookCommand command) {
    UUID organizationId = TenantContext.require();
    if (subscriptions.existsByUrl(organizationId, command.url())) {
      throw new ConflictException("A webhook with this URL already exists");
    }
    String secret = secrets.generate();
    WebhookSubscription subscription =
        new WebhookSubscription(
            organizationId,
            command.url(),
            command.description(),
            command.eventTypes(),
            secret,
            command.active());
    WebhookSubscription saved = subscriptions.save(subscription);
    audit.recordCurrentActor(
        "webhook_subscription",
        saved.getId(),
        "webhook_created",
        Map.of("url", saved.getUrl(), "eventTypes", saved.eventTypeSet()));
    return new CreatedSubscription(saved, secret);
  }

  @Transactional
  public WebhookSubscription update(UUID id, long expectedVersion, UpdateWebhookCommand command) {
    WebhookSubscription subscription = get(id);
    assertVersion(subscription, expectedVersion);
    if (!subscription.getUrl().equalsIgnoreCase(command.url())
        && subscriptions.existsByUrl(TenantContext.require(), command.url())) {
      throw new ConflictException("A webhook with this URL already exists");
    }
    subscription.update(
        command.url(), command.description(), command.eventTypes(), command.active());
    WebhookSubscription saved = subscriptions.save(subscription);
    audit.recordCurrentActor(
        "webhook_subscription",
        saved.getId(),
        "webhook_updated",
        Map.of("url", saved.getUrl(), "active", saved.isActive()));
    return saved;
  }

  @Transactional
  public RotatedSecret rotateSecret(UUID id, long expectedVersion) {
    WebhookSubscription subscription = get(id);
    assertVersion(subscription, expectedVersion);
    String secret = secrets.generate();
    subscription.rotateSecret(secret);
    WebhookSubscription saved = subscriptions.save(subscription);
    audit.recordCurrentActor(
        "webhook_subscription",
        saved.getId(),
        "webhook_secret_rotated",
        Map.of("url", saved.getUrl()));
    return new RotatedSecret(saved, secret);
  }

  @Transactional
  public void delete(UUID id, long expectedVersion) {
    WebhookSubscription subscription = get(id);
    assertVersion(subscription, expectedVersion);
    subscriptions.delete(subscription);
    audit.recordCurrentActor(
        "webhook_subscription", id, "webhook_deleted", Map.of("url", subscription.getUrl()));
  }

  @Transactional(readOnly = true)
  public List<WebhookSubscription> list() {
    return subscriptions.findAllByOrganizationId(TenantContext.require());
  }

  @Transactional(readOnly = true)
  public List<WebhookDelivery> deliveries(UUID subscriptionId, int limit) {
    UUID organizationId = TenantContext.require();
    get(subscriptionId);
    return deliveries.findBySubscription(
        organizationId, subscriptionId, Math.min(Math.max(limit, 1), 200));
  }

  @Transactional
  public WebhookDelivery retryDelivery(UUID deliveryId) {
    UUID organizationId = TenantContext.require();
    WebhookDelivery delivery =
        deliveries
            .findByIdAndOrganizationId(deliveryId, organizationId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Webhook delivery %s not found".formatted(deliveryId)));
    delivery.retry(clock.instant());
    WebhookDelivery saved = deliveries.save(delivery);
    audit.recordCurrentActor(
        "webhook_delivery",
        saved.getId(),
        "webhook_delivery_retried",
        Map.of("eventType", saved.getEventType()));
    return saved;
  }

  private WebhookSubscription get(UUID id) {
    return subscriptions
        .findByIdAndOrganizationId(id, TenantContext.require())
        .orElseThrow(
            () -> new ResourceNotFoundException("Webhook subscription %s not found".formatted(id)));
  }

  private static void assertVersion(WebhookSubscription subscription, long expectedVersion) {
    if (expectedVersion >= 0 && subscription.getVersion() != expectedVersion) {
      throw new PreconditionFailedException(
          "The webhook subscription was modified by another operation; reload and retry");
    }
  }

  public record CreateWebhookCommand(
      String url, String description, Collection<String> eventTypes, boolean active) {}

  public record UpdateWebhookCommand(
      String url, String description, Collection<String> eventTypes, boolean active) {}

  public record CreatedSubscription(WebhookSubscription subscription, String signingSecret) {}

  public record RotatedSecret(WebhookSubscription subscription, String signingSecret) {}
}
