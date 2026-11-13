package com.company.procurement.subscription.application;

import com.company.procurement.shared.error.BusinessRuleException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Enforces subscription status and plan entitlements. */
@Service
public class SubscriptionAccessService {

  private static final long MB = 1024L * 1024L;
  private static final long GRACE_DAYS = 7;

  private final JdbcTemplate jdbc;
  private final Clock clock;

  public SubscriptionAccessService(JdbcTemplate jdbc, Clock clock) {
    this.jdbc = jdbc;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public boolean hasFeature(UUID organizationId, String featureCode) {
    if (organizationId == null || featureCode == null || featureCode.isBlank()) return false;
    Long count =
        jdbc.queryForObject(
            """
            select count(*)
              from tenant_subscription ts
              join subscription_plan_feature pf on pf.plan_code = ts.plan_code
             where ts.organization_id = ? and pf.feature_code = ?
            """,
            Long.class,
            organizationId,
            featureCode);
    return count != null && count > 0;
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
    long users =
        count(
            "select count(*) from user_profile where organization_id = ? and deleted_at is null and active = true",
            organizationId);
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
    long departments =
        count(
            "select count(*) from department where organization_id = ? and deleted_at is null",
            organizationId);
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
    long requests =
        count(
            "select count(*) from purchase_request where organization_id = ? and date_trunc('month', created_at) = date_trunc('month', now())",
            organizationId);
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
    Long used =
        jdbc.queryForObject(
            "select coalesce(sum(size_bytes), 0) from request_attachment where organization_id = ?",
            Long.class,
            organizationId);
    long total = (used == null ? 0 : used) + additionalBytes;
    if (total > limit.maxStorageMb() * MB) {
      throw new BusinessRuleException(
          "The %s plan storage limit has been reached".formatted(limit.planCode()));
    }
  }

  private SubscriptionLimit subscription(UUID organizationId) {
    List<Map<String, Object>> rows =
        jdbc.queryForList(
            """
            select ts.plan_code, ts.status, ts.trial_ends_at, ts.current_period_end,
                   sp.max_users, sp.max_departments, sp.max_requests_month, sp.max_storage_mb
              from tenant_subscription ts
              join subscription_plan sp on sp.code = ts.plan_code
             where ts.organization_id = ?
            """,
            organizationId);
    if (rows.isEmpty()) return null;
    Map<String, Object> row = rows.get(0);
    return new SubscriptionLimit(
        (String) row.get("plan_code"),
        (String) row.get("status"),
        instant(row.get("trial_ends_at")),
        instant(row.get("current_period_end")),
        (Integer) row.get("max_users"),
        (Integer) row.get("max_departments"),
        (Integer) row.get("max_requests_month"),
        (Integer) row.get("max_storage_mb"));
  }

  private long count(String sql, Object... args) {
    Long value = jdbc.queryForObject(sql, Long.class, args);
    return value == null ? 0 : value;
  }

  private static Instant instant(Object value) {
    return value instanceof java.sql.Timestamp timestamp ? timestamp.toInstant() : null;
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
