package com.company.procurement.subscription.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** Commercial subscription plan. */
@Entity
@Table(name = "subscription_plan")
@EntityListeners(AuditingEntityListener.class)
public class SubscriptionPlan {

  @Id
  @Column(nullable = false, length = 30)
  private String code;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(nullable = false, length = 500)
  private String description;

  @Column(name = "monthly_price", nullable = false, precision = 19, scale = 4)
  private BigDecimal monthlyPrice;

  @Column(name = "annual_price", nullable = false, precision = 19, scale = 4)
  private BigDecimal annualPrice;

  @Column(nullable = false, length = 3)
  private String currency;

  @Column(name = "trial_days", nullable = false)
  private int trialDays;

  @Column(name = "max_users")
  private Integer maxUsers;

  @Column(name = "max_departments")
  private Integer maxDepartments;

  @Column(name = "max_requests_month")
  private Integer maxRequestsMonth;

  @Column(name = "max_storage_mb")
  private Integer maxStorageMb;

  @Column(nullable = false)
  private boolean active;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Column(name = "stripe_price_monthly", length = 100)
  private String stripePriceMonthly;

  @Column(name = "stripe_price_annual", length = 100)
  private String stripePriceAnnual;

  @Column(name = "created_at", nullable = false, updatable = false)
  @CreatedDate
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  @LastModifiedDate
  private Instant updatedAt;

  protected SubscriptionPlan() {
    // for JPA
  }

  public SubscriptionPlan(
      String code,
      String name,
      String description,
      BigDecimal monthlyPrice,
      BigDecimal annualPrice,
      String currency,
      int trialDays,
      Integer maxUsers,
      Integer maxDepartments,
      Integer maxRequestsMonth,
      Integer maxStorageMb,
      boolean active,
      int sortOrder,
      String stripePriceMonthly,
      String stripePriceAnnual) {
    this.code = Objects.requireNonNull(code, "code");
    this.name = Objects.requireNonNull(name, "name");
    this.description = Objects.requireNonNull(description, "description");
    this.monthlyPrice = Objects.requireNonNull(monthlyPrice, "monthlyPrice");
    this.annualPrice = Objects.requireNonNull(annualPrice, "annualPrice");
    this.currency = Objects.requireNonNull(currency, "currency");
    this.trialDays = trialDays;
    this.maxUsers = maxUsers;
    this.maxDepartments = maxDepartments;
    this.maxRequestsMonth = maxRequestsMonth;
    this.maxStorageMb = maxStorageMb;
    this.active = active;
    this.sortOrder = sortOrder;
    this.stripePriceMonthly = stripePriceMonthly;
    this.stripePriceAnnual = stripePriceAnnual;
  }

  public void update(
      String name,
      String description,
      BigDecimal monthlyPrice,
      BigDecimal annualPrice,
      String currency,
      int trialDays,
      Integer maxUsers,
      Integer maxDepartments,
      Integer maxRequestsMonth,
      Integer maxStorageMb,
      boolean active,
      int sortOrder,
      String stripePriceMonthly,
      String stripePriceAnnual) {
    this.name = Objects.requireNonNull(name, "name");
    this.description = Objects.requireNonNull(description, "description");
    this.monthlyPrice = Objects.requireNonNull(monthlyPrice, "monthlyPrice");
    this.annualPrice = Objects.requireNonNull(annualPrice, "annualPrice");
    this.currency = Objects.requireNonNull(currency, "currency");
    this.trialDays = trialDays;
    this.maxUsers = maxUsers;
    this.maxDepartments = maxDepartments;
    this.maxRequestsMonth = maxRequestsMonth;
    this.maxStorageMb = maxStorageMb;
    this.active = active;
    this.sortOrder = sortOrder;
    this.stripePriceMonthly = stripePriceMonthly;
    this.stripePriceAnnual = stripePriceAnnual;
  }

  public void deactivate() {
    this.active = false;
  }

  public String getCode() {
    return code;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public BigDecimal getMonthlyPrice() {
    return monthlyPrice;
  }

  public BigDecimal getAnnualPrice() {
    return annualPrice;
  }

  public String getCurrency() {
    return currency;
  }

  public int getTrialDays() {
    return trialDays;
  }

  public Integer getMaxUsers() {
    return maxUsers;
  }

  public Integer getMaxDepartments() {
    return maxDepartments;
  }

  public Integer getMaxRequestsMonth() {
    return maxRequestsMonth;
  }

  public Integer getMaxStorageMb() {
    return maxStorageMb;
  }

  public boolean isActive() {
    return active;
  }

  public int getSortOrder() {
    return sortOrder;
  }

  public String getStripePriceMonthly() {
    return stripePriceMonthly;
  }

  public String getStripePriceAnnual() {
    return stripePriceAnnual;
  }
}
