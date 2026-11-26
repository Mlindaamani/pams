package com.company.procurement.platform.repositories;

import com.company.procurement.platform.domain.TenantProvisioning;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence port for tenant administrator provisioning state. */
public interface TenantProvisioningRepository {

  Optional<TenantProvisioning> findByOrganizationId(UUID organizationId);

  List<TenantProvisioning> findByOrganizationIdIn(Collection<UUID> organizationIds);

  TenantProvisioning save(TenantProvisioning provisioning);
}
