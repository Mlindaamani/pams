package com.company.procurement.subscription.services;

import com.company.procurement.shared.error.BusinessRuleException;
import com.company.procurement.shared.usage.TenantUsagePort;
import com.company.procurement.subscription.repositories.TenantSubscriptionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Enforces subscription status and plan entitlements. */
@Service
public class SubscriptionAccessService {

  private static final long MB = 1024L * 1024L;
  private static final long GRACE_DAYS = 7;

  private final TenantSubscriptionRepository subscriptions;
  private final TenantUsagePort usage;
  private final Clock clock;

  public SubscriptionAccessService(
      TenantSubscriptionRepository subscriptions, TenantUsagePort usage, Clock clock) {
    this.subscriptions = subscriptions;
    this.usage = usage;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public boolean hasFeature(UUID organizationId, String featureCode) {
    if (organizationId == null || featureCode == null || featureCode.isBlank()) return false;
    return subscriptions.hasFeature(organizationId, featureCode);
  }

  @Transactional(readOnly = true)
  public void assertWriteAllowed(UUID organizationId) {
    SubscriptionLimit limit = subscription(organizationId);
    if (limit == null) return;
    String status = limit.status();
    if ("TRIALING".equals(status)) {
      if (limit.trialEndsAt() != null && limit.trialEndsAt().isBefore(clock.instant())) {
        throw new BusinessRuleException("The subscription trial has expired");
      }
      return;
    }
    if ("ACTIVE".equals(status)) return;
    if ("PAST_DUE".equals(status)) {
      Instant graceEnd =
          limit.currentPeriodEnd() == null
              ? clock.instant()
              : limit.currentPeriodEnd().plusSeconds(GRACE_DAYS * 86400);
      if (graceEnd.isAfter(clock.instant())) return;
      throw new BusinessRuleException("The subscription payment is overdue");
    }
    throw new BusinessRuleException("The tenant subscription is not active");
  }

  @Transactional(readOnly = true)
  public void assertCanAddUser(UUID organizationId) {
    assertWriteAllowed(organizationId);
    SubscriptionLimit limit = subscription(organizationId);
    if (limit == null || limit.maxUsers() == null) return;
    long users = usage.usage(organizationId).users();
    if (users >= limit.maxUsers()) {
      throw new BusinessRuleException(
          "The %s plan allows %d users".formatted(limit.planCode(), limit.maxUsers()));
    }
  }

  @Transactional(readOnly = true)
  public void assertCanAddDepartment(UUID organizationId) {
    assertWriteAllowed(organizationId);
    SubscriptionLimit limit = subscription(organizationId);
    if (limit == null || limit.maxDepartments() == null) return;
    long departments = usage.usage(organizationId).departments();
    if (departments >= limit.maxDepartments()) {
      throw new BusinessRuleException(
          "The %s plan allows %d departments".formatted(limit.planCode(), limit.maxDepartments()));
    }
  }

  @Transactional(readOnly = true)
  public void assertCanSubmitRequest(UUID organizationId) {
    assertWriteAllowed(organizationId);
    SubscriptionLimit limit = subscription(organizationId);
    if (limit == null || limit.maxRequestsMonth() == null) return;
    long requests = usage.usage(organizationId).requestsThisMonth();
    if (requests >= limit.maxRequestsMonth()) {
      throw new BusinessRuleException(
          "The %s plan allows %d requests per month"
              .formatted(limit.planCode(), limit.maxRequestsMonth()));
    }
  }

  @Transactional(readOnly = true)
  public void assertCanStore(UUID organizationId, long additionalBytes) {
    assertWriteAllowed(organizationId);
    SubscriptionLimit limit = subscription(organizationId);
    if (limit == null || limit.maxStorageMb() == null) return;
    long total = usage.usage(organizationId).storageBytes() + additionalBytes;
    if (total > limit.maxStorageMb() * MB) {
      throw new BusinessRuleException(
          "The %s plan storage limit has been reached".formatted(limit.planCode()));
    }
  }

  private SubscriptionLimit subscription(UUID organizationId) {
    return subscriptions
        .entitlements(organizationId)
        .map(
            row ->
                new SubscriptionLimit(
                    row.planCode(),
                    row.status(),
                    row.trialEndsAt(),
                    row.currentPeriodEnd(),
                    row.maxUsers(),
                    row.maxDepartments(),
                    row.maxRequestsMonth(),
                    row.maxStorageMb()))
        .orElse(null);
  }

  private record SubscriptionLimit(
      String planCode,
      String status,
      Instant trialEndsAt,
      Instant currentPeriodEnd,
      Integer maxUsers,
      Integer maxDepartments,
      Integer maxRequestsMonth,
      Integer maxStorageMb) {}
}
