package com.company.procurement.reporting.services;

import com.company.procurement.reporting.config.CacheConfig;
import com.company.procurement.reporting.repositories.ReportingRepository;
import com.company.procurement.reporting.services.dto.CommitmentRow;
import com.company.procurement.reporting.services.dto.CommitmentsReport;
import com.company.procurement.reporting.services.dto.CycleTimeReport;
import com.company.procurement.reporting.services.dto.CycleTimeRow;
import com.company.procurement.reporting.services.dto.SpendReport;
import com.company.procurement.reporting.services.dto.SpendRow;
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
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Builds spend, commitment and cycle-time reports. */
@Service
public class ReportService {

  private final ReportingRepository reports;
  private final CacheManager cacheManager;
  private final Clock clock;

  public ReportService(ReportingRepository reports, CacheManager cacheManager, Clock clock) {
    this.reports = reports;
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
          List<SpendRow> rows =
              spendRows(organizationId, groupBy, window.from(), window.to()).stream()
                  .map(
                      row ->
                          new SpendRow(
                              row.key(),
                              row.label(),
                              row.currency(),
                              row.total(),
                              row.orderCount()))
                  .toList();
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
          List<ReportingRepository.CycleRow> rows =
              groupBy == CycleGroupBy.POLICY
                  ? reports.cycleByPolicy(organizationId, window.from(), window.to())
                  : reports.cycleByApprover(organizationId, window.from(), window.to());
          return new CycleTimeReport(
              groupBy.name().toLowerCase(Locale.ROOT),
              window.from(),
              window.to(),
              clock.instant(),
              rows.stream()
                  .map(
                      row ->
                          new CycleTimeRow(
                              row.key(),
                              row.label(),
                              row.count(),
                              row.avgMinutes(),
                              row.p50Minutes(),
                              row.p90Minutes()))
                  .toList());
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
          List<CommitmentRow> rows =
              reports.commitments(organizationId, fiscalYear, departmentId).stream()
                  .map(
                      row -> {
                        String currency = row.currency();
                        BigDecimal allocated = row.allocatedAmount();
                        BigDecimal reserved = row.reservedAmount();
                        BigDecimal committed = row.committedAmount();
                        BigDecimal actual = row.actualAmount();
                        return new CommitmentRow(
                            row.id(),
                            row.departmentId(),
                            row.departmentName(),
                            row.fiscalYear(),
                            currency,
                            MoneyDto.of(allocated, currency),
                            MoneyDto.of(reserved, currency),
                            MoneyDto.of(committed, currency),
                            MoneyDto.of(actual, currency),
                            MoneyDto.of(
                                allocated.subtract(reserved).subtract(committed).subtract(actual),
                                currency));
                      })
                  .toList();
          return new CommitmentsReport(fiscalYear, departmentId, clock.instant(), rows);
        });
  }

  public void streamSpendCsv(
      String groupByRaw, Instant fromParam, Instant toParam, OutputStream output) {
    UUID organizationId = TenantContext.require();
    SpendGroupBy groupBy = SpendGroupBy.parse(groupByRaw);
    Window window = window(fromParam, toParam);
    List<ReportingRepository.SpendRow> rows =
        spendRows(organizationId, groupBy, window.from(), window.to());
    try {
      Writer writer = new OutputStreamWriter(output, StandardCharsets.UTF_8);
      writer.write("key,label,currency,total,order_count\n");
      for (ReportingRepository.SpendRow row : rows) {
        writer.write(
            "%s,%s,%s,%s,%s%n"
                .formatted(
                    csv(row.key()),
                    csv(row.label()),
                    csv(row.currency()),
                    row.total().toPlainString(),
                    row.orderCount()));
      }
      writer.flush();
    } catch (IOException ex) {
      throw new IllegalStateException("Could not stream CSV", ex);
    }
  }

  private List<ReportingRepository.SpendRow> spendRows(
      UUID organizationId, SpendGroupBy groupBy, Instant from, Instant to) {
    return switch (groupBy) {
      case DEPARTMENT -> reports.spendByDepartment(organizationId, from, to);
      case VENDOR -> reports.spendByVendor(organizationId, from, to);
      case CATEGORY -> reports.spendByCategory(organizationId, from, to);
      case MONTH -> reports.spendByMonth(organizationId, from, to);
    };
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
    DEPARTMENT,
    VENDOR,
    CATEGORY,
    MONTH;

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
}
