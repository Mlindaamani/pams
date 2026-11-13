package com.company.procurement.reporting.application;

import com.company.procurement.reporting.application.dto.CommitmentRow;
import com.company.procurement.reporting.application.dto.CommitmentsReport;
import com.company.procurement.reporting.application.dto.CycleTimeReport;
import com.company.procurement.reporting.application.dto.CycleTimeRow;
import com.company.procurement.reporting.application.dto.SpendReport;
import com.company.procurement.reporting.application.dto.SpendRow;
import com.company.procurement.reporting.config.CacheConfig;
import com.company.procurement.shared.dto.MoneyDto;
import com.company.procurement.shared.error.BadRequestException;
import com.company.procurement.shared.tenant.TenantContext;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Builds spend, commitment and cycle-time reports. */
@Service
public class ReportService {

  private static final String SPEND_STATUSES =
      "('ISSUED','PARTIALLY_RECEIVED','RECEIVED','CLOSED')";

  private final JdbcTemplate jdbcTemplate;
  private final CacheManager cacheManager;
  private final Clock clock;

  public ReportService(JdbcTemplate jdbcTemplate, CacheManager cacheManager, Clock clock) {
    this.jdbcTemplate = jdbcTemplate;
    this.cacheManager = cacheManager;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public SpendReport spend(String groupByRaw, Instant fromParam, Instant toParam) {
    UUID organizationId = TenantContext.require();
    SpendGroupBy groupBy = SpendGroupBy.parse(groupByRaw);
    Window window = window(fromParam, toParam);
    String key = "%s|spend|%s|%s|%s".formatted(organizationId, groupBy, fromParam, toParam);
    return cached(
        CacheConfig.SPEND_CACHE,
        key,
        () -> {
          QuerySpec query = spendQuery(organizationId, groupBy, window);
          List<SpendRow> rows =
              jdbcTemplate.query(
                  query.sql(),
                  (rs, rowNum) ->
                      new SpendRow(
                          rs.getString("key"),
                          rs.getString("label"),
                          rs.getString("currency"),
                          rs.getBigDecimal("total"),
                          rs.getLong("order_count")),
                  query.arguments().toArray());
          return new SpendReport(
              groupBy.name().toLowerCase(Locale.ROOT),
              window.from(),
              window.to(),
              clock.instant(),
              rows);
        });
  }

  @Transactional(readOnly = true)
  public CycleTimeReport cycleTime(String groupByRaw, Instant fromParam, Instant toParam) {
    UUID organizationId = TenantContext.require();
    CycleGroupBy groupBy = CycleGroupBy.parse(groupByRaw);
    Window window = window(fromParam, toParam);
    String key = "%s|cycle|%s|%s|%s".formatted(organizationId, groupBy, fromParam, toParam);
    return cached(
        CacheConfig.CYCLE_TIME_CACHE,
        key,
        () -> {
          String sql = groupBy == CycleGroupBy.POLICY ? POLICY_CYCLE_SQL : APPROVER_CYCLE_SQL;
          List<CycleTimeRow> rows =
              jdbcTemplate.query(
                  sql,
                  (rs, rowNum) ->
                      new CycleTimeRow(
                          rs.getString("key"),
                          rs.getString("label"),
                          rs.getLong("n"),
                          rs.getDouble("avg_minutes"),
                          rs.getDouble("p50_minutes"),
                          rs.getDouble("p90_minutes")),
                  organizationId,
                  java.sql.Timestamp.from(window.from()),
                  java.sql.Timestamp.from(window.to()));
          return new CycleTimeReport(
              groupBy.name().toLowerCase(Locale.ROOT),
              window.from(),
              window.to(),
              clock.instant(),
              rows);
        });
  }

  @Transactional(readOnly = true)
  public CommitmentsReport commitments(Integer fiscalYear, UUID departmentId) {
    UUID organizationId = TenantContext.require();
    String cacheKey =
        "%s|commitments|%s|%s"
            .formatted(organizationId, fiscalYear, departmentId == null ? "" : departmentId);
    return cached(
        CacheConfig.COMMITMENTS_CACHE,
        cacheKey,
        () -> {
          StringBuilder sql =
              new StringBuilder(
                  """
                  select b.id, b.department_id, d.name as department_name, b.fiscal_year, b.currency,
                         b.allocated_amount, b.reserved_amount, b.committed_amount, b.actual_amount
                    from budget b
                    join department d on d.id = b.department_id
                   where b.organization_id = ?
                     and b.deleted_at is null
                  """);
          List<Object> arguments = new ArrayList<>();
          arguments.add(organizationId);
          if (fiscalYear != null) {
            sql.append(" and b.fiscal_year = ?");
            arguments.add(fiscalYear);
          }
          if (departmentId != null) {
            sql.append(" and b.department_id = ?");
            arguments.add(departmentId);
          }
          sql.append(" order by d.name asc, b.fiscal_year desc");
          List<CommitmentRow> rows =
              jdbcTemplate.query(
                  sql.toString(),
                  (rs, rowNum) -> {
                    BigDecimal allocated = rs.getBigDecimal("allocated_amount");
                    BigDecimal reserved = rs.getBigDecimal("reserved_amount");
                    BigDecimal committed = rs.getBigDecimal("committed_amount");
                    BigDecimal actual = rs.getBigDecimal("actual_amount");
                    String currency = rs.getString("currency");
                    return new CommitmentRow(
                        rs.getObject("id", UUID.class),
                        rs.getObject("department_id", UUID.class),
                        rs.getString("department_name"),
                        rs.getInt("fiscal_year"),
                        currency,
                        MoneyDto.of(allocated, currency),
                        MoneyDto.of(reserved, currency),
                        MoneyDto.of(committed, currency),
                        MoneyDto.of(actual, currency),
                        MoneyDto.of(
                            allocated.subtract(reserved).subtract(committed).subtract(actual),
                            currency));
                  },
                  arguments.toArray());
          return new CommitmentsReport(fiscalYear, departmentId, clock.instant(), rows);
        });
  }

  public void streamSpendCsv(
      String groupByRaw, Instant fromParam, Instant toParam, OutputStream output) {
    UUID organizationId = TenantContext.require();
    SpendGroupBy groupBy = SpendGroupBy.parse(groupByRaw);
    Window window = window(fromParam, toParam);
    QuerySpec query = spendQuery(organizationId, groupBy, window);
    try {
      Writer writer = new OutputStreamWriter(output, StandardCharsets.UTF_8);
      writer.write("key,label,currency,total,order_count\n");
      jdbcTemplate.query(
          query.sql(),
          rs -> {
            try {
              writer.write(
                  "%s,%s,%s,%s,%s%n"
                      .formatted(
                          csv(rs.getString("key")),
                          csv(rs.getString("label")),
                          csv(rs.getString("currency")),
                          rs.getBigDecimal("total").toPlainString(),
                          rs.getLong("order_count")));
            } catch (IOException ex) {
              throw new IllegalStateException("Could not stream CSV", ex);
            }
          },
          query.arguments().toArray());
      writer.flush();
    } catch (IOException ex) {
      throw new IllegalStateException("Could not stream CSV", ex);
    }
  }

  private static final String POLICY_CYCLE_SQL =
      """
      select p.id::text as key,
             p.name || ' v' || p.policy_version as label,
             count(*) as n,
             avg(extract(epoch from (ai.completed_at - ai.created_at)) / 60.0) as avg_minutes,
             percentile_cont(0.5) within group (
                 order by extract(epoch from (ai.completed_at - ai.created_at)) / 60.0) as p50_minutes,
             percentile_cont(0.9) within group (
                 order by extract(epoch from (ai.completed_at - ai.created_at)) / 60.0) as p90_minutes
        from approval_instance ai
        join approval_policy p on p.id = ai.policy_id
       where ai.organization_id = ?
         and ai.status = 'APPROVED'
         and ai.completed_at >= ?
         and ai.completed_at < ?
       group by p.id, p.name, p.policy_version
       order by n desc
      """;

  private static final String APPROVER_CYCLE_SQL =
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
         where s.organization_id = ?
           and s.status = 'APPROVED'
           and ai.status = 'APPROVED'
           and ai.completed_at >= ?
           and ai.completed_at < ?
      )
      select acted_by_subject as key,
             max(acted_by_name) as label,
             count(*) as n,
             avg(minutes) as avg_minutes,
             percentile_cont(0.5) within group (order by minutes) as p50_minutes,
             percentile_cont(0.9) within group (order by minutes) as p90_minutes
        from step_times
       group by acted_by_subject
       order by n desc
      """;

  private QuerySpec spendQuery(UUID organizationId, SpendGroupBy groupBy, Window window) {
    String keyExpression = groupBy.keyExpression();
    String labelExpression = groupBy.labelExpression();
    String joins = groupBy.joins();
    String sql =
        """
        select %s as key, %s as label, po.currency,
               sum(pol.line_total) as total,
               count(distinct po.id) as order_count
          from purchase_order po
          join purchase_order_line pol on pol.purchase_order_id = po.id
          %s
         where po.organization_id = ?
           and po.status in %s
           and po.issued_at >= ?
           and po.issued_at < ?
         group by %s, %s, po.currency
         order by total desc
        """
            .formatted(
                keyExpression,
                labelExpression,
                joins,
                SPEND_STATUSES,
                keyExpression,
                labelExpression);
    return new QuerySpec(
        sql,
        List.of(
            organizationId,
            java.sql.Timestamp.from(window.from()),
            java.sql.Timestamp.from(window.to())));
  }

  private Window window(Instant fromParam, Instant toParam) {
    Instant to = toParam == null ? clock.instant() : toParam;
    Instant from = fromParam == null ? to.minus(Duration.ofDays(365)) : fromParam;
    if (!from.isBefore(to)) {
      throw new BadRequestException("from must be before to");
    }
    return new Window(from, to);
  }

  private <T> T cached(String cacheName, String key, Callable<T> loader) {
    Cache cache = cacheManager.getCache(cacheName);
    if (cache == null) {
      throw new IllegalStateException("Cache is not configured: " + cacheName);
    }
    try {
      return cache.get(key, loader);
    } catch (RuntimeException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new IllegalStateException("Could not load report", ex);
    }
  }

  private static String csv(String value) {
    if (value == null) {
      return "";
    }
    if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
      return "\"" + value.replace("\"", "\"\"") + "\"";
    }
    return value;
  }

  public enum SpendGroupBy {
    DEPARTMENT(
        "pr.department_id::text",
        "d.name",
        "join purchase_request pr on pr.id = po.purchase_request_id "
            + "join department d on d.id = pr.department_id"),
    VENDOR("po.vendor_id::text", "v.name", "join vendor v on v.id = po.vendor_id"),
    CATEGORY(
        "coalesce(ci.category, 'UNCATEGORIZED')",
        "coalesce(ci.category, 'UNCATEGORIZED')",
        "join purchase_request pr on pr.id = po.purchase_request_id "
            + "join purchase_request_line prl on prl.purchase_request_id = po.purchase_request_id "
            + "and prl.position = pol.sequence - 1 "
            + "left join catalog_item ci on ci.id = prl.catalog_item_id"),
    MONTH(
        "to_char(date_trunc('month', po.issued_at), 'YYYY-MM')",
        "to_char(date_trunc('month', po.issued_at), 'YYYY-MM')",
        "");

    private final String keyExpression;
    private final String labelExpression;
    private final String joins;

    SpendGroupBy(String keyExpression, String labelExpression, String joins) {
      this.keyExpression = keyExpression;
      this.labelExpression = labelExpression;
      this.joins = joins;
    }

    public String keyExpression() {
      return keyExpression;
    }

    public String labelExpression() {
      return labelExpression;
    }

    public String joins() {
      return joins;
    }

    public static SpendGroupBy parse(String raw) {
      try {
        return valueOf((raw == null ? "department" : raw).trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException ex) {
        throw new BadRequestException("groupBy must be one of department, vendor, category, month");
      }
    }
  }

  public enum CycleGroupBy {
    POLICY,
    APPROVER;

    public static CycleGroupBy parse(String raw) {
      try {
        return valueOf((raw == null ? "policy" : raw).trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException ex) {
        throw new BadRequestException("groupBy must be policy or approver");
      }
    }
  }

  private record Window(Instant from, Instant to) {}

  private record QuerySpec(String sql, List<Object> arguments) {}
}
