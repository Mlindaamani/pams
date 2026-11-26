package com.company.procurement.platform.repositories;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PlatformAnalyticsRepositoryAdapter implements PlatformAnalyticsRepository {

  private final JpaPlatformAnalyticsRepository jpa;
  private final JdbcTemplate jdbc;

  public PlatformAnalyticsRepositoryAdapter(JpaPlatformAnalyticsRepository jpa, JdbcTemplate jdbc) {
    this.jpa = jpa;
    this.jdbc = jdbc;
  }

  @Override
  public void useTenant(UUID organizationId) {
    jdbc.queryForObject(
        "select set_config('app.current_org', ?, false)",
        String.class,
        organizationId == null ? "" : organizationId.toString());
  }

  @Override
  public long countActiveUsers(UUID organizationId) {
    return jpa.countActiveUsers(organizationId);
  }

  @Override
  public long countRequests(UUID organizationId, Instant from, Instant to) {
    return jpa.countRequests(organizationId, from, to);
  }

  @Override
  public long countCurrentPendingApprovals(UUID organizationId) {
    return jpa.countCurrentPendingApprovals(organizationId);
  }

  @Override
  public long countMismatchedInvoices(UUID organizationId) {
    return jpa.countMismatchedInvoices(organizationId);
  }

  @Override
  public Instant lastActivity(UUID organizationId) {
    return jpa.lastActivity(organizationId);
  }

  @Override
  public List<CurrencyTotal> spendByCurrency(UUID organizationId, Instant from, Instant to) {
    return jpa.spendByCurrency(organizationId, from, to).stream()
        .map(row -> new CurrencyTotal(row.getCurrency(), row.getTotal(), row.getOrderCount()))
        .toList();
  }

  @Override
  public List<MonthStatusCount> requestStatusTrend(UUID organizationId, Instant from, Instant to) {
    return jpa.requestStatusTrend(organizationId, from, to).stream()
        .map(row -> new MonthStatusCount(row.getMonth(), row.getStatus(), row.getCount()))
        .toList();
  }

  @Override
  public List<MonthCurrencyTotal> spendTrend(UUID organizationId, Instant from, Instant to) {
    return jpa.spendTrend(organizationId, from, to).stream()
        .map(
            row ->
                new MonthCurrencyTotal(
                    row.getMonth(), row.getCurrency(), row.getTotal(), row.getOrderCount()))
        .toList();
  }

  @Override
  public long countOutboxDeadLetters() {
    return jpa.countOutboxDeadLetters();
  }
}
