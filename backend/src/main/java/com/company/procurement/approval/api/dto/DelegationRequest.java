package com.company.procurement.approval.api.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record DelegationRequest(
    @NotNull(message = "delegateUserId is required") UUID delegateUserId,
    @NotNull(message = "startsAt is required") Instant startsAt,
    @NotNull(message = "endsAt is required") Instant endsAt) {}
