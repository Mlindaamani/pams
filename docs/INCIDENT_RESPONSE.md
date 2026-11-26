# Incident Response and Alerting

This runbook is the repository-owned part of production operations. Contact details, status-page
accounts, on-call routing and external monitoring credentials must be filled in by the operator.

## Roles

| Role | Responsibility | Current contact |
|---|---|---|
| Incident commander | Declares severity, owns timeline and decisions | TODO |
| On-call engineer | Triage, mitigation, technical comms | TODO |
| Platform/billing owner | Tenant, Keycloak, Stripe and data decisions | TODO |
| Security/privacy lead | Breach assessment, legal notification | TODO |

## Severity and response targets

| Severity | Example | Acknowledge | Update cadence |
|---|---|---|---|
| SEV1 | Data leak, cross-tenant exposure, full outage, payment double-charge | 15 min | Every 30 min |
| SEV2 | Core workflow unavailable, partial outage, rising 5xx | 30 min | Every 60 min |
| SEV3 | Degraded reports/notifications, single tenant issue | 4 h | Daily |
| SEV4 | Cosmetic or low-risk issue | Next business day | As needed |

## Required alerts

Configure these in the external monitoring/alerting provider. Do not provision Prometheus, Grafana
or Tempo in this repository.

| Signal | Warning | Critical | First action |
|---|---|---|---|
| API readiness | 2 failures in 2 min | 5 min unavailable | Check `/actuator/health/readiness`, API logs, DB/Redis/Keycloak |
| HTTP 5xx rate | > 2% for 5 min | > 10% for 5 min | Inspect `traceId`, roll back image if release-related |
| API latency p95 | > 1s for 10 min | > 2s for 10 min | Check DB pool, slow queries, Redis, load test |
| Outbox pending | > 100 for 10 min | > 500 for 10 min | Inspect `/admin/system`, poller and handler errors |
| Outbox dead letters | > 0 | Recurring growth | Fix handler, then replay from admin/system |
| DB connections | > 80% pool | > 95% pool | Check leaks/slow queries; scale or restart safely |
| Disk usage | > 80% | > 90% | Prune backups/logs, expand volume |
| TLS certificate | < 21 days | < 7 days | Renew at edge/load balancer |
| Keycloak readiness | 2 failures | 5 min unavailable | Check Keycloak DB and issuer URL |
| Stripe webhook failures | > 1% for 15 min | Payment state stale | Check signature secret, Stripe events, replay |
| Backup freshness | > 26 h | > 48 h | Run backup, inspect backup logs and storage |

## Incident flow

1. Declare severity in the incident channel and assign an incident commander.
2. Protect data first: disable compromised accounts, rotate exposed secrets, and isolate affected
   components.
3. Check `/admin/system`, API readiness/liveness, Keycloak, PostgreSQL, Redis, MinIO and recent
   deployments.
4. Prefer rollback for a bad release. Use `ops/production/rollback.sh`; migrations are forward-only,
   so restore the verified database backup if a bad migration was applied.
5. Publish status-page updates at the cadence for the severity. State impact, affected tenants and
   next update time; do not speculate.
6. Preserve evidence: export logs by `traceId`/`requestId`, record timeline and actions, and keep
   database/access audit records.
7. Close only after readiness, tenant sign-in, core request/approval/PO flow and outbox health are
   verified.

## Security and privacy escalation

- Treat suspected cross-tenant access, credential exposure, unauthorized admin action or loss of
  backups as SEV1.
- Rotate `POSTGRES_PASSWORD`, Keycloak admin/client secrets, MinIO keys, Stripe keys and mail
  credentials immediately when exposure is suspected.
- Preserve platform and tenant audit logs; do not delete records during investigation.
- The privacy/legal lead determines breach-notification obligations and deadlines. Do not notify
  customers or regulators without that decision.

## Post-incident

- Write a blameless postmortem within five business days for SEV1/SEV2.
- Record root cause, customer impact, detection gap, timeline, mitigation and prevention items.
- Add a regression test, alert or runbook change for every preventable failure.
- Verify the next backup and restore drill after any data-integrity incident.

## Status page and communications

- Status-page provider: TODO.
- Public status URL: TODO.
- Support inbox/phone: TODO.
- Pre-approved templates: investigating, identified, monitoring, resolved, scheduled maintenance.
- Never include tenant PII, tokens, stack traces or exploit details in public updates.
