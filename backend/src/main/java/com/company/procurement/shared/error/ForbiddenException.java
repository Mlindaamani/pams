package com.company.procurement.shared.error;

/** Thrown when an authenticated principal is not allowed to use a tenant-scoped feature. */
public class ForbiddenException extends DomainException {

  public ForbiddenException(String message) {
    super(ErrorCodes.FORBIDDEN, message);
  }
}
