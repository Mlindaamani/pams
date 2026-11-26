package com.company.procurement.organization.repositories;

import com.company.procurement.organization.domain.TenantBranding;
import java.util.Optional;
import java.util.UUID;

/** Persistence port for tenant branding. */
public interface TenantBrandingRepository {

  Optional<TenantBranding> findById(UUID organizationId);

  TenantBranding save(TenantBranding branding);
}
