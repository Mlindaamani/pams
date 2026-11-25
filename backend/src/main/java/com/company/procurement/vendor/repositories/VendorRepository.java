package com.company.procurement.vendor.repositories;

import com.company.procurement.vendor.domain.Vendor;
import com.company.procurement.vendor.domain.VendorStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface VendorRepository {

  Vendor save(Vendor vendor);

  Optional<Vendor> findByIdAndOrganizationId(UUID id, UUID organizationId);

  boolean existsByOrganizationIdAndTaxId(UUID organizationId, String taxId);

  boolean existsByOrganizationIdAndTaxIdAndIdNot(UUID organizationId, String taxId, UUID id);

  Page<Vendor> search(UUID organizationId, VendorStatus status, String text, Pageable pageable);
}
