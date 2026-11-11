package com.company.procurement.budget.application;

import com.company.procurement.budget.domain.Budget;
import com.company.procurement.budget.domain.BudgetExceededException;
import com.company.procurement.budget.domain.BudgetRepository;
import com.company.procurement.organization.application.DepartmentService;
import com.company.procurement.shared.actor.CurrentActorProvider;
import com.company.procurement.shared.domain.ReportingDataChangedEvent;
import com.company.procurement.shared.error.BusinessRuleException;
import com.company.procurement.shared.error.ConflictException;
import com.company.procurement.shared.error.ResourceNotFoundException;
import com.company.procurement.shared.numbering.DocumentNumberGenerator;
import com.company.procurement.shared.numbering.DocumentType;
import com.company.procurement.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BudgetService {

  private final BudgetRepository budgets;
  private final DepartmentService departments;
  private final Clock clock;
  private final CurrentActorProvider actorProvider;
  private final ApplicationEventPublisher events;
  private final DocumentNumberGenerator numbers;

  public BudgetService(
      BudgetRepository budgets,
      DepartmentService departments,
      Clock clock,
      CurrentActorProvider actorProvider,
      ApplicationEventPublisher events,
      DocumentNumberGenerator numbers) {
    this.budgets = budgets;
    this.departments = departments;
    this.clock = clock;
    this.actorProvider = actorProvider;
    this.events = events;
    this.numbers = numbers;
  }

  @Transactional
  public Budget create(CreateBudgetCommand command) {
    UUID organizationId = TenantContext.require();
    departments.get(command.departmentId());
    String category = normalizeCategory(command.category());
    if (budgets.existsByOrganizationIdAndDepartmentIdAndFiscalYearAndCategory(
        organizationId, command.departmentId(), command.fiscalYear(), category)) {
      throw new ConflictException("A budget already exists for this department and fiscal year");
    }
    Budget saved =
        budgets.save(
            new Budget(
                organizationId,
                numbers.next(organizationId, DocumentType.BUDGET, command.fiscalYear()),
                command.departmentId(),
                command.fiscalYear(),
                category,
                command.currency(),
                command.allocatedAmount()));
    events.publishEvent(new ReportingDataChangedEvent(organizationId));
    return saved;
  }

  @Transactional
  public Budget update(UUID id, UpdateBudgetCommand command) {
    Budget budget = get(id);
    budget.reallocate(command.allocatedAmount());
    Budget saved = budgets.save(budget);
    events.publishEvent(new ReportingDataChangedEvent(saved.getOrganizationId()));
    return saved;
  }

  @Transactional
  public void delete(UUID id) {
    Budget budget = get(id);
    if (budget.consumed().signum() > 0) {
      throw new BusinessRuleException(
          "A budget with reservations, commitments or spend cannot be deleted");
    }
    budget.softDelete(clock.instant(), actorProvider.currentActor());
    budgets.save(budget);
    events.publishEvent(new ReportingDataChangedEvent(budget.getOrganizationId()));
  }

  @Transactional(readOnly = true)
  public Budget get(UUID id) {
    UUID organizationId = TenantContext.require();
    return budgets
        .findByIdAndOrganizationId(id, organizationId)
        .orElseThrow(() -> new ResourceNotFoundException("Budget %s not found".formatted(id)));
  }

  @Transactional(readOnly = true)
  public Page<Budget> search(UUID departmentId, Integer fiscalYear, Pageable pageable) {
    UUID organizationId = TenantContext.require();
    return budgets.search(organizationId, departmentId, fiscalYear, pageable);
  }

  /**
   * Atomically reserves the amount against the department-level budget for the fiscal year. Throws
   * {@link BudgetExceededException} when the remaining amount is insufficient.
   */
  @Transactional(noRollbackFor = BudgetExceededException.class)
  public UUID reserveForDepartment(
      UUID organizationId, UUID departmentId, int fiscalYear, BigDecimal amount, String currency) {
    Budget budget =
        budgets
            .findDepartmentBudget(organizationId, departmentId, fiscalYear)
            .orElseThrow(
                () ->
                    new BudgetExceededException(
                        "No budget is defined for department %s in %d"
                            .formatted(departmentId, fiscalYear)));
    if (!budgets.reserve(budget.getId(), amount, currency)) {
      throw new BudgetExceededException(
          "Budget exceeded for department %s in %d".formatted(departmentId, fiscalYear));
    }
    events.publishEvent(new ReportingDataChangedEvent(organizationId));
    return budget.getId();
  }

  @Transactional
  public void release(UUID organizationId, UUID budgetId, BigDecimal amount) {
    if (!budgets.release(budgetId, amount)) {
      throw new BusinessRuleException("Budget reservation could not be released");
    }
    events.publishEvent(new ReportingDataChangedEvent(organizationId));
  }

  @Transactional
  public void convertToCommitted(UUID organizationId, UUID budgetId, BigDecimal amount) {
    if (!budgets.convertToCommitted(budgetId, amount)) {
      throw new BusinessRuleException("Budget reservation could not be converted to a commitment");
    }
    events.publishEvent(new ReportingDataChangedEvent(organizationId));
  }

  @Transactional
  public void releaseCommitted(UUID organizationId, UUID budgetId, BigDecimal amount) {
    if (!budgets.releaseCommitted(budgetId, amount)) {
      throw new BusinessRuleException("Budget commitment could not be released");
    }
    events.publishEvent(new ReportingDataChangedEvent(organizationId));
  }

  private static String normalizeCategory(String category) {
    if (category == null) {
      return null;
    }
    String trimmed = category.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
