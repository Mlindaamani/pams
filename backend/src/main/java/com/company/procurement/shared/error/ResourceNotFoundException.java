package com.company.procurement.shared.error;

/** Thrown when a requested resource does not exist or is not visible to the caller. */
public class ResourceNotFoundException extends DomainException {

  public ResourceNotFoundException(String message) {
    super(ErrorCodes.RESOURCE_NOT_FOUND, message);
  }
}
