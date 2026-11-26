package com.company.procurement.receiving.repositories;

import com.company.procurement.receiving.domain.VendorPayment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence port for vendor payments. */
public interface VendorPaymentRepository {

  Optional<VendorPayment> findByIdAndOrganizationId(UUID id, UUID organizationId);

  Optional<VendorPayment> findForUpdate(UUID id, UUID organizationId);

  List<VendorPayment> findByOrganizationId(UUID organizationId);

  VendorPayment save(VendorPayment payment);
}
