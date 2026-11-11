package com.company.procurement.audit.application.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

public record AuditEventResponse(
    UUID id,
    String aggregateType,
    UUID aggregateId,
    String action,
    String actorSubject,
    String actorName,
    JsonNode payload,
    Instant occurredAt) {}
