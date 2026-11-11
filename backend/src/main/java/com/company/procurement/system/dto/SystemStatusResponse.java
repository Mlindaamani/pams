package com.company.procurement.system.dto;

import java.time.Instant;

public record SystemStatusResponse(
    String status,
    String database,
    long outboxPending,
    long outboxDeadLettered,
    long pendingApprovals,
    long unreadNotifications,
    String version,
    String buildTime,
    Instant timestamp) {}
