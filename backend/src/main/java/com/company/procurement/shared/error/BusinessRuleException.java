package com.company.procurement.shared.error;

/** Thrown when a valid request violates a business rule (HTTP 422). */
public class BusinessRuleException extends DomainException {

  public BusinessRuleException(String message) {
    super(ErrorCodes.BUSINESS_RULE_VIOLATION, message);
  }

  protected BusinessRuleException(String code, String message) {
    super(code, message);
  }
}
