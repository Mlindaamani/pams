package com.company.procurement.platform.services;

import com.company.procurement.organization.domain.Organization;
import com.company.procurement.organization.domain.OrganizationStatus;
import com.company.procurement.organization.repositories.OrganizationRepository;
import com.company.procurement.platform.repositories.PlatformAnalyticsRepository;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Aggregates platform-wide tenant and usage analytics. */
@Service
public class PlatformAnalyticsService {

  private final OrganizationRepository organizations;
  private final PlatformAnalyticsRepository analytics;
  private final CacheManager cacheManager;
  private final Clock clock;

  public PlatformAnalyticsService(
      OrganizationRepository organizations,
      PlatformAnalyticsRepository analytics,
      CacheManager cacheManager,
      Clock clock) {
    this.organizations = organizations;
    this.analytics = analytics;
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
    Map<String, BigDecimal> spendTrendTotals = new HashMap<>();
    Map<String, Long> spendTrendOrders = new HashMap<>();
    List<TenantMetric> tenantMetrics = new ArrayList<>();

    long totalRequests = 0;
    long totalUsers = 0;
    long pendingApprovals = 0;
    long mismatchInvoices = 0;
    long newTenants = 0;

    try {
      for (Organization tenant : tenants) {
        if (tenant.getCreatedAt() != null
            && !tenant.getCreatedAt().isBefore(window.from())
            && tenant.getCreatedAt().isBefore(window.to())) {
          newTenants++;
        }
        analytics.useTenant(tenant.getId());
        long users = analytics.countActiveUsers(tenant.getId());
        long requests = analytics.countRequests(tenant.getId(), window.from(), window.to());
        long pending = analytics.countCurrentPendingApprovals(tenant.getId());
        long mismatches = analytics.countMismatchedInvoices(tenant.getId());
        Instant lastActivity = analytics.lastActivity(tenant.getId());

        Map<String, BigDecimal> tenantSpend = new LinkedHashMap<>();
        for (PlatformAnalyticsRepository.CurrencyTotal total :
            analytics.spendByCurrency(tenant.getId(), window.from(), window.to())) {
          tenantSpend.put(total.currency(), total.total());
          spendByCurrency.merge(total.currency(), total.total(), BigDecimal::add);
          orderCountByCurrency.merge(total.currency(), total.orderCount(), Long::sum);
        }

        for (PlatformAnalyticsRepository.MonthStatusCount row :
            analytics.requestStatusTrend(tenant.getId(), window.from(), window.to())) {
          requestStatusCounts.merge(row.status(), row.count(), Long::sum);
          requestTrend
              .computeIfAbsent(row.month(), ignored -> new HashMap<>())
              .merge(row.status(), row.count(), Long::sum);
        }

        for (PlatformAnalyticsRepository.MonthCurrencyTotal row :
            analytics.spendTrend(tenant.getId(), window.from(), window.to())) {
          String key = row.month() + "|" + row.currency();
          spendTrendTotals.merge(key, row.total(), BigDecimal::add);
          spendTrendOrders.merge(key, row.orderCount(), Long::sum);
        }

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
    } finally {
      analytics.useTenant(null);
    }
    long activeTenants =
        tenants.stream().filter(tenant -> tenant.getStatus() == OrganizationStatus.ACTIVE).count();
    long suspendedTenants = tenants.size() - activeTenants;
    long outboxDeadLetters = analytics.countOutboxDeadLetters();

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
        spendTrendTotals.entrySet().stream()
            .map(
                entry -> {
                  String[] parts = entry.getKey().split("\\|");
                  return new MonthlySpend(
                      parts[0],
                      parts[1],
                      entry.getValue(),
                      spendTrendOrders.getOrDefault(entry.getKey(), 0L));
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
