package com.company.procurement.receiving.repositories;

import com.company.procurement.receiving.domain.VendorPayment;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaVendorPaymentRepository extends JpaRepository<VendorPayment, UUID> {

  Optional<VendorPayment> findByIdAndOrganizationId(UUID id, UUID organizationId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from VendorPayment p where p.id = :id and p.organizationId = :organizationId")
  Optional<VendorPayment> findForUpdate(
      @Param("id") UUID id, @Param("organizationId") UUID organizationId);

  List<VendorPayment> findByOrganizationIdOrderByPaymentDateDescCreatedAtDesc(UUID organizationId);
}
