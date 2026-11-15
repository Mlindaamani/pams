package com.company.procurement.platform.api.dto;

import com.company.procurement.organization.api.dto.OrganizationResponse;
import com.company.procurement.organization.domain.OrganizationStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Tenant row enriched with the tenant administrator contact. */
public record PlatformTenantResponse(
    UUID id,
    String name,
    String slug,
    BigDecimal invoiceTolerancePercent,
    OrganizationStatus status,
    Instant suspendedAt,
    String suspendedBy,
    Instant createdAt,
    Instant updatedAt,
    long version,
    String adminName,
    String adminEmail) {

  public static PlatformTenantResponse from(
      OrganizationResponse tenant, String adminName, String adminEmail) {
    return new PlatformTenantResponse(
        tenant.id(),
        tenant.name(),
        tenant.slug(),
        tenant.invoiceTolerancePercent(),
        tenant.status(),
        tenant.suspendedAt(),
        tenant.suspendedBy(),
        tenant.createdAt(),
        tenant.updatedAt(),
        tenant.version(),
        adminName,
        adminEmail);
  }
}
