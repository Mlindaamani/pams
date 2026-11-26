package com.company.procurement.shared.tenant;

import java.util.UUID;

/** Tenant write policy supplied by the subscription module. */
public interface TenantWriteAccessPort {

  void assertWriteAllowed(UUID organizationId);
}
