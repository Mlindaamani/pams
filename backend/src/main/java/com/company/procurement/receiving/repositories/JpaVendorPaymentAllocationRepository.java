package com.company.procurement.receiving.repositories;

import com.company.procurement.receiving.domain.VendorPaymentAllocation;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaVendorPaymentAllocationRepository
    extends JpaRepository<VendorPaymentAllocation, UUID> {

  List<VendorPaymentAllocation> findByPaymentIdOrderByInvoiceIdAsc(UUID paymentId);

  List<VendorPaymentAllocation> findByPaymentIdIn(Collection<UUID> paymentIds);
}
