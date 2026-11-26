package com.company.procurement.subscription.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Processed Stripe webhook event for idempotency. */
@Entity
@Table(name = "stripe_event")
public class StripeEvent {

  @Id
  @Column(name = "event_id", nullable = false, length = 200)
  private String eventId;

  @Column(name = "event_type", nullable = false, length = 100)
  private String eventType;

  @Column(name = "processed_at", nullable = false)
  private Instant processedAt;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private String payload;

  protected StripeEvent() {
    // for JPA
  }

  public StripeEvent(String eventId, String eventType, Instant processedAt, String payload) {
    this.eventId = Objects.requireNonNull(eventId, "eventId");
    this.eventType = Objects.requireNonNull(eventType, "eventType");
    this.processedAt = Objects.requireNonNull(processedAt, "processedAt");
    this.payload = Objects.requireNonNull(payload, "payload");
  }

  public String getEventId() {
    return eventId;
  }

  public String getEventType() {
    return eventType;
  }

  public Instant getProcessedAt() {
    return processedAt;
  }

  public String getPayload() {
    return payload;
  }
}
