package com.company.procurement.approval.services;

import java.time.Instant;
import java.util.UUID;

public record CreateDelegationCommand(UUID delegateUserId, Instant startsAt, Instant endsAt) {}
