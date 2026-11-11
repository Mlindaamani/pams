package com.company.procurement.audit.domain;

import com.company.procurement.shared.domain.TenantEntity;
import com.company.procurement.shared.domain.TenantFilter;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "audit_event")
@Filter(name = TenantFilter.NAME, condition = "organization_id = :organizationId")
public class AuditEvent extends TenantEntity {

  @Column(name = "aggregate_type", nullable = false, length = 50)
  private String aggregateType;

  @Column(name = "aggregate_id", nullable = false)
  private UUID aggregateId;

  @Column(name = "action", nullable = false, length = 80)
  private String action;

  @Column(name = "actor_subject", nullable = false, length = 200)
  private String actorSubject;

  @Column(name = "actor_name", nullable = false, length = 200)
  private String actorName;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
  private String payload;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  protected AuditEvent() {
    // for JPA
  }

  public AuditEvent(
      UUID organizationId,
      String aggregateType,
      UUID aggregateId,
      String action,
      String actorSubject,
      String actorName,
      String payload,
      Instant occurredAt) {
    super(organizationId);
    this.aggregateType = Objects.requireNonNull(aggregateType, "aggregateType");
    this.aggregateId = Objects.requireNonNull(aggregateId, "aggregateId");
    this.action = Objects.requireNonNull(action, "action");
    this.actorSubject = Objects.requireNonNull(actorSubject, "actorSubject");
    this.actorName = Objects.requireNonNull(actorName, "actorName");
    this.payload = Objects.requireNonNull(payload, "payload");
    this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
  }

  public String getAggregateType() {
    return aggregateType;
  }

  public UUID getAggregateId() {
    return aggregateId;
  }

  public String getAction() {
    return action;
  }

  public String getActorSubject() {
    return actorSubject;
  }

  public String getActorName() {
    return actorName;
  }

  public String getPayload() {
    return payload;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }
}
