package com.company.procurement.webhook.infrastructure;

import com.company.procurement.webhook.domain.WebhookDelivery;
import com.company.procurement.webhook.domain.WebhookDeliveryStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaWebhookDeliveryRepository extends JpaRepository<WebhookDelivery, UUID> {

  Optional<WebhookDelivery> findByIdAndOrganizationId(UUID id, UUID organizationId);

  boolean existsBySubscriptionIdAndEventId(UUID subscriptionId, UUID eventId);

  @Query(
      """
      select d from WebhookDelivery d
      where d.status = :status and d.nextAttemptAt <= :now
      order by d.nextAttemptAt asc
      """)
  List<WebhookDelivery> findDue(
      @Param("status") WebhookDeliveryStatus status, @Param("now") Instant now, Pageable pageable);

  List<WebhookDelivery> findByOrganizationIdAndSubscriptionIdOrderByCreatedAtDesc(
      UUID organizationId, UUID subscriptionId, Pageable pageable);
}
