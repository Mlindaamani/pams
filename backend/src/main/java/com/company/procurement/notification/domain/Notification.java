package com.company.procurement.notification.domain;

import com.company.procurement.shared.domain.TenantEntity;
import com.company.procurement.shared.domain.TenantFilter;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "notification")
@Filter(name = TenantFilter.NAME, condition = "organization_id = :organizationId")
public class Notification extends TenantEntity {

  @Column(name = "recipient_subject", nullable = false, length = 200)
  private String recipientSubject;

  @Column(name = "recipient_email", length = 320)
  private String recipientEmail;

  @Column(name = "type", nullable = false, length = 50)
  private String type;

  @Column(name = "title", nullable = false, length = 300)
  private String title;

  @Column(name = "body", nullable = false, length = 2000)
  private String body;

  @Column(name = "event_id", nullable = false, updatable = false)
  private UUID eventId;

  @Column(name = "read_at")
  private Instant readAt;

  protected Notification() {
    // for JPA
  }

  public Notification(
      UUID organizationId,
      String recipientSubject,
      String recipientEmail,
      String type,
      String title,
      String body,
      UUID eventId) {
    super(organizationId);
    this.recipientSubject = Objects.requireNonNull(recipientSubject, "recipientSubject");
    this.recipientEmail = recipientEmail;
    this.type = Objects.requireNonNull(type, "type");
    this.title = Objects.requireNonNull(title, "title");
    this.body = Objects.requireNonNull(body, "body");
    this.eventId = Objects.requireNonNull(eventId, "eventId");
  }

  public void markRead(Instant now) {
    if (readAt == null) {
      readAt = now;
    }
  }

  public String getRecipientSubject() {
    return recipientSubject;
  }

  public String getRecipientEmail() {
    return recipientEmail;
  }

  public String getType() {
    return type;
  }

  public String getTitle() {
    return title;
  }

  public String getBody() {
    return body;
  }

  public UUID getEventId() {
    return eventId;
  }

  public Instant getReadAt() {
    return readAt;
  }
}
