package com.company.procurement.shared.tenant;

import java.util.UUID;

/** Tenant lifecycle check used by the request filter chain. */
public interface TenantAvailabilityPort {

  boolean isSuspended(UUID organizationId);
}
