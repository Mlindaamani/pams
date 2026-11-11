package com.company.procurement.vendor.infrastructure;

import com.company.procurement.vendor.domain.Vendor;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface JpaVendorRepository
    extends JpaRepository<Vendor, UUID>, JpaSpecificationExecutor<Vendor> {

  Optional<Vendor> findByIdAndOrganizationId(UUID id, UUID organizationId);

  boolean existsByOrganizationIdAndTaxId(UUID organizationId, String taxId);

  boolean existsByOrganizationIdAndTaxIdAndIdNot(UUID organizationId, String taxId, UUID id);
}
