package com.company.procurement.receiving.repositories;

import com.company.procurement.receiving.domain.VendorInvoice;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
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

  Optional<VendorInvoice> findByIdAndOrganizationId(UUID id, UUID organizationId);

  List<VendorInvoice> findAllByIdIn(Collection<UUID> ids);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select i from VendorInvoice i where i.id = :id and i.organizationId = :organizationId")
  Optional<VendorInvoice> findForUpdate(
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

  long countByOrganizationIdAndPurchaseOrderId(UUID organizationId, UUID purchaseOrderId);

  long countByOrganizationIdAndVendorId(UUID organizationId, UUID vendorId);

  @Query(
      value =
          """
          select v.id as vendorId, v.name as vendorName, i.currency as currency,
                 sum(case when coalesce(i.due_date, i.invoice_date) >= current_date
                          then i.total_amount - i.paid_amount else 0 end) as currentAmount,
                 sum(case when current_date - coalesce(i.due_date, i.invoice_date)
                                between 1 and 30
                          then i.total_amount - i.paid_amount else 0 end) as days1To30,
                 sum(case when current_date - coalesce(i.due_date, i.invoice_date)
                                between 31 and 60
                          then i.total_amount - i.paid_amount else 0 end) as days31To60,
                 sum(case when current_date - coalesce(i.due_date, i.invoice_date)
                                between 61 and 90
                          then i.total_amount - i.paid_amount else 0 end) as days61To90,
                 sum(case when current_date - coalesce(i.due_date, i.invoice_date) > 90
                          then i.total_amount - i.paid_amount else 0 end) as daysOver90,
                 sum(i.total_amount - i.paid_amount) as totalOutstanding,
                 count(*) as invoiceCount
            from vendor_invoice i
            join vendor v on v.id = i.vendor_id
           where i.organization_id = :organizationId
             and i.status in ('APPROVED', 'PARTIALLY_PAID')
             and i.total_amount - i.paid_amount > 0
           group by v.id, v.name, i.currency
           order by v.name, i.currency
          """,
      nativeQuery = true)
  List<AgingProjection> agingReport(@Param("organizationId") UUID organizationId);

  interface AgingProjection {
    UUID getVendorId();

    String getVendorName();

    String getCurrency();

    java.math.BigDecimal getCurrentAmount();

    java.math.BigDecimal getDays1To30();

    java.math.BigDecimal getDays31To60();

    java.math.BigDecimal getDays61To90();

    java.math.BigDecimal getDaysOver90();

    java.math.BigDecimal getTotalOutstanding();

    long getInvoiceCount();
  }

  interface InvoicedQuantityProjection {
    UUID getPurchaseOrderLineId();

    BigDecimal getQuantity();
  }
}
