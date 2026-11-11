package com.company.procurement.webhook.domain;

import com.company.procurement.shared.domain.TenantEntity;
import com.company.procurement.shared.domain.TenantFilter;
import com.company.procurement.shared.error.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "webhook_subscription")
@Filter(name = TenantFilter.NAME, condition = "organization_id = :organizationId")
public class WebhookSubscription extends TenantEntity {

  @Column(name = "url", nullable = false, length = 1000)
  private String url;

  @Column(name = "description", length = 300)
  private String description;

  @Column(name = "secret", nullable = false, length = 100)
  private String secret;

  @Column(name = "event_types", nullable = false, length = 500)
  private String eventTypes;

  @Column(name = "active", nullable = false)
  private boolean active;

  @Column(name = "last_success_at")
  private Instant lastSuccessAt;

  @Column(name = "last_failure_at")
  private Instant lastFailureAt;

  protected WebhookSubscription() {
    // for JPA
  }

  public WebhookSubscription(
      java.util.UUID organizationId,
      String url,
      String description,
      Collection<String> eventTypes,
      String secret,
      boolean active) {
    super(organizationId);
    this.url = normalizeUrl(url);
    this.description = blankToNull(description);
    this.eventTypes = normalizeEventTypes(eventTypes);
    this.secret = Objects.requireNonNull(secret, "secret");
    this.active = active;
  }

  public void update(
      String url, String description, Collection<String> eventTypes, boolean active) {
    this.url = normalizeUrl(url);
    this.description = blankToNull(description);
    this.eventTypes = normalizeEventTypes(eventTypes);
    this.active = active;
  }

  public void rotateSecret(String newSecret) {
    this.secret = Objects.requireNonNull(newSecret, "secret");
  }

  public void markDeliverySuccess(Instant now) {
    this.lastSuccessAt = now;
  }

  public void markDeliveryFailure(Instant now) {
    this.lastFailureAt = now;
  }

  public boolean subscribesTo(String eventType) {
    return eventTypeSet().contains(eventType);
  }

  public Set<String> eventTypeSet() {
    if (eventTypes == null || eventTypes.isBlank()) {
      return Set.of();
    }
    return Arrays.stream(eventTypes.split(","))
        .map(String::trim)
        .filter(value -> !value.isEmpty())
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  public String getUrl() {
    return url;
  }

  public String getDescription() {
    return description;
  }

  public String getSecret() {
    return secret;
  }

  public String getEventTypes() {
    return eventTypes;
  }

  public boolean isActive() {
    return active;
  }

  public Instant getLastSuccessAt() {
    return lastSuccessAt;
  }

  public Instant getLastFailureAt() {
    return lastFailureAt;
  }

  private static String normalizeUrl(String value) {
    String trimmed = Objects.requireNonNull(value, "url").trim();
    try {
      URI uri = new URI(trimmed);
      if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
        throw new BusinessRuleException("Webhook URL must use http or https");
      }
      if (uri.getHost() == null || uri.getHost().isBlank() || uri.getUserInfo() != null) {
        throw new BusinessRuleException("Webhook URL must be an absolute URL without credentials");
      }
      return trimmed;
    } catch (URISyntaxException ex) {
      throw new BusinessRuleException("Webhook URL is invalid");
    }
  }

  private static String normalizeEventTypes(Collection<String> values) {
    if (values == null || values.isEmpty()) {
      throw new BusinessRuleException("At least one webhook event type is required");
    }
    Set<String> normalized =
        values.stream()
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .collect(Collectors.toCollection(LinkedHashSet::new));
    if (normalized.isEmpty()) {
      throw new BusinessRuleException("At least one webhook event type is required");
    }
    if (!WebhookEventTypes.ALL.containsAll(normalized)) {
      Set<String> unsupported = new LinkedHashSet<>(normalized);
      unsupported.removeAll(WebhookEventTypes.ALL);
      throw new BusinessRuleException(
          "Unsupported webhook event type(s): " + String.join(", ", unsupported));
    }
    return String.join(",", normalized);
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
