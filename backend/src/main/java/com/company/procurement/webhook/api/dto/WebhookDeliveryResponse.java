package com.company.procurement.webhook.api.dto;

import com.company.procurement.webhook.domain.WebhookDeliveryStatus;
import java.time.Instant;
import java.util.UUID;

public record WebhookDeliveryResponse(
    UUID id,
    UUID subscriptionId,
    UUID eventId,
    String eventType,
    WebhookDeliveryStatus status,
    int attempts,
    Instant nextAttemptAt,
    Instant lastAttemptAt,
    Integer responseStatus,
    String responseBody,
    String errorMessage,
    Integer durationMs,
    Instant createdAt) {}
