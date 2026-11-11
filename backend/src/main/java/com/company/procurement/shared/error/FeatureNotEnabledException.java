package com.company.procurement.shared.error;

/** Raised when the tenant's subscription does not include a required feature. */
public class FeatureNotEnabledException extends DomainException {

  public FeatureNotEnabledException(String featureCode) {
    super(
        ErrorCodes.FEATURE_NOT_ENABLED,
        "The current subscription does not include the '%s' feature. Upgrade the plan to use it."
            .formatted(featureCode));
  }
}
