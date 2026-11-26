package com.company.procurement.reporting.repositories;

import com.company.procurement.organization.domain.Organization;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaReportingRepository extends JpaRepository<Organization, UUID> {

  @Query(
      value =
          """
          select pr.department_id::text as key, d.name as label, po.currency as currency,
                 sum(pol.line_total) as total, count(distinct po.id) as orderCount
            from purchase_order po
            join purchase_order_line pol on pol.purchase_order_id = po.id
            join purchase_request pr on pr.id = po.purchase_request_id
            join department d on d.id = pr.department_id
           where po.organization_id = :organizationId
             and po.status in ('ISSUED','PARTIALLY_RECEIVED','RECEIVED','CLOSED')
             and po.issued_at >= :from and po.issued_at < :to
           group by pr.department_id, d.name, po.currency
           order by total desc
          """,
      nativeQuery = true)
  List<SpendProjection> spendByDepartment(
      @Param("organizationId") UUID organizationId,
      @Param("from") Instant from,
      @Param("to") Instant to);

  @Query(
      value =
          """
          select po.vendor_id::text as key, v.name as label, po.currency as currency,
                 sum(pol.line_total) as total, count(distinct po.id) as orderCount
            from purchase_order po
            join purchase_order_line pol on pol.purchase_order_id = po.id
            join vendor v on v.id = po.vendor_id
           where po.organization_id = :organizationId
             and po.status in ('ISSUED','PARTIALLY_RECEIVED','RECEIVED','CLOSED')
             and po.issued_at >= :from and po.issued_at < :to
           group by po.vendor_id, v.name, po.currency
           order by total desc
          """,
      nativeQuery = true)
  List<SpendProjection> spendByVendor(
      @Param("organizationId") UUID organizationId,
      @Param("from") Instant from,
      @Param("to") Instant to);

  @Query(
      value =
          """
          select coalesce(ci.category, 'UNCATEGORIZED') as key,
                 coalesce(ci.category, 'UNCATEGORIZED') as label,
                 po.currency as currency, sum(pol.line_total) as total,
                 count(distinct po.id) as orderCount
            from purchase_order po
            join purchase_order_line pol on pol.purchase_order_id = po.id
            join purchase_request pr on pr.id = po.purchase_request_id
            join purchase_request_line prl
              on prl.purchase_request_id = po.purchase_request_id
             and prl.position = pol.sequence - 1
            left join catalog_item ci on ci.id = prl.catalog_item_id
           where po.organization_id = :organizationId
             and po.status in ('ISSUED','PARTIALLY_RECEIVED','RECEIVED','CLOSED')
             and po.issued_at >= :from and po.issued_at < :to
           group by coalesce(ci.category, 'UNCATEGORIZED'), po.currency
           order by total desc
          """,
      nativeQuery = true)
  List<SpendProjection> spendByCategory(
      @Param("organizationId") UUID organizationId,
      @Param("from") Instant from,
      @Param("to") Instant to);

  @Query(
      value =
          """
          select to_char(date_trunc('month', po.issued_at), 'YYYY-MM') as key,
                 to_char(date_trunc('month', po.issued_at), 'YYYY-MM') as label,
                 po.currency as currency, sum(pol.line_total) as total,
                 count(distinct po.id) as orderCount
            from purchase_order po
            join purchase_order_line pol on pol.purchase_order_id = po.id
           where po.organization_id = :organizationId
             and po.status in ('ISSUED','PARTIALLY_RECEIVED','RECEIVED','CLOSED')
             and po.issued_at >= :from and po.issued_at < :to
           group by date_trunc('month', po.issued_at), po.currency
           order by total desc
          """,
      nativeQuery = true)
  List<SpendProjection> spendByMonth(
      @Param("organizationId") UUID organizationId,
      @Param("from") Instant from,
      @Param("to") Instant to);

  @Query(
      value =
          """
          select p.id::text as key,
                 p.name || ' v' || p.policy_version as label,
                 count(*) as count,
                 avg(extract(epoch from (ai.completed_at - ai.created_at)) / 60.0) as avgMinutes,
                 percentile_cont(0.5) within group (
                     order by extract(epoch from (ai.completed_at - ai.created_at)) / 60.0) as p50Minutes,
                 percentile_cont(0.9) within group (
                     order by extract(epoch from (ai.completed_at - ai.created_at)) / 60.0) as p90Minutes
            from approval_instance ai
            join approval_policy p on p.id = ai.policy_id
           where ai.organization_id = :organizationId
             and ai.status = 'APPROVED'
             and ai.completed_at >= :from
             and ai.completed_at < :to
           group by p.id, p.name, p.policy_version
           order by count desc
          """,
      nativeQuery = true)
  List<CycleProjection> cycleByPolicy(
      @Param("organizationId") UUID organizationId,
      @Param("from") Instant from,
      @Param("to") Instant to);

  @Query(
      value =
          """
          with step_times as (
            select s.acted_by_subject,
                   s.acted_by_name,
                   extract(epoch from (
                       s.acted_at - coalesce(
                           lag(s.acted_at) over (partition by s.instance_id order by s.sequence),
                           ai.created_at)) / 60.0) as minutes
              from approval_step s
              join approval_instance ai on ai.id = s.instance_id
             where s.organization_id = :organizationId
               and s.status = 'APPROVED'
               and ai.status = 'APPROVED'
               and ai.completed_at >= :from
               and ai.completed_at < :to
          )
          select acted_by_subject as key,
                 max(acted_by_name) as label,
                 count(*) as count,
                 avg(minutes) as avgMinutes,
                 percentile_cont(0.5) within group (order by minutes) as p50Minutes,
                 percentile_cont(0.9) within group (order by minutes) as p90Minutes
            from step_times
           group by acted_by_subject
           order by count desc
          """,
      nativeQuery = true)
  List<CycleProjection> cycleByApprover(
      @Param("organizationId") UUID organizationId,
      @Param("from") Instant from,
      @Param("to") Instant to);

  @Query(
      value =
          """
          select b.id as id, b.department_id as departmentId, d.name as departmentName,
                 b.fiscal_year as fiscalYear, b.currency as currency,
                 b.allocated_amount as allocatedAmount, b.reserved_amount as reservedAmount,
                 b.committed_amount as committedAmount, b.actual_amount as actualAmount
            from budget b
            join department d on d.id = b.department_id
           where b.organization_id = :organizationId
             and b.deleted_at is null
             and (:fiscalYear is null or b.fiscal_year = :fiscalYear)
             and (:departmentId is null or b.department_id = :departmentId)
           order by d.name asc, b.fiscal_year desc
          """,
      nativeQuery = true)
  List<CommitmentProjection> commitments(
      @Param("organizationId") UUID organizationId,
      @Param("fiscalYear") Integer fiscalYear,
      @Param("departmentId") UUID departmentId);

  interface SpendProjection {
    String getKey();

    String getLabel();

    String getCurrency();

    java.math.BigDecimal getTotal();

    long getOrderCount();
  }

  interface CycleProjection {
    String getKey();

    String getLabel();

    long getCount();

    double getAvgMinutes();

    double getP50Minutes();

    double getP90Minutes();
  }

  interface CommitmentProjection {
    UUID getId();

    UUID getDepartmentId();

    String getDepartmentName();

    int getFiscalYear();

    String getCurrency();

    java.math.BigDecimal getAllocatedAmount();

    java.math.BigDecimal getReservedAmount();

    java.math.BigDecimal getCommittedAmount();

    java.math.BigDecimal getActualAmount();
  }
}
