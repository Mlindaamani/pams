package com.company.procurement.notification.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface NotificationRepository {

  Notification save(Notification notification);

  Optional<Notification> findByIdAndOrganizationIdAndRecipientSubject(
      UUID id, UUID organizationId, String recipientSubject);

  Page<Notification> search(
      UUID organizationId, String recipientSubject, Boolean unread, Pageable pageable);

  long countByOrganizationIdAndRecipientSubjectAndReadAtIsNull(
      UUID organizationId, String recipientSubject);

  boolean existsByOrganizationIdAndEventIdAndRecipientSubject(
      UUID organizationId, UUID eventId, String recipientSubject);

  int markAllRead(UUID organizationId, String recipientSubject, java.time.Instant now);
}
