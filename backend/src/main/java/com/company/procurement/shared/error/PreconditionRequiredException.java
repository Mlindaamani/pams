package com.company.procurement.shared.error;

/** Thrown when a mutation requires an If-Match header that was not supplied (HTTP 428). */
public class PreconditionRequiredException extends DomainException {

  public PreconditionRequiredException(String message) {
    super(ErrorCodes.PRECONDITION_REQUIRED, message);
  }
}
