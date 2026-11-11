package com.company.procurement.shared.error;

/** Thrown when a request cannot be applied because the current state forbids it (HTTP 409). */
public class ConflictException extends DomainException {

  public ConflictException(String message) {
    super(ErrorCodes.CONFLICT, message);
  }

  protected ConflictException(String code, String message) {
    super(code, message);
  }
}
