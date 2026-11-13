package com.company.procurement.webhook.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WebhookSubscriptionResponse(
    UUID id,
    String name,
    String url,
    String description,
    List<String> eventTypes,
    boolean active,
    Instant lastSuccessAt,
    Instant lastFailureAt,
    String signingMethod,
    int timeoutSeconds,
    int maxAttempts,
    long deliveryCount,
    Instant createdAt,
    Instant updatedAt,
    long version) {}
