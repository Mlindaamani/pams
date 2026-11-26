package com.company.procurement.subscription.repositories;

import java.time.Instant;

/** Persistence port for processed Stripe events. */
public interface StripeEventRepository {

  boolean recordIfAbsent(String eventId, String eventType, Instant processedAt, String payload);
}
