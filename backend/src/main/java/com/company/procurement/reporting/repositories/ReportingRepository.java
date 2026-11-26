package com.company.procurement.reporting.repositories;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Read-model queries for procurement reporting. */
public interface ReportingRepository {

  List<SpendRow> spendByDepartment(UUID organizationId, Instant from, Instant to);

  List<SpendRow> spendByVendor(UUID organizationId, Instant from, Instant to);

  List<SpendRow> spendByCategory(UUID organizationId, Instant from, Instant to);

  List<SpendRow> spendByMonth(UUID organizationId, Instant from, Instant to);

  List<CycleRow> cycleByPolicy(UUID organizationId, Instant from, Instant to);

  List<CycleRow> cycleByApprover(UUID organizationId, Instant from, Instant to);

  List<CommitmentRow> commitments(UUID organizationId, Integer fiscalYear, UUID departmentId);

  record SpendRow(String key, String label, String currency, BigDecimal total, long orderCount) {}

  record CycleRow(
      String key,
      String label,
      long count,
      double avgMinutes,
      double p50Minutes,
      double p90Minutes) {}

  record CommitmentRow(
      UUID id,
      UUID departmentId,
      String departmentName,
      int fiscalYear,
      String currency,
      BigDecimal allocatedAmount,
      BigDecimal reservedAmount,
      BigDecimal committedAmount,
      BigDecimal actualAmount) {}
}
