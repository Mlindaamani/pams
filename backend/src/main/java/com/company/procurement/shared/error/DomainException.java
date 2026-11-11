package com.company.procurement.shared.error;

import java.util.Objects;

/**
 * Root of all expected business failures. Subclasses carry a stable {@link #getCode() error code},
 * which the web layer maps centrally to an HTTP status and RFC 7807 response.
 */
public abstract class DomainException extends RuntimeException {

  private final String code;

  protected DomainException(String code, String message) {
    super(message);
    this.code = Objects.requireNonNull(code, "code");
  }

  public String getCode() {
    return code;
  }
}
