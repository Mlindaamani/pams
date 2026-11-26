package com.company.procurement.organization.repositories;

import com.company.procurement.organization.domain.TenantBranding;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaTenantBrandingRepository extends JpaRepository<TenantBranding, UUID> {}
