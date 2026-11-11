package com.company.procurement.notification.infrastructure;

import com.company.procurement.notification.domain.Notification;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaNotificationRepository
    extends JpaRepository<Notification, UUID>, JpaSpecificationExecutor<Notification> {

  Optional<Notification> findByIdAndOrganizationIdAndRecipientSubject(
      UUID id, UUID organizationId, String recipientSubject);

  long countByOrganizationIdAndRecipientSubjectAndReadAtIsNull(
      UUID organizationId, String recipientSubject);

  boolean existsByOrganizationIdAndEventIdAndRecipientSubject(
      UUID organizationId, UUID eventId, String recipientSubject);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      update Notification n
         set n.readAt = :now
       where n.organizationId = :organizationId
         and n.recipientSubject = :recipientSubject
         and n.readAt is null
      """)
  int markAllRead(
      @Param("organizationId") UUID organizationId,
      @Param("recipientSubject") String recipientSubject,
      @Param("now") Instant now);
}
