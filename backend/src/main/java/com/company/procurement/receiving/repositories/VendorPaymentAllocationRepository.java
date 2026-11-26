package com.company.procurement.receiving.repositories;

import com.company.procurement.receiving.domain.VendorPaymentAllocation;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Persistence port for vendor-payment allocations. */
public interface VendorPaymentAllocationRepository {

  List<VendorPaymentAllocation> findByPaymentId(UUID paymentId);

  List<VendorPaymentAllocation> findByPaymentIds(Collection<UUID> paymentIds);

  List<VendorPaymentAllocation> saveAll(List<VendorPaymentAllocation> allocations);
}
