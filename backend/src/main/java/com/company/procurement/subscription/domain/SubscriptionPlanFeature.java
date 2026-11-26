package com.company.procurement.subscription.domain;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Feature assigned to a subscription plan. */
@Entity
@Table(name = "subscription_plan_feature")
public class SubscriptionPlanFeature {

  @EmbeddedId private SubscriptionPlanFeatureId id;

  protected SubscriptionPlanFeature() {
    // for JPA
  }

  public SubscriptionPlanFeature(String planCode, String featureCode) {
    this.id = new SubscriptionPlanFeatureId(planCode, featureCode);
  }

  public SubscriptionPlanFeatureId getId() {
    return id;
  }
}
