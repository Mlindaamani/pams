package com.company.procurement.platform.application;

import com.company.procurement.organization.domain.Organization;
import com.company.procurement.organization.domain.OrganizationRepository;
import com.company.procurement.organization.domain.OrganizationStatus;
import com.company.procurement.reporting.config.CacheConfig;
import com.company.procurement.shared.error.BadRequestException;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Aggregates platform-wide tenant and usage analytics. */
@Service
public class PlatformAnalyticsService {

  private final OrganizationRepository organizations;
  private final JdbcTemplate jdbcTemplate;
  private final CacheManager cacheManager;
  private final Clock clock;

  public PlatformAnalyticsService(
      OrganizationRepository organizations,
      JdbcTemplate jdbcTemplate,
      CacheManager cacheManager,
      Clock clock) {
    this.organizations = organizations;
    this.jdbcTemplate = jdbcTemplate;
    this.cacheManager = cacheManager;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public PlatformDashboard dashboard(Instant fromParam, Instant toParam) {
    Window window = window(fromParam, toParam);
    String key = "dashboard|%s|%s".formatted(window.from(), window.to());
    return cached(CacheConfig.PLATFORM_DASHBOARD_CACHE, key, () -> computeDashboard(window));
  }

  private PlatformDashboard computeDashboard(Window window) {
    List<Organization> tenants =
        organizations.searchAll(null, null, Pageable.unpaged()).getContent();
    Map<String, BigDecimal> spendByCurrency = new HashMap<>();
    Map<String, Long> orderCountByCurrency = new HashMap<>();
    Map<String, Long> requestStatusCounts = new HashMap<>();
    Map<String, Map<String, Long>> requestTrend = new HashMap<>();
    Map<String, Map<String, BigDecimal>> spendTrend = new HashMap<>();
    Map<String, Long> spendTrendOrders = new HashMap<>();
    List<TenantMetric> tenantMetrics = new ArrayList<>();

    long totalRequests = 0;
    long totalUsers = 0;
    long pendingApprovals = 0;
    long mismatchInvoices = 0;
    long newTenants = 0;

    for (Organization tenant : tenants) {
      if (tenant.getCreatedAt() != null
          && !tenant.getCreatedAt().isBefore(window.from())
          && tenant.getCreatedAt().isBefore(window.to())) {
        newTenants++;
      }
      setTenant(tenant.getId());
      long users =
          count(
              "select count(*) from user_profile where organization_id = ? and deleted_at is null and active = true",
              tenant.getId());
      long requests =
          count(
              "select count(*) from purchase_request where organization_id = ? and created_at >= ? and created_at < ?",
              tenant.getId(),
              ts(window.from()),
              ts(window.to()));
      long pending =
          count(
              """
              select count(*)
                from approval_step s
                join approval_instance ai on ai.id = s.instance_id
               where s.organization_id = ?
                 and ai.current_sequence = s.sequence
                 and s.status = 'PENDING'
              """,
              tenant.getId());
      long mismatches =
          count(
              "select count(*) from vendor_invoice where organization_id = ? and status = 'MISMATCHED'",
              tenant.getId());
      Instant lastActivity =
          jdbcTemplate.queryForObject(
                      "select max(updated_at) from purchase_request where organization_id = ?",
                      java.sql.Timestamp.class,
                      tenant.getId())
                  == null
              ? null
              : jdbcTemplate
                  .queryForObject(
                      "select max(updated_at) from purchase_request where organization_id = ?",
                      java.sql.Timestamp.class,
                      tenant.getId())
                  .toInstant();

      Map<String, BigDecimal> tenantSpend = new LinkedHashMap<>();
      jdbcTemplate.query(
          """
              select po.currency, sum(pol.line_total) total, count(distinct po.id) orders
                from purchase_order po
                join purchase_order_line pol on pol.purchase_order_id = po.id
               where po.organization_id = ?
                 and po.status in ('ISSUED','PARTIALLY_RECEIVED','RECEIVED','CLOSED')
                 and po.issued_at >= ? and po.issued_at < ?
               group by po.currency
              """,
          (rs, rowNum) -> {
            String currency = rs.getString("currency");
            BigDecimal total = rs.getBigDecimal("total");
            long orders = rs.getLong("orders");
            tenantSpend.put(currency, total);
            spendByCurrency.merge(currency, total, BigDecimal::add);
            orderCountByCurrency.merge(currency, orders, Long::sum);
            return null;
          },
          tenant.getId(),
          ts(window.from()),
          ts(window.to()));

      jdbcTemplate.query(
          """
          select to_char(date_trunc('month', created_at), 'YYYY-MM') as month_label,
                 status, count(*) n
            from purchase_request
           where organization_id = ? and created_at >= ? and created_at < ?
           group by 1, 2
          """,
          (rs, rowNum) -> {
            String month = rs.getString("month_label");
            String status = rs.getString("status");
            long value = rs.getLong("n");
            requestStatusCounts.merge(status, value, Long::sum);
            requestTrend
                .computeIfAbsent(month, ignored -> new HashMap<>())
                .merge(status, value, Long::sum);
            return null;
          },
          tenant.getId(),
          ts(window.from()),
          ts(window.to()));

      jdbcTemplate.query(
          """
          select to_char(date_trunc('month', po.issued_at), 'YYYY-MM') as month_label,
                 po.currency, sum(pol.line_total) total, count(distinct po.id) orders
            from purchase_order po
            join purchase_order_line pol on pol.purchase_order_id = po.id
           where po.organization_id = ?
             and po.status in ('ISSUED','PARTIALLY_RECEIVED','RECEIVED','CLOSED')
             and po.issued_at >= ? and po.issued_at < ?
           group by 1, 2
          """,
          (rs, rowNum) -> {
            String month = rs.getString("month_label");
            String currency = rs.getString("currency");
            BigDecimal total = rs.getBigDecimal("total");
            long orders = rs.getLong("orders");
            spendTrend
                .computeIfAbsent(month + "|" + currency, ignored -> new HashMap<>())
                .merge(currency, total, BigDecimal::add);
            spendTrendOrders.merge(month + "|" + currency, orders, Long::sum);
            return null;
          },
          tenant.getId(),
          ts(window.from()),
          ts(window.to()));

      totalUsers += users;
      totalRequests += requests;
      pendingApprovals += pending;
      mismatchInvoices += mismatches;
      tenantMetrics.add(
          new TenantMetric(
              tenant.getId(),
              tenant.getName(),
              tenant.getSlug(),
              tenant.getStatus(),
              users,
              requests,
              pending,
              mismatches,
              tenantSpend,
              lastActivity));
    }

    setTenant(null);
    long activeTenants =
        tenants.stream().filter(tenant -> tenant.getStatus() == OrganizationStatus.ACTIVE).count();
    long suspendedTenants = tenants.size() - activeTenants;
    long outboxDeadLetters =
        count("select count(*) from outbox_event where dead_lettered_at is not null");

    List<CurrencySpend> currencySpend =
        spendByCurrency.entrySet().stream()
            .map(
                entry ->
                    new CurrencySpend(
                        entry.getKey(),
                        entry.getValue(),
                        orderCountByCurrency.getOrDefault(entry.getKey(), 0L)))
            .sorted(Comparator.comparing(CurrencySpend::currency))
            .toList();

    List<MonthlySpend> monthlySpend =
        spendTrend.entrySet().stream()
            .map(
                entry -> {
                  String key = entry.getKey();
                  String[] parts = key.split("\\|");
                  BigDecimal total =
                      entry.getValue().values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
                  return new MonthlySpend(
                      parts[0], parts[1], total, spendTrendOrders.getOrDefault(key, 0L));
                })
            .sorted(Comparator.comparing(MonthlySpend::month).thenComparing(MonthlySpend::currency))
            .toList();

    List<MonthlyRequestCount> monthlyRequests =
        requestTrend.entrySet().stream()
            .map(
                entry ->
                    new MonthlyRequestCount(
                        entry.getKey(),
                        entry.getValue().getOrDefault("SUBMITTED", 0L)
                            + entry.getValue().getOrDefault("IN_APPROVAL", 0L),
                        entry.getValue().getOrDefault("APPROVED", 0L),
                        entry.getValue().getOrDefault("REJECTED", 0L)))
            .sorted(Comparator.comparing(MonthlyRequestCount::month))
            .toList();

    tenantMetrics.sort(
        Comparator.comparingLong(TenantMetric::requests)
            .reversed()
            .thenComparing(TenantMetric::name));

    return new PlatformDashboard(
        window.from(),
        window.to(),
        clock.instant(),
        new Summary(
            tenants.size(),
            activeTenants,
            suspendedTenants,
            newTenants,
            totalUsers,
            totalRequests,
            pendingApprovals,
            mismatchInvoices,
            outboxDeadLetters),
        currencySpend,
        requestStatusCounts.entrySet().stream()
            .map(entry -> new StatusCount(entry.getKey(), entry.getValue()))
            .sorted(Comparator.comparing(StatusCount::status))
            .toList(),
        monthlySpend,
        monthlyRequests,
        tenantMetrics);
  }

  private void setTenant(UUID organizationId) {
    jdbcTemplate.queryForObject(
        "select set_config('app.current_org', ?, false)",
        String.class,
        organizationId == null ? "" : organizationId.toString());
  }

  private long count(String sql, Object... args) {
    Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
    return value == null ? 0 : value;
  }

  private static java.sql.Timestamp ts(Instant instant) {
    return java.sql.Timestamp.from(instant);
  }

  private Window window(Instant fromParam, Instant toParam) {
    Instant to = toParam == null ? clock.instant() : toParam;
    Instant from = fromParam == null ? to.minus(Duration.ofDays(30)) : fromParam;
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
    Cache.ValueWrapper wrapper = cache.get(key);
    if (wrapper != null) {
      return (T) wrapper.get();
    }
    try {
      T value = loader.call();
      cache.put(key, value);
      return value;
    } catch (Exception ex) {
      throw new IllegalStateException("Could not load platform analytics", ex);
    }
  }

  private record Window(Instant from, Instant to) {}

  public record PlatformDashboard(
      Instant from,
      Instant to,
      Instant generatedAt,
      Summary summary,
      List<CurrencySpend> spendByCurrency,
      List<StatusCount> requestStatus,
      List<MonthlySpend> monthlySpend,
      List<MonthlyRequestCount> monthlyRequests,
      List<TenantMetric> tenants)
      implements Serializable {}

  public record Summary(
      long totalTenants,
      long activeTenants,
      long suspendedTenants,
      long newTenants,
      long totalUsers,
      long totalRequests,
      long pendingApprovals,
      long mismatchedInvoices,
      long outboxDeadLetters)
      implements Serializable {}

  public record CurrencySpend(String currency, BigDecimal total, long orderCount)
      implements Serializable {}

  public record StatusCount(String status, long count) implements Serializable {}

  public record MonthlySpend(String month, String currency, BigDecimal total, long orderCount)
      implements Serializable {}

  public record MonthlyRequestCount(String month, long submitted, long approved, long rejected)
      implements Serializable {}

  public record TenantMetric(
      UUID id,
      String name,
      String slug,
      OrganizationStatus status,
      long users,
      long requests,
      long pendingApprovals,
      long mismatchedInvoices,
      Map<String, BigDecimal> spendByCurrency,
      Instant lastActivity)
      implements Serializable {}
}
