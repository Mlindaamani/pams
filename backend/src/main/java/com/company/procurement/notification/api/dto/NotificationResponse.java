package com.company.procurement.notification.api.dto;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
    UUID id,
    String type,
    String title,
    String body,
    String recipientSubject,
    Instant readAt,
    Instant createdAt) {}
