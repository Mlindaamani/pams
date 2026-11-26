package com.company.procurement.shared.usage;

import java.util.UUID;

/** Read-only tenant onboarding readiness counters. */
public interface TenantReadinessPort {

  ReadinessSnapshot readiness(UUID organizationId);

  record ReadinessSnapshot(
      long departments,
      long activeUsers,
      long activePolicies,
      long nonDraftRequests,
      long nonDraftOrders) {}
}
