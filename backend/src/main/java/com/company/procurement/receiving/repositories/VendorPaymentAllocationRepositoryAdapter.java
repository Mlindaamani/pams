package com.company.procurement.receiving.repositories;

import com.company.procurement.receiving.domain.VendorPaymentAllocation;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class VendorPaymentAllocationRepositoryAdapter implements VendorPaymentAllocationRepository {

  private final JpaVendorPaymentAllocationRepository jpa;

  public VendorPaymentAllocationRepositoryAdapter(JpaVendorPaymentAllocationRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public List<VendorPaymentAllocation> findByPaymentId(UUID paymentId) {
    return jpa.findByPaymentIdOrderByInvoiceIdAsc(paymentId);
  }

  @Override
  public List<VendorPaymentAllocation> findByPaymentIds(Collection<UUID> paymentIds) {
    return jpa.findByPaymentIdIn(paymentIds);
  }

  @Override
  public List<VendorPaymentAllocation> saveAll(List<VendorPaymentAllocation> allocations) {
    return jpa.saveAll(allocations);
  }
}
