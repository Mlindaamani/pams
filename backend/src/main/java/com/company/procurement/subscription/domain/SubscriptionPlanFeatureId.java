package com.company.procurement.subscription.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class SubscriptionPlanFeatureId implements Serializable {

  @Column(name = "plan_code", nullable = false, length = 30)
  private String planCode;

  @Column(name = "feature_code", nullable = false, length = 50)
  private String featureCode;

  protected SubscriptionPlanFeatureId() {
    // for JPA
  }

  public SubscriptionPlanFeatureId(String planCode, String featureCode) {
    this.planCode = Objects.requireNonNull(planCode, "planCode");
    this.featureCode = Objects.requireNonNull(featureCode, "featureCode");
  }

  public String getPlanCode() {
    return planCode;
  }

  public String getFeatureCode() {
    return featureCode;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof SubscriptionPlanFeatureId that)) return false;
    return planCode.equals(that.planCode) && featureCode.equals(that.featureCode);
  }

  @Override
  public int hashCode() {
    return Objects.hash(planCode, featureCode);
  }
}
