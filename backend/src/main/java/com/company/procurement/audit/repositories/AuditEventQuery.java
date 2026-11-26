package com.company.procurement.audit.repositories;

import java.time.Instant;
import java.util.UUID;

/** Filter and keyset cursor for audit event reads. */
public record AuditEventQuery(
    UUID organizationId,
    String aggregateType,
    UUID aggregateId,
    String action,
    Instant from,
    Instant to,
    Instant beforeOccurredAt,
    UUID beforeId) {}
