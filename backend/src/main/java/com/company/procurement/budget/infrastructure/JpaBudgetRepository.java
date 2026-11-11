package com.company.procurement.budget.infrastructure;

import com.company.procurement.budget.domain.Budget;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaBudgetRepository
    extends JpaRepository<Budget, UUID>, JpaSpecificationExecutor<Budget> {

  Optional<Budget> findByIdAndOrganizationId(UUID id, UUID organizationId);

  Optional<Budget> findFirstByOrganizationIdAndDepartmentIdAndFiscalYearAndCategoryIsNull(
      UUID organizationId, UUID departmentId, int fiscalYear);

  boolean existsByOrganizationIdAndDepartmentIdAndFiscalYearAndCategoryIsNull(
      UUID organizationId, UUID departmentId, int fiscalYear);

  boolean existsByOrganizationIdAndDepartmentIdAndFiscalYearAndCategory(
      UUID organizationId, UUID departmentId, int fiscalYear, String category);

  @Modifying(flushAutomatically = true)
  @Query(
      """
      update Budget b
         set b.reservedAmount = b.reservedAmount + :amount,
             b.version = b.version + 1
       where b.id = :id
         and b.currency = :currency
         and b.reservedAmount + b.committedAmount + b.actualAmount + :amount <= b.allocatedAmount
      """)
  int reserve(
      @Param("id") UUID id, @Param("amount") BigDecimal amount, @Param("currency") String currency);

  @Modifying(flushAutomatically = true)
  @Query(
      """
      update Budget b
         set b.reservedAmount = b.reservedAmount - :amount,
             b.version = b.version + 1
       where b.id = :id
         and b.reservedAmount >= :amount
      """)
  int release(@Param("id") UUID id, @Param("amount") BigDecimal amount);

  @Modifying(flushAutomatically = true)
  @Query(
      """
      update Budget b
         set b.reservedAmount = b.reservedAmount - :amount,
             b.committedAmount = b.committedAmount + :amount,
             b.version = b.version + 1
       where b.id = :id
         and b.reservedAmount >= :amount
      """)
  int convertToCommitted(@Param("id") UUID id, @Param("amount") BigDecimal amount);

  @Modifying(flushAutomatically = true)
  @Query(
      """
      update Budget b
         set b.committedAmount = b.committedAmount - :amount,
             b.version = b.version + 1
       where b.id = :id
         and b.committedAmount >= :amount
      """)
  int releaseCommitted(@Param("id") UUID id, @Param("amount") BigDecimal amount);
}
