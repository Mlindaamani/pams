package com.company.procurement.subscription.repositories;

import com.company.procurement.subscription.domain.StripeEvent;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaStripeEventRepository extends JpaRepository<StripeEvent, String> {

  @Modifying
  @Query(
      value =
          """
          insert into stripe_event (event_id, event_type, processed_at, payload)
          values (:eventId, :eventType, :processedAt, cast(:payload as jsonb))
          on conflict (event_id) do nothing
          """,
      nativeQuery = true)
  int recordIfAbsent(
      @Param("eventId") String eventId,
      @Param("eventType") String eventType,
      @Param("processedAt") Instant processedAt,
      @Param("payload") String payload);
}
