package com.company.procurement.approval.application;

import java.time.Instant;
import java.util.UUID;

public record CreateDelegationCommand(UUID delegateUserId, Instant startsAt, Instant endsAt) {}
