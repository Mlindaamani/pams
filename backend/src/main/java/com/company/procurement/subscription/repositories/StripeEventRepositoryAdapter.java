package com.company.procurement.subscription.repositories;

import java.time.Instant;
import org.springframework.stereotype.Repository;

@Repository
public class StripeEventRepositoryAdapter implements StripeEventRepository {

  private final JpaStripeEventRepository jpa;

  public StripeEventRepositoryAdapter(JpaStripeEventRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public boolean recordIfAbsent(
      String eventId, String eventType, Instant processedAt, String payload) {
    return jpa.recordIfAbsent(eventId, eventType, processedAt, payload) > 0;
  }
}
