package com.company.procurement.budget.domain;

import com.company.procurement.shared.domain.SoftDeletableEntity;
import com.company.procurement.shared.domain.TenantFilter;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "budget")
@SQLRestriction("deleted_at IS NULL")
@Filter(name = TenantFilter.NAME, condition = "organization_id = :organizationId")
/** Departmental budget with reserved and committed balances. */
public class Budget extends SoftDeletableEntity {

  @Column(name = "budget_number", nullable = false, updatable = false, length = 40)
  private String budgetNumber;

  @Column(name = "department_id", nullable = false, updatable = false)
  private UUID departmentId;

  @Column(name = "fiscal_year", nullable = false, updatable = false)
  private int fiscalYear;

  @Column(name = "category", length = 100)
  private String category;

  @Column(name = "currency", nullable = false, length = 3)
  private String currency;

  @Column(name = "allocated_amount", nullable = false, precision = 19, scale = 4)
  private BigDecimal allocatedAmount;

  @Column(name = "reserved_amount", nullable = false, precision = 19, scale = 4)
  private BigDecimal reservedAmount = BigDecimal.ZERO;

  @Column(name = "committed_amount", nullable = false, precision = 19, scale = 4)
  private BigDecimal committedAmount = BigDecimal.ZERO;

  @Column(name = "actual_amount", nullable = false, precision = 19, scale = 4)
  private BigDecimal actualAmount = BigDecimal.ZERO;

  protected Budget() {
    // for JPA
  }

  public Budget(
      UUID organizationId,
      String budgetNumber,
      UUID departmentId,
      int fiscalYear,
      String category,
      String currency,
      BigDecimal allocatedAmount) {
    super(organizationId);
    this.budgetNumber = Objects.requireNonNull(budgetNumber, "budgetNumber");
    this.departmentId = Objects.requireNonNull(departmentId, "departmentId");
    this.fiscalYear = fiscalYear;
    this.category = trimToNull(category);
    this.currency = requireCurrency(currency);
    this.allocatedAmount = requireNonNegative(allocatedAmount);
  }

  public void reallocate(BigDecimal newAllocatedAmount) {
    BigDecimal newAmount = requireNonNegative(newAllocatedAmount);
    if (newAmount.compareTo(consumed()) < 0) {
      throw new IllegalArgumentException(
          "Allocated amount cannot be lower than reserved, committed and actual amounts");
    }
    this.allocatedAmount = newAmount;
  }

  public BigDecimal remaining() {
    return allocatedAmount.subtract(consumed());
  }

  public BigDecimal consumed() {
    return reservedAmount.add(committedAmount).add(actualAmount);
  }

  public String getBudgetNumber() {
    return budgetNumber;
  }

  public UUID getDepartmentId() {
    return departmentId;
  }

  public int getFiscalYear() {
    return fiscalYear;
  }

  public String getCategory() {
    return category;
  }

  public String getCurrency() {
    return currency;
  }

  public BigDecimal getAllocatedAmount() {
    return allocatedAmount;
  }

  public BigDecimal getReservedAmount() {
    return reservedAmount;
  }

  public BigDecimal getCommittedAmount() {
    return committedAmount;
  }

  public BigDecimal getActualAmount() {
    return actualAmount;
  }

  private static BigDecimal requireNonNegative(BigDecimal value) {
    Objects.requireNonNull(value, "amount");
    if (value.signum() < 0) {
      throw new IllegalArgumentException("amount must not be negative");
    }
    return value;
  }

  private static String requireCurrency(String currency) {
    Objects.requireNonNull(currency, "currency");
    String trimmed = currency.trim();
    if (trimmed.length() != 3) {
      throw new IllegalArgumentException("currency must be a 3-letter code");
    }
    return trimmed;
  }

  private static String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
