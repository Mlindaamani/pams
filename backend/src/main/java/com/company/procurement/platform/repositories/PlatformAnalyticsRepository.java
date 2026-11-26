package com.company.procurement.platform.repositories;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Cross-tenant read-model analytics for the platform dashboard. */
public interface PlatformAnalyticsRepository {

  void useTenant(UUID organizationId);

  long countActiveUsers(UUID organizationId);

  long countRequests(UUID organizationId, Instant from, Instant to);

  long countCurrentPendingApprovals(UUID organizationId);

  long countMismatchedInvoices(UUID organizationId);

  Instant lastActivity(UUID organizationId);

  List<CurrencyTotal> spendByCurrency(UUID organizationId, Instant from, Instant to);

  List<MonthStatusCount> requestStatusTrend(UUID organizationId, Instant from, Instant to);

  List<MonthCurrencyTotal> spendTrend(UUID organizationId, Instant from, Instant to);

  long countOutboxDeadLetters();

  record CurrencyTotal(String currency, BigDecimal total, long orderCount) {}

  record MonthStatusCount(String month, String status, long count) {}

  record MonthCurrencyTotal(String month, String currency, BigDecimal total, long orderCount) {}
}
