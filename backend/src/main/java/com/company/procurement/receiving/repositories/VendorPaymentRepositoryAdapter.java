package com.company.procurement.receiving.repositories;

import com.company.procurement.receiving.domain.VendorPayment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class VendorPaymentRepositoryAdapter implements VendorPaymentRepository {

  private final JpaVendorPaymentRepository jpa;

  public VendorPaymentRepositoryAdapter(JpaVendorPaymentRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public Optional<VendorPayment> findByIdAndOrganizationId(UUID id, UUID organizationId) {
    return jpa.findByIdAndOrganizationId(id, organizationId);
  }

  @Override
  public Optional<VendorPayment> findForUpdate(UUID id, UUID organizationId) {
    return jpa.findForUpdate(id, organizationId);
  }

  @Override
  public List<VendorPayment> findByOrganizationId(UUID organizationId) {
    return jpa.findByOrganizationIdOrderByPaymentDateDescCreatedAtDesc(organizationId);
  }

  @Override
  public VendorPayment save(VendorPayment payment) {
    return jpa.save(payment);
  }
}
