package com.company.procurement.budget.domain;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface BudgetRepository {

  Budget save(Budget budget);

  Optional<Budget> findByIdAndOrganizationId(UUID id, UUID organizationId);

  Optional<Budget> findDepartmentBudget(UUID organizationId, UUID departmentId, int fiscalYear);

  boolean existsByOrganizationIdAndDepartmentIdAndFiscalYearAndCategory(
      UUID organizationId, UUID departmentId, int fiscalYear, String category);

  Page<Budget> search(
      UUID organizationId, UUID departmentId, Integer fiscalYear, Pageable pageable);

  boolean reserve(UUID id, BigDecimal amount, String currency);

  boolean release(UUID id, BigDecimal amount);

  boolean convertToCommitted(UUID id, BigDecimal amount);

  boolean releaseCommitted(UUID id, BigDecimal amount);
}
