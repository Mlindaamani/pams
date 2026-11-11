package com.company.procurement.shared.error;

/** Stable, machine-readable error codes exposed in RFC 7807 {@code code} fields. */
public final class ErrorCodes {

  public static final String VALIDATION_FAILED = "validation-failed";
  public static final String MALFORMED_REQUEST = "malformed-request";
  public static final String INVALID_REQUEST = "invalid-request";
  public static final String UNAUTHORIZED = "unauthorized";
  public static final String FORBIDDEN = "forbidden";
  public static final String FEATURE_NOT_ENABLED = "feature-not-enabled";
  public static final String PRECONDITION_REQUIRED = "precondition-required";
  public static final String PRECONDITION_FAILED = "precondition-failed";
  public static final String BUDGET_EXCEEDED = "budget-exceeded";
  public static final String FILE_STORAGE_ERROR = "file-storage-error";
  public static final String IDEMPOTENCY_KEY_REUSE = "idempotency-key-reuse";
  public static final String RATE_LIMITED = "rate-limited";
  public static final String TENANT_SUSPENDED = "tenant-suspended";
  public static final String RESOURCE_NOT_FOUND = "resource-not-found";
  public static final String CONFLICT = "conflict";
  public static final String ILLEGAL_STATE_TRANSITION = "illegal-state-transition";
  public static final String BUSINESS_RULE_VIOLATION = "business-rule-violation";
  public static final String INTERNAL_ERROR = "internal-error";

  private ErrorCodes() {}
}
