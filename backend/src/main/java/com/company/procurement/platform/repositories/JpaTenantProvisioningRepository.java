package com.company.procurement.platform.repositories;

import com.company.procurement.platform.domain.TenantProvisioning;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaTenantProvisioningRepository extends JpaRepository<TenantProvisioning, UUID> {

  Optional<TenantProvisioning> findByOrganizationId(UUID organizationId);

  List<TenantProvisioning> findByOrganizationIdIn(Collection<UUID> organizationIds);
}
