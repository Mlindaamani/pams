package com.company.procurement.budget.domain;

import com.company.procurement.shared.error.BusinessRuleException;
import com.company.procurement.shared.error.ErrorCodes;

public class BudgetExceededException extends BusinessRuleException {

  public BudgetExceededException(String message) {
    super(ErrorCodes.BUDGET_EXCEEDED, message);
  }
}
