package com.company.procurement.budget.infrastructure;

import com.company.procurement.budget.domain.Budget;
import com.company.procurement.budget.domain.BudgetRepository;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

@Repository
public class BudgetRepositoryAdapter implements BudgetRepository {

  private final JpaBudgetRepository jpa;

  public BudgetRepositoryAdapter(JpaBudgetRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public Budget save(Budget budget) {
    return jpa.save(budget);
  }

  @Override
  public Optional<Budget> findByIdAndOrganizationId(UUID id, UUID organizationId) {
    return jpa.findByIdAndOrganizationId(id, organizationId);
  }

  @Override
  public Optional<Budget> findDepartmentBudget(
      UUID organizationId, UUID departmentId, int fiscalYear) {
    return jpa.findFirstByOrganizationIdAndDepartmentIdAndFiscalYearAndCategoryIsNull(
        organizationId, departmentId, fiscalYear);
  }

  @Override
  public boolean existsByOrganizationIdAndDepartmentIdAndFiscalYearAndCategory(
      UUID organizationId, UUID departmentId, int fiscalYear, String category) {
    if (category == null || category.isBlank()) {
      return jpa.existsByOrganizationIdAndDepartmentIdAndFiscalYearAndCategoryIsNull(
          organizationId, departmentId, fiscalYear);
    }
    return jpa.existsByOrganizationIdAndDepartmentIdAndFiscalYearAndCategory(
        organizationId, departmentId, fiscalYear, category.trim());
  }

  @Override
  public Page<Budget> search(
      UUID organizationId, UUID departmentId, Integer fiscalYear, Pageable pageable) {
    return jpa.findAll(specification(organizationId, departmentId, fiscalYear), pageable);
  }

  @Override
  public boolean reserve(UUID id, BigDecimal amount, String currency) {
    return jpa.reserve(id, amount, currency) == 1;
  }

  @Override
  public boolean release(UUID id, BigDecimal amount) {
    return jpa.release(id, amount) == 1;
  }

  @Override
  public boolean convertToCommitted(UUID id, BigDecimal amount) {
    return jpa.convertToCommitted(id, amount) == 1;
  }

  @Override
  public boolean releaseCommitted(UUID id, BigDecimal amount) {
    return jpa.releaseCommitted(id, amount) == 1;
  }

  private static Specification<Budget> specification(
      UUID organizationId, UUID departmentId, Integer fiscalYear) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      predicates.add(cb.equal(root.get("organizationId"), organizationId));
      if (departmentId != null) {
        predicates.add(cb.equal(root.get("departmentId"), departmentId));
      }
      if (fiscalYear != null) {
        predicates.add(cb.equal(root.get("fiscalYear"), fiscalYear));
      }
      return cb.and(predicates.toArray(Predicate[]::new));
    };
  }
}
