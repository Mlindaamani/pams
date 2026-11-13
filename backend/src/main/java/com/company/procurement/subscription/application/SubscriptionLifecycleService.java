package com.company.procurement.subscription.application;

import com.company.procurement.shared.outbox.OutboxEventTypes;
import com.company.procurement.shared.outbox.OutboxService;
import com.company.procurement.shared.tenant.TenantContext;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Runs trial, dunning and cancellation lifecycle notices. */
@Service
public class SubscriptionLifecycleService {

  private final JdbcTemplate jdbc;
  private final OutboxService outbox;
  private final Clock clock;

  public SubscriptionLifecycleService(JdbcTemplate jdbc, OutboxService outbox, Clock clock) {
    this.jdbc = jdbc;
    this.outbox = outbox;
    this.clock = clock;
  }

  public void runLifecycleNotifications() {
    Instant now = clock.instant();
    processTrials(now);
    processPastDue();
    processCanceled();
  }

  private void processTrials(Instant now) {
    List<Map<String, Object>> trials =
        jdbc.queryForList(
            """
            select organization_id, id, plan_code, trial_ends_at,
                   trial_reminder_7d_sent_at, trial_reminder_1d_sent_at
              from tenant_subscription
             where status = 'TRIALING'
               and billing_source = 'TRIAL'
               and trial_ends_at is not null
            """);
    for (Map<String, Object> trial : trials) {
      UUID organizationId = (UUID) trial.get("organization_id");
      Instant trialEndsAt = instant(trial.get("trial_ends_at"));
      if (trialEndsAt == null) continue;
      if (!trialEndsAt.isAfter(now)) {
        jdbc.update(
            "update tenant_subscription set status = 'EXPIRED', trial_expired_notified_at = now(), updated_at = now() where id = ?",
            trial.get("id"));
        notify(
            organizationId,
            (UUID) trial.get("id"),
            OutboxEventTypes.SUBSCRIPTION_TRIAL_EXPIRED,
            "Expired",
            trialEndsAt);
        continue;
      }
      Duration remaining = Duration.between(now, trialEndsAt);
      if (remaining.toHours() <= 24 && trial.get("trial_reminder_1d_sent_at") == null) {
        jdbc.update(
            "update tenant_subscription set trial_reminder_1d_sent_at = now(), updated_at = now() where id = ?",
            trial.get("id"));
        notify(
            organizationId,
            (UUID) trial.get("id"),
            OutboxEventTypes.SUBSCRIPTION_TRIAL_ENDING,
            "Trial ending",
            trialEndsAt);
      } else if (remaining.toHours() <= 7 * 24
          && remaining.toHours() > 24
          && trial.get("trial_reminder_7d_sent_at") == null) {
        jdbc.update(
            "update tenant_subscription set trial_reminder_7d_sent_at = now(), updated_at = now() where id = ?",
            trial.get("id"));
        notify(
            organizationId,
            (UUID) trial.get("id"),
            OutboxEventTypes.SUBSCRIPTION_TRIAL_ENDING,
            "Trial ending",
            trialEndsAt);
      }
    }
  }

  private void processPastDue() {
    List<Map<String, Object>> rows =
        jdbc.queryForList(
            """
            select organization_id, id, current_period_end
              from tenant_subscription
             where status in ('PAST_DUE', 'UNPAID')
               and payment_failed_notified_at is null
            """);
    for (Map<String, Object> row : rows) {
      UUID organizationId = (UUID) row.get("organization_id");
      UUID subscriptionId = (UUID) row.get("id");
      jdbc.update(
          "update tenant_subscription set payment_failed_notified_at = now(), updated_at = now() where id = ?",
          subscriptionId);
      notify(
          organizationId,
          subscriptionId,
          OutboxEventTypes.SUBSCRIPTION_PAYMENT_FAILED,
          "Payment failed",
          instant(row.get("current_period_end")));
    }
  }

  private void processCanceled() {
    List<Map<String, Object>> rows =
        jdbc.queryForList(
            """
            select organization_id, id, current_period_end
              from tenant_subscription
             where status = 'CANCELED'
               and canceled_notified_at is null
            """);
    for (Map<String, Object> row : rows) {
      UUID organizationId = (UUID) row.get("organization_id");
      UUID subscriptionId = (UUID) row.get("id");
      jdbc.update(
          "update tenant_subscription set canceled_notified_at = now(), updated_at = now() where id = ?",
          subscriptionId);
      notify(
          organizationId,
          subscriptionId,
          OutboxEventTypes.SUBSCRIPTION_CANCELED,
          "Canceled",
          instant(row.get("current_period_end")));
    }
  }

  private void notify(
      UUID organizationId,
      UUID subscriptionId,
      String eventType,
      String statusLabel,
      Instant date) {
    Map<String, Object> details =
        jdbc.queryForMap(
            """
            select o.name as tenant_name, p.name as plan_name,
                   coalesce(tp.admin_email,
                            (select email from user_profile up
                              where up.organization_id = ts.organization_id
                                and up.deleted_at is null and up.active = true
                              order by up.created_at limit 1)) as email,
                   coalesce(tp.admin_name,
                            (select display_name from user_profile up
                              where up.organization_id = ts.organization_id
                                and up.deleted_at is null and up.active = true
                              order by up.created_at limit 1),
                            'Administrator') as recipient_name
              from tenant_subscription ts
              join organization o on o.id = ts.organization_id
              join subscription_plan p on p.code = ts.plan_code
              left join tenant_provisioning tp on tp.organization_id = ts.organization_id
             where ts.id = ?
            """,
            subscriptionId);
    String email = (String) details.get("email");
    if (email == null || email.isBlank()) return;
    TenantContext.set(organizationId);
    try {
      outbox.enqueue(
          eventType,
          "TenantSubscription",
          subscriptionId,
          Map.of(
              "email",
              email,
              "recipientName",
              details.get("recipient_name"),
              "tenantName",
              details.get("tenant_name"),
              "planName",
              details.get("plan_name"),
              "statusLabel",
              statusLabel,
              "dateLabel",
              date == null ? "—" : date.toString()));
    } finally {
      TenantContext.clear();
    }
  }

  private static Instant instant(Object value) {
    return value instanceof java.sql.Timestamp timestamp ? timestamp.toInstant() : null;
  }
}
