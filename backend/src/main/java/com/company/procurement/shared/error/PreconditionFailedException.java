package com.company.procurement.shared.error;

/** Thrown when If-Match does not match the current resource version (HTTP 412). */
public class PreconditionFailedException extends DomainException {

  public PreconditionFailedException(String message) {
    super(ErrorCodes.PRECONDITION_FAILED, message);
  }
}
