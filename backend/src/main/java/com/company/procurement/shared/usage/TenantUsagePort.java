package com.company.procurement.shared.usage;

import java.util.UUID;

/** Read-only tenant resource usage for subscription entitlement checks. */
public interface TenantUsagePort {

  UsageSnapshot usage(UUID organizationId);

  record UsageSnapshot(long users, long departments, long requestsThisMonth, long storageBytes) {}
}
