package com.company.procurement.notification.application;

import com.company.procurement.notification.domain.Notification;
import com.company.procurement.notification.domain.NotificationRepository;
import com.company.procurement.shared.error.ResourceNotFoundException;
import com.company.procurement.shared.security.CurrentUser;
import com.company.procurement.shared.tenant.TenantContext;
import java.time.Clock;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Manages in-app notifications and unread state. */
@Service
public class NotificationService {

  private final NotificationRepository notifications;
  private final CurrentUser currentUser;
  private final Clock clock;

  public NotificationService(
      NotificationRepository notifications, CurrentUser currentUser, Clock clock) {
    this.notifications = notifications;
    this.currentUser = currentUser;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public Page<Notification> list(Boolean unread, Pageable pageable) {
    return notifications.search(TenantContext.require(), currentUser.subject(), unread, pageable);
  }

  @Transactional(readOnly = true)
  public long unreadCount() {
    return notifications.countByOrganizationIdAndRecipientSubjectAndReadAtIsNull(
        TenantContext.require(), currentUser.subject());
  }

  @Transactional
  public int markAllRead() {
    return notifications.markAllRead(
        TenantContext.require(), currentUser.subject(), clock.instant());
  }

  @Transactional
  public Notification markRead(UUID id) {
    Notification notification =
        notifications
            .findByIdAndOrganizationIdAndRecipientSubject(
                id, TenantContext.require(), currentUser.subject())
            .orElseThrow(
                () -> new ResourceNotFoundException("Notification %s not found".formatted(id)));
    notification.markRead(clock.instant());
    return notifications.save(notification);
  }

  @Transactional
  public boolean createIfAbsent(
      UUID organizationId,
      UUID eventId,
      String recipientSubject,
      String recipientEmail,
      String type,
      String title,
      String body) {
    if (notifications.existsByOrganizationIdAndEventIdAndRecipientSubject(
        organizationId, eventId, recipientSubject)) {
      return false;
    }
    notifications.save(
        new Notification(
            organizationId, recipientSubject, recipientEmail, type, title, body, eventId));
    return true;
  }
}
