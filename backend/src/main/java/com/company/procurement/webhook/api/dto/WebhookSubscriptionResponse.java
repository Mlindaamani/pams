package com.company.procurement.webhook.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WebhookSubscriptionResponse(
    UUID id,
    String url,
    String description,
    List<String> eventTypes,
    boolean active,
    Instant lastSuccessAt,
    Instant lastFailureAt,
    long deliveryCount,
    Instant createdAt,
    Instant updatedAt,
    long version) {}
