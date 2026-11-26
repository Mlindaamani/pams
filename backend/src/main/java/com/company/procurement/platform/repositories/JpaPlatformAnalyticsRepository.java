package com.company.procurement.platform.repositories;

import com.company.procurement.organization.domain.Organization;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaPlatformAnalyticsRepository extends JpaRepository<Organization, UUID> {

  @Query(
      value =
          """
          select count(*) from user_profile
           where organization_id = :organizationId and deleted_at is null and active = true
          """,
      nativeQuery = true)
  long countActiveUsers(@Param("organizationId") UUID organizationId);

  @Query(
      value =
          """
          select count(*) from purchase_request
           where organization_id = :organizationId
             and created_at >= :from and created_at < :to
          """,
      nativeQuery = true)
  long countRequests(
      @Param("organizationId") UUID organizationId,
      @Param("from") Instant from,
      @Param("to") Instant to);

  @Query(
      value =
          """
          select count(*)
            from approval_step s
            join approval_instance ai on ai.id = s.instance_id
           where s.organization_id = :organizationId
             and ai.current_sequence = s.sequence
             and s.status = 'PENDING'
          """,
      nativeQuery = true)
  long countCurrentPendingApprovals(@Param("organizationId") UUID organizationId);

  @Query(
      value =
          """
          select count(*) from vendor_invoice
           where organization_id = :organizationId and status = 'MISMATCHED'
          """,
      nativeQuery = true)
  long countMismatchedInvoices(@Param("organizationId") UUID organizationId);

  @Query(
      value =
          "select max(updated_at) from purchase_request where organization_id = :organizationId",
      nativeQuery = true)
  Instant lastActivity(@Param("organizationId") UUID organizationId);

  @Query(
      value =
          """
          select po.currency as currency, sum(pol.line_total) as total,
                 count(distinct po.id) as orderCount
            from purchase_order po
            join purchase_order_line pol on pol.purchase_order_id = po.id
           where po.organization_id = :organizationId
             and po.status in ('ISSUED','PARTIALLY_RECEIVED','RECEIVED','CLOSED')
             and po.issued_at >= :from and po.issued_at < :to
           group by po.currency
          """,
      nativeQuery = true)
  List<CurrencyTotalProjection> spendByCurrency(
      @Param("organizationId") UUID organizationId,
      @Param("from") Instant from,
      @Param("to") Instant to);

  @Query(
      value =
          """
          select to_char(date_trunc('month', created_at), 'YYYY-MM') as month,
                 status as status, count(*) as count
            from purchase_request
           where organization_id = :organizationId
             and created_at >= :from and created_at < :to
           group by 1, 2
          """,
      nativeQuery = true)
  List<MonthStatusProjection> requestStatusTrend(
      @Param("organizationId") UUID organizationId,
      @Param("from") Instant from,
      @Param("to") Instant to);

  @Query(
      value =
          """
          select to_char(date_trunc('month', po.issued_at), 'YYYY-MM') as month,
                 po.currency as currency, sum(pol.line_total) as total,
                 count(distinct po.id) as orderCount
            from purchase_order po
            join purchase_order_line pol on pol.purchase_order_id = po.id
           where po.organization_id = :organizationId
             and po.status in ('ISSUED','PARTIALLY_RECEIVED','RECEIVED','CLOSED')
             and po.issued_at >= :from and po.issued_at < :to
           group by 1, 2
          """,
      nativeQuery = true)
  List<MonthCurrencyProjection> spendTrend(
      @Param("organizationId") UUID organizationId,
      @Param("from") Instant from,
      @Param("to") Instant to);

  @Query(
      value = "select count(*) from outbox_event where dead_lettered_at is not null",
      nativeQuery = true)
  long countOutboxDeadLetters();

  interface CurrencyTotalProjection {
    String getCurrency();

    BigDecimal getTotal();

    long getOrderCount();
  }

  interface MonthStatusProjection {
    String getMonth();

    String getStatus();

    long getCount();
  }

  interface MonthCurrencyProjection {
    String getMonth();

    String getCurrency();

    BigDecimal getTotal();

    long getOrderCount();
  }
}
