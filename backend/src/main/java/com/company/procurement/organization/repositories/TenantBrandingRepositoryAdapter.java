package com.company.procurement.organization.repositories;

import com.company.procurement.organization.domain.TenantBranding;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class TenantBrandingRepositoryAdapter implements TenantBrandingRepository {

  private final JpaTenantBrandingRepository jpa;

  public TenantBrandingRepositoryAdapter(JpaTenantBrandingRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public Optional<TenantBranding> findById(UUID organizationId) {
    return jpa.findById(organizationId);
  }

  @Override
  public TenantBranding save(TenantBranding branding) {
    return jpa.save(branding);
  }
}
