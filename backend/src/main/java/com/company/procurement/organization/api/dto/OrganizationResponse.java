package com.company.procurement.organization.api.dto;

import com.company.procurement.organization.domain.OrganizationStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrganizationResponse(
    UUID id,
    String name,
    String slug,
    BigDecimal invoiceTolerancePercent,
    OrganizationStatus status,
    Instant suspendedAt,
    String suspendedBy,
    Instant createdAt,
    Instant updatedAt,
    long version) {}
