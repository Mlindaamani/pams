package com.company.procurement.notification.infrastructure;

import com.company.procurement.notification.domain.Notification;
import com.company.procurement.notification.domain.NotificationRepository;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

@Repository
public class NotificationRepositoryAdapter implements NotificationRepository {

  private final JpaNotificationRepository jpa;

  public NotificationRepositoryAdapter(JpaNotificationRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public Notification save(Notification notification) {
    return jpa.save(notification);
  }

  @Override
  public Optional<Notification> findByIdAndOrganizationIdAndRecipientSubject(
      UUID id, UUID organizationId, String recipientSubject) {
    return jpa.findByIdAndOrganizationIdAndRecipientSubject(id, organizationId, recipientSubject);
  }

  @Override
  public Page<Notification> search(
      UUID organizationId, String recipientSubject, Boolean unread, Pageable pageable) {
    return jpa.findAll(specification(organizationId, recipientSubject, unread), pageable);
  }

  @Override
  public long countByOrganizationIdAndRecipientSubjectAndReadAtIsNull(
      UUID organizationId, String recipientSubject) {
    return jpa.countByOrganizationIdAndRecipientSubjectAndReadAtIsNull(
        organizationId, recipientSubject);
  }

  @Override
  public boolean existsByOrganizationIdAndEventIdAndRecipientSubject(
      UUID organizationId, UUID eventId, String recipientSubject) {
    return jpa.existsByOrganizationIdAndEventIdAndRecipientSubject(
        organizationId, eventId, recipientSubject);
  }

  @Override
  public int markAllRead(UUID organizationId, String recipientSubject, java.time.Instant now) {
    return jpa.markAllRead(organizationId, recipientSubject, now);
  }

  private static Specification<Notification> specification(
      UUID organizationId, String recipientSubject, Boolean unread) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      predicates.add(cb.equal(root.get("organizationId"), organizationId));
      predicates.add(cb.equal(root.get("recipientSubject"), recipientSubject));
      if (Boolean.TRUE.equals(unread)) {
        predicates.add(cb.isNull(root.get("readAt")));
      }
      return cb.and(predicates.toArray(Predicate[]::new));
    };
  }
}
