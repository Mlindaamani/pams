package com.company.procurement.reporting.repositories;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class ReportingRepositoryAdapter implements ReportingRepository {

  private final JpaReportingRepository jpa;

  public ReportingRepositoryAdapter(JpaReportingRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public List<SpendRow> spendByDepartment(UUID organizationId, Instant from, Instant to) {
    return jpa.spendByDepartment(organizationId, from, to).stream()
        .map(ReportingRepositoryAdapter::spend)
        .toList();
  }

  @Override
  public List<SpendRow> spendByVendor(UUID organizationId, Instant from, Instant to) {
    return jpa.spendByVendor(organizationId, from, to).stream()
        .map(ReportingRepositoryAdapter::spend)
        .toList();
  }

  @Override
  public List<SpendRow> spendByCategory(UUID organizationId, Instant from, Instant to) {
    return jpa.spendByCategory(organizationId, from, to).stream()
        .map(ReportingRepositoryAdapter::spend)
        .toList();
  }

  @Override
  public List<SpendRow> spendByMonth(UUID organizationId, Instant from, Instant to) {
    return jpa.spendByMonth(organizationId, from, to).stream()
        .map(ReportingRepositoryAdapter::spend)
        .toList();
  }

  @Override
  public List<CycleRow> cycleByPolicy(UUID organizationId, Instant from, Instant to) {
    return jpa.cycleByPolicy(organizationId, from, to).stream()
        .map(ReportingRepositoryAdapter::cycle)
        .toList();
  }

  @Override
  public List<CycleRow> cycleByApprover(UUID organizationId, Instant from, Instant to) {
    return jpa.cycleByApprover(organizationId, from, to).stream()
        .map(ReportingRepositoryAdapter::cycle)
        .toList();
  }

  @Override
  public List<CommitmentRow> commitments(
      UUID organizationId, Integer fiscalYear, UUID departmentId) {
    return jpa.commitments(organizationId, fiscalYear, departmentId).stream()
        .map(
            row ->
                new CommitmentRow(
                    row.getId(),
                    row.getDepartmentId(),
                    row.getDepartmentName(),
                    row.getFiscalYear(),
                    row.getCurrency(),
                    row.getAllocatedAmount(),
                    row.getReservedAmount(),
                    row.getCommittedAmount(),
                    row.getActualAmount()))
        .toList();
  }

  private static SpendRow spend(JpaReportingRepository.SpendProjection row) {
    return new SpendRow(
        row.getKey(), row.getLabel(), row.getCurrency(), row.getTotal(), row.getOrderCount());
  }

  private static CycleRow cycle(JpaReportingRepository.CycleProjection row) {
    return new CycleRow(
        row.getKey(),
        row.getLabel(),
        row.getCount(),
        row.getAvgMinutes(),
        row.getP50Minutes(),
        row.getP90Minutes());
  }
}
