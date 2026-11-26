package com.company.procurement.platform.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Cross-tenant platform administration audit event. */
@Entity
@Table(name = "platform_audit_event")
public class PlatformAuditEvent {

  @Id @GeneratedValue private UUID id;

  @Column(name = "actor_subject", nullable = false, length = 200)
  private String actorSubject;

  @Column(name = "actor_name", nullable = false, length = 200)
  private String actorName;

  @Column(nullable = false, length = 100)
  private String action;

  @Column(name = "tenant_id")
  private UUID tenantId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private String payload;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  protected PlatformAuditEvent() {
    // for JPA
  }

  public PlatformAuditEvent(
      String actorSubject,
      String actorName,
      String action,
      UUID tenantId,
      String payload,
      Instant occurredAt) {
    this.actorSubject = Objects.requireNonNull(actorSubject, "actorSubject");
    this.actorName = Objects.requireNonNull(actorName, "actorName");
    this.action = Objects.requireNonNull(action, "action");
    this.tenantId = tenantId;
    this.payload = Objects.requireNonNull(payload, "payload");
    this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
  }

  public UUID getId() {
    return id;
  }

  public String getActorSubject() {
    return actorSubject;
  }

  public String getActorName() {
    return actorName;
  }

  public String getAction() {
    return action;
  }

  public UUID getTenantId() {
    return tenantId;
  }

  public String getPayload() {
    return payload;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }
}
