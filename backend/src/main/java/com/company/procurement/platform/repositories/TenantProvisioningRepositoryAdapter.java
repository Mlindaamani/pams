package com.company.procurement.platform.repositories;

import com.company.procurement.platform.domain.TenantProvisioning;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class TenantProvisioningRepositoryAdapter implements TenantProvisioningRepository {

  private final JpaTenantProvisioningRepository jpa;

  public TenantProvisioningRepositoryAdapter(JpaTenantProvisioningRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public Optional<TenantProvisioning> findByOrganizationId(UUID organizationId) {
    return jpa.findByOrganizationId(organizationId);
  }

  @Override
  public List<TenantProvisioning> findByOrganizationIdIn(Collection<UUID> organizationIds) {
    return jpa.findByOrganizationIdIn(organizationIds);
  }

  @Override
  public TenantProvisioning save(TenantProvisioning provisioning) {
    return jpa.save(provisioning);
  }
}
