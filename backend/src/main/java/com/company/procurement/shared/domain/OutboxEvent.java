package com.company.procurement.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "outbox_event")
public class OutboxEvent extends TenantEntity {

  @Column(name = "aggregate_type", nullable = false, length = 50)
  private String aggregateType;

  @Column(name = "aggregate_id", nullable = false)
  private UUID aggregateId;

  @Column(name = "event_type", nullable = false, length = 100)
  private String eventType;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
  private String payload;

  @Column(name = "attempts", nullable = false)
  private int attempts;

  @Column(name = "next_attempt_at", nullable = false)
  private Instant nextAttemptAt;

  @Column(name = "processed_at")
  private Instant processedAt;

  @Column(name = "dead_lettered_at")
  private Instant deadLetteredAt;

  protected OutboxEvent() {
    // for JPA
  }

  public OutboxEvent(
      UUID organizationId,
      String aggregateType,
      UUID aggregateId,
      String eventType,
      String payload,
      Instant nextAttemptAt) {
    super(organizationId);
    this.aggregateType = Objects.requireNonNull(aggregateType, "aggregateType");
    this.aggregateId = Objects.requireNonNull(aggregateId, "aggregateId");
    this.eventType = Objects.requireNonNull(eventType, "eventType");
    this.payload = Objects.requireNonNull(payload, "payload");
    this.nextAttemptAt = Objects.requireNonNull(nextAttemptAt, "nextAttemptAt");
  }

  public void markProcessed(Instant now) {
    this.processedAt = now;
  }

  public void recordFailure(Instant now, int maxAttempts) {
    this.attempts++;
    if (this.attempts >= maxAttempts) {
      this.deadLetteredAt = now;
    } else {
      long backoffSeconds = Math.min(3600L, (1L << Math.min(attempts, 10)) * 30L);
      this.nextAttemptAt = now.plusSeconds(backoffSeconds);
    }
  }

  public boolean isPending() {
    return processedAt == null && deadLetteredAt == null;
  }

  public String getAggregateType() {
    return aggregateType;
  }

  public UUID getAggregateId() {
    return aggregateId;
  }

  public String getEventType() {
    return eventType;
  }

  public String getPayload() {
    return payload;
  }

  public int getAttempts() {
    return attempts;
  }

  public Instant getNextAttemptAt() {
    return nextAttemptAt;
  }

  public Instant getProcessedAt() {
    return processedAt;
  }

  public Instant getDeadLetteredAt() {
    return deadLetteredAt;
  }
}
