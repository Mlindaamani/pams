package com.company.procurement.webhook.domain;

import com.company.procurement.shared.domain.TenantEntity;
import com.company.procurement.shared.domain.TenantFilter;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "webhook_delivery")
@Filter(name = TenantFilter.NAME, condition = "organization_id = :organizationId")
public class WebhookDelivery extends TenantEntity {

  @Column(name = "subscription_id", nullable = false, updatable = false)
  private UUID subscriptionId;

  @Column(name = "event_id", nullable = false, updatable = false)
  private UUID eventId;

  @Column(name = "event_type", nullable = false, updatable = false, length = 100)
  private String eventType;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
  private String payload;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 30)
  private WebhookDeliveryStatus status;

  @Column(name = "attempts", nullable = false)
  private int attempts;

  @Column(name = "next_attempt_at", nullable = false)
  private Instant nextAttemptAt;

  @Column(name = "last_attempt_at")
  private Instant lastAttemptAt;

  @Column(name = "response_status")
  private Integer responseStatus;

  @Column(name = "response_body", length = 2000)
  private String responseBody;

  @Column(name = "error_message", length = 1000)
  private String errorMessage;

  @Column(name = "duration_ms")
  private Integer durationMs;

  protected WebhookDelivery() {
    // for JPA
  }

  public WebhookDelivery(
      UUID organizationId,
      UUID subscriptionId,
      UUID eventId,
      String eventType,
      String payload,
      Instant nextAttemptAt) {
    super(organizationId);
    this.subscriptionId = Objects.requireNonNull(subscriptionId, "subscriptionId");
    this.eventId = Objects.requireNonNull(eventId, "eventId");
    this.eventType = Objects.requireNonNull(eventType, "eventType");
    this.payload = Objects.requireNonNull(payload, "payload");
    this.status = WebhookDeliveryStatus.PENDING;
    this.nextAttemptAt = Objects.requireNonNull(nextAttemptAt, "nextAttemptAt");
  }

  public void markSucceeded(Instant now, int responseStatus, String responseBody, int durationMs) {
    this.status = WebhookDeliveryStatus.SUCCEEDED;
    this.attempts++;
    this.lastAttemptAt = now;
    this.responseStatus = responseStatus;
    this.responseBody = truncate(responseBody, 2000);
    this.errorMessage = null;
    this.durationMs = durationMs;
  }

  public void recordFailure(
      Instant now,
      int responseStatus,
      String responseBody,
      String error,
      int durationMs,
      int maxAttempts) {
    this.attempts++;
    this.lastAttemptAt = now;
    this.responseStatus = responseStatus == 0 ? null : responseStatus;
    this.responseBody = truncate(responseBody, 2000);
    this.errorMessage = truncate(error, 1000);
    this.durationMs = durationMs;
    if (attempts >= maxAttempts) {
      this.status = WebhookDeliveryStatus.DEAD_LETTER;
    } else {
      this.status = WebhookDeliveryStatus.PENDING;
      long backoffSeconds = Math.min(3600L, 30L * (1L << Math.min(attempts, 7)));
      this.nextAttemptAt = now.plusSeconds(backoffSeconds);
    }
  }

  public void retry(Instant now) {
    this.status = WebhookDeliveryStatus.PENDING;
    this.attempts = 0;
    this.nextAttemptAt = now;
    this.responseStatus = null;
    this.responseBody = null;
    this.errorMessage = null;
    this.durationMs = null;
  }

  public UUID getSubscriptionId() {
    return subscriptionId;
  }

  public UUID getEventId() {
    return eventId;
  }

  public String getEventType() {
    return eventType;
  }

  public String getPayload() {
    return payload;
  }

  public WebhookDeliveryStatus getStatus() {
    return status;
  }

  public int getAttempts() {
    return attempts;
  }

  public Instant getNextAttemptAt() {
    return nextAttemptAt;
  }

  public Instant getLastAttemptAt() {
    return lastAttemptAt;
  }

  public Integer getResponseStatus() {
    return responseStatus;
  }

  public String getResponseBody() {
    return responseBody;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public Integer getDurationMs() {
    return durationMs;
  }

  private static String truncate(String value, int maxLength) {
    if (value == null) {
      return null;
    }
    return value.length() <= maxLength ? value : value.substring(0, maxLength);
  }
}
