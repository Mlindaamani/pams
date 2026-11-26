package com.company.procurement.vendor.repositories;

import java.util.UUID;

/** Read-only document references used by vendor lifecycle rules. */
public interface VendorReferencePort {

  long referenceCount(UUID organizationId, UUID vendorId);
}
