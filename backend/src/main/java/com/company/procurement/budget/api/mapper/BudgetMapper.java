package com.company.procurement.budget.api.mapper;

import com.company.procurement.budget.api.dto.BudgetResponse;
import com.company.procurement.budget.domain.Budget;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface BudgetMapper {

  @Mapping(
      target = "allocated",
      expression = "java(MoneyDto.of(budget.getAllocatedAmount(), budget.getCurrency()))")
  @Mapping(
      target = "reserved",
      expression = "java(MoneyDto.of(budget.getReservedAmount(), budget.getCurrency()))")
  @Mapping(
      target = "committed",
      expression = "java(MoneyDto.of(budget.getCommittedAmount(), budget.getCurrency()))")
  @Mapping(
      target = "actual",
      expression = "java(MoneyDto.of(budget.getActualAmount(), budget.getCurrency()))")
  @Mapping(
      target = "remaining",
      expression = "java(MoneyDto.of(budget.remaining(), budget.getCurrency()))")
  BudgetResponse toResponse(Budget budget);
}
