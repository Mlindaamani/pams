package com.company.procurement.subscription.domain;

import com.company.procurement.shared.domain.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Billing state for one tenant. */
@Entity
@Table(name = "tenant_subscription")
public class TenantSubscription extends TenantEntity {

  @Column(name = "plan_code", nullable = false, length = 30)
  private String planCode;

  @Column(nullable = false, length = 30)
  private String status;

  @Column(name = "billing_interval", nullable = false, length = 20)
  private String billingInterval;

  @Column(name = "stripe_customer_id", length = 100)
  private String stripeCustomerId;

  @Column(name = "stripe_subscription_id", length = 100)
  private String stripeSubscriptionId;

  @Column(name = "trial_ends_at")
  private Instant trialEndsAt;

  @Column(name = "current_period_start")
  private Instant currentPeriodStart;

  @Column(name = "current_period_end")
  private Instant currentPeriodEnd;

  @Column(name = "cancel_at_period_end", nullable = false)
  private boolean cancelAtPeriodEnd;

  @Column private Integer seats;

  @Column(name = "activated_at")
  private Instant activatedAt;

  @Column(name = "billing_source", nullable = false, length = 20)
  private String billingSource;

  @Column(name = "monthly_price_snapshot", nullable = false, precision = 19, scale = 4)
  private BigDecimal monthlyPriceSnapshot;

  @Column(name = "annual_price_snapshot", nullable = false, precision = 19, scale = 4)
  private BigDecimal annualPriceSnapshot;

  @Column(name = "currency_snapshot", nullable = false, length = 3)
  private String currencySnapshot;

  @Column(name = "stripe_price_id", length = 100)
  private String stripePriceId;

  @Column(name = "last_stripe_sync_at")
  private Instant lastStripeSyncAt;

  @Column(name = "trial_reminder_7d_sent_at")
  private Instant trialReminder7dSentAt;

  @Column(name = "trial_reminder_1d_sent_at")
  private Instant trialReminder1dSentAt;

  @Column(name = "trial_expired_notified_at")
  private Instant trialExpiredNotifiedAt;

  @Column(name = "payment_failed_notified_at")
  private Instant paymentFailedNotifiedAt;

  @Column(name = "canceled_notified_at")
  private Instant canceledNotifiedAt;

  protected TenantSubscription() {
    // for JPA
  }

  public TenantSubscription(UUID organizationId, SubscriptionPlan plan, Instant now) {
    super(organizationId);
    this.planCode = plan.getCode();
    this.status = "TRIALING";
    this.billingInterval = "MONTHLY";
    this.billingSource = "TRIAL";
    this.trialEndsAt = now.plus(Duration.ofDays(plan.getTrialDays()));
    this.cancelAtPeriodEnd = false;
    this.monthlyPriceSnapshot = plan.getMonthlyPrice();
    this.annualPriceSnapshot = plan.getAnnualPrice();
    this.currencySnapshot = plan.getCurrency();
    this.stripePriceId = plan.getStripePriceMonthly();
  }

  public void applyStripeSubscription(
      String planCode,
      String status,
      String billingInterval,
      String stripeSubscriptionId,
      String stripeCustomerId,
      BigDecimal monthlyPriceSnapshot,
      BigDecimal annualPriceSnapshot,
      String currencySnapshot,
      String stripePriceId,
      Instant currentPeriodStart,
      Instant currentPeriodEnd,
      boolean cancelAtPeriodEnd,
      Instant now) {
    this.planCode = Objects.requireNonNull(planCode, "planCode");
    this.status = Objects.requireNonNull(status, "status");
    this.billingInterval = Objects.requireNonNull(billingInterval, "billingInterval");
    this.stripeSubscriptionId = firstNonBlank(stripeSubscriptionId, this.stripeSubscriptionId);
    this.stripeCustomerId = firstNonBlank(stripeCustomerId, this.stripeCustomerId);
    this.billingSource = "STRIPE";
    this.monthlyPriceSnapshot =
        Objects.requireNonNull(monthlyPriceSnapshot, "monthlyPriceSnapshot");
    this.annualPriceSnapshot = Objects.requireNonNull(annualPriceSnapshot, "annualPriceSnapshot");
    this.currencySnapshot = Objects.requireNonNull(currencySnapshot, "currencySnapshot");
    this.stripePriceId = firstNonBlank(stripePriceId, this.stripePriceId);
    this.currentPeriodStart = currentPeriodStart;
    this.currentPeriodEnd = currentPeriodEnd;
    this.cancelAtPeriodEnd = cancelAtPeriodEnd;
    if ("ACTIVE".equals(status) && this.activatedAt == null) {
      this.activatedAt = now;
    }
    this.lastStripeSyncAt = now;
  }

  public void applyStripeStatus(
      String status,
      Instant currentPeriodStart,
      Instant currentPeriodEnd,
      boolean cancelAtPeriodEnd,
      String stripePriceId,
      Instant now) {
    this.status = Objects.requireNonNull(status, "status");
    this.currentPeriodStart = currentPeriodStart;
    this.currentPeriodEnd = currentPeriodEnd;
    this.cancelAtPeriodEnd = cancelAtPeriodEnd;
    this.billingSource = "STRIPE";
    this.stripePriceId = firstNonBlank(stripePriceId, this.stripePriceId);
    this.lastStripeSyncAt = now;
  }

  public void applyTerms(
      String planCode,
      String billingInterval,
      BigDecimal monthlyPriceSnapshot,
      BigDecimal annualPriceSnapshot,
      String currencySnapshot,
      String stripePriceId) {
    this.planCode = Objects.requireNonNull(planCode, "planCode");
    this.billingInterval = Objects.requireNonNull(billingInterval, "billingInterval");
    this.monthlyPriceSnapshot =
        Objects.requireNonNull(monthlyPriceSnapshot, "monthlyPriceSnapshot");
    this.annualPriceSnapshot = Objects.requireNonNull(annualPriceSnapshot, "annualPriceSnapshot");
    this.currencySnapshot = Objects.requireNonNull(currencySnapshot, "currencySnapshot");
    this.stripePriceId = stripePriceId;
  }

  public void linkStripeCheckout(String customerId, String subscriptionId, Instant now) {
    this.stripeCustomerId = firstNonBlank(customerId, this.stripeCustomerId);
    this.stripeSubscriptionId = firstNonBlank(subscriptionId, this.stripeSubscriptionId);
    this.lastStripeSyncAt = now;
  }

  public void setStripeCustomerId(String customerId) {
    this.stripeCustomerId = customerId;
  }

  public void markStripeBilling(Instant now) {
    this.billingSource = "STRIPE";
    this.lastStripeSyncAt = now;
  }

  public void cancelByStripe(Instant now) {
    this.status = "CANCELED";
    this.lastStripeSyncAt = now;
  }

  public void markPastDue(Instant now) {
    this.status = "PAST_DUE";
    this.lastStripeSyncAt = now;
  }

  public void markPaid(Instant now) {
    if ("PAST_DUE".equals(status) || "UNPAID".equals(status)) {
      this.status = "ACTIVE";
    }
    this.paymentFailedNotifiedAt = null;
    this.lastStripeSyncAt = now;
  }

  public void expireTrial(Instant now) {
    this.status = "EXPIRED";
    this.trialExpiredNotifiedAt = now;
  }

  public void markTrialReminder7d(Instant now) {
    this.trialReminder7dSentAt = now;
  }

  public void markTrialReminder1d(Instant now) {
    this.trialReminder1dSentAt = now;
  }

  public void markPaymentFailedNotified(Instant now) {
    this.paymentFailedNotifiedAt = now;
  }

  public void markCanceledNotified(Instant now) {
    this.canceledNotifiedAt = now;
  }

  public void extendTrial(int days, Instant now) {
    Instant start = trialEndsAt == null || trialEndsAt.isBefore(now) ? now : trialEndsAt;
    this.trialEndsAt = start.plus(Duration.ofDays(days));
  }

  public void activateManual(Instant now, Instant periodEnd) {
    this.status = "ACTIVE";
    this.billingSource = "MANUAL";
    this.trialEndsAt = null;
    this.cancelAtPeriodEnd = false;
    if (this.activatedAt == null) {
      this.activatedAt = now;
    }
    this.currentPeriodStart = now;
    this.currentPeriodEnd = periodEnd;
  }

  public void cancelAtPeriodEnd() {
    this.cancelAtPeriodEnd = true;
  }

  public void cancelNow() {
    this.status = "CANCELED";
    this.cancelAtPeriodEnd = false;
  }

  public void clearCancelAtPeriodEnd() {
    this.cancelAtPeriodEnd = false;
  }

  public String getPlanCode() {
    return planCode;
  }

  public String getStatus() {
    return status;
  }

  public String getBillingInterval() {
    return billingInterval;
  }

  public String getStripeCustomerId() {
    return stripeCustomerId;
  }

  public String getStripeSubscriptionId() {
    return stripeSubscriptionId;
  }

  public Instant getTrialEndsAt() {
    return trialEndsAt;
  }

  public Instant getCurrentPeriodStart() {
    return currentPeriodStart;
  }

  public Instant getCurrentPeriodEnd() {
    return currentPeriodEnd;
  }

  public boolean isCancelAtPeriodEnd() {
    return cancelAtPeriodEnd;
  }

  public Integer getSeats() {
    return seats;
  }

  public Instant getActivatedAt() {
    return activatedAt;
  }

  public String getBillingSource() {
    return billingSource;
  }

  public BigDecimal getMonthlyPriceSnapshot() {
    return monthlyPriceSnapshot;
  }

  public BigDecimal getAnnualPriceSnapshot() {
    return annualPriceSnapshot;
  }

  public String getCurrencySnapshot() {
    return currencySnapshot;
  }

  public String getStripePriceId() {
    return stripePriceId;
  }

  public Instant getLastStripeSyncAt() {
    return lastStripeSyncAt;
  }

  public Instant getTrialReminder7dSentAt() {
    return trialReminder7dSentAt;
  }

  public Instant getTrialReminder1dSentAt() {
    return trialReminder1dSentAt;
  }

  public Instant getTrialExpiredNotifiedAt() {
    return trialExpiredNotifiedAt;
  }

  public Instant getPaymentFailedNotifiedAt() {
    return paymentFailedNotifiedAt;
  }

  public Instant getCanceledNotifiedAt() {
    return canceledNotifiedAt;
  }

  private static String firstNonBlank(String candidate, String fallback) {
    return candidate == null || candidate.isBlank() ? fallback : candidate;
  }
}
