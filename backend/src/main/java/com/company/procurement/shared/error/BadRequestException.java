package com.company.procurement.shared.error;

/** Thrown for requests that are syntactically valid JSON but semantically unusable (HTTP 400). */
public class BadRequestException extends DomainException {

  public BadRequestException(String message) {
    super(ErrorCodes.INVALID_REQUEST, message);
  }
}
