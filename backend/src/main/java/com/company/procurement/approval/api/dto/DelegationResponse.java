package com.company.procurement.approval.api.dto;

import java.time.Instant;
import java.util.UUID;

public record DelegationResponse(
    UUID id,
    UUID delegatorUserId,
    UUID delegateUserId,
    Instant startsAt,
    Instant endsAt,
    boolean active) {}
