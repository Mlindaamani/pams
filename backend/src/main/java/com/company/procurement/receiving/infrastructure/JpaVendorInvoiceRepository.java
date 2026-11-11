package com.company.procurement.receiving.infrastructure;

import com.company.procurement.receiving.domain.VendorInvoice;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaVendorInvoiceRepository
    extends JpaRepository<VendorInvoice, UUID>, JpaSpecificationExecutor<VendorInvoice> {

  @Query(
      """
      select count(i) > 0 from VendorInvoice i
       where i.organizationId = :organizationId
         and i.vendorId = :vendorId
         and lower(i.invoiceNumber) = lower(:invoiceNumber)
         and i.status not in (
           com.company.procurement.receiving.domain.VendorInvoiceStatus.VOID,
           com.company.procurement.receiving.domain.VendorInvoiceStatus.REJECTED)
      """)
  boolean existsActiveByVendorAndNumber(
      @Param("organizationId") UUID organizationId,
      @Param("vendorId") UUID vendorId,
      @Param("invoiceNumber") String invoiceNumber);

  @EntityGraph(attributePaths = "lines")
  @Query("select i from VendorInvoice i where i.id = :id and i.organizationId = :organizationId")
  Optional<VendorInvoice> findDetail(
      @Param("id") UUID id, @Param("organizationId") UUID organizationId);

  @Query(
      """
      select l.purchaseOrderLineId as purchaseOrderLineId, sum(l.quantity) as quantity
      from VendorInvoiceLine l
      where l.organizationId = :organizationId and l.purchaseOrderId = :purchaseOrderId
        and l.invoice.status in (
          com.company.procurement.receiving.domain.VendorInvoiceStatus.MATCHED,
          com.company.procurement.receiving.domain.VendorInvoiceStatus.APPROVED,
          com.company.procurement.receiving.domain.VendorInvoiceStatus.PARTIALLY_PAID,
          com.company.procurement.receiving.domain.VendorInvoiceStatus.PAID)
      group by l.purchaseOrderLineId
      """)
  List<InvoicedQuantityProjection> sumInvoicedQuantities(
      @Param("organizationId") UUID organizationId, @Param("purchaseOrderId") UUID purchaseOrderId);

  interface InvoicedQuantityProjection {
    UUID getPurchaseOrderLineId();

    BigDecimal getQuantity();
  }
}
