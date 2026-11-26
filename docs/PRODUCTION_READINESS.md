# Production Readiness

This document covers the repository-controlled production baseline. External services, legal
documents, managed infrastructure and penetration testing remain operational tasks.

## Backups

Back up all three stateful stores together:

```bash
PATH=/opt/pams/backup:$PATH
COMPOSE_FILE=docker-compose.prod.yml \
BACKUP_ROOT=/var/backups/pams \
BACKUP_RETENTION_DAYS=30 \
ops/backup/backup.sh
```

The script writes a timestamped directory containing:

- `pams.dump` — PostgreSQL application database
- `keycloak.dump` — Keycloak database
- `minio-data.tar.gz` — attachments, logos and favicons
- `SHA256SUMS` and `manifest.txt`

Archives are verified immediately. Re-verify any backup without restoring it:

```bash
BACKUP_DIR=/var/backups/pams/20261126T120000Z ops/backup/verify.sh
# Production compose:
COMPOSE_FILE=docker-compose.prod.yml BACKUP_DIR=/var/backups/pams/20261126T120000Z \
  ops/backup/verify.sh
```

Keep backups encrypted at rest and off the application host.

### Target RPO / RTO

| Item | Target |
|---|---|
| PostgreSQL + Keycloak RPO | 15 minutes |
| Object storage RPO | 24 hours |
| RTO (full stack) | 2 hours |
| Restore drill | Quarterly and after major migrations |

Run a restore drill into a disposable environment:

```bash
BACKUP_DIR=/var/backups/pams/20261126T120000Z \
COMPOSE_FILE=docker-compose.prod.yml \
ops/backup/restore.sh --confirm
```

The restore replaces all three stores and restarts services. Verify readiness, sign-in, request
attachments, branding and `/admin/system`.

## Secrets and configuration

- Keep production secrets in a secret manager or encrypted store, never `.env` in the repository.
- Use `.env.production.example` as the required-variable checklist.
- Rotate `POSTGRES_PASSWORD`, Keycloak admin/service credentials, MinIO keys, Stripe keys and mail
  credentials on a defined schedule and immediately after suspected exposure.
- `PAMS_IMAGE_TAG` must be an immutable git SHA or semantic version; never deploy `latest`.

## TLS and edge protection

Use `ops/production/nginx-pams.conf.example` behind a WAF/load balancer. Enforce HTTPS, HSTS,
secure cookies and request-size limits at the edge. The app containers do not terminate TLS.

## Monitoring and logs

- Readiness/liveness: management port `9090`; do not expose it publicly.
- Watch `/admin/system` for outbox backlog, dead letters, pending approvals and unread notifications.
- Ship JSON logs to a central store with PII redaction and retention.
- Configure uptime checks for `web`, API readiness, Keycloak and object storage.
- Configure error tracking with release/version tags derived from `PAMS_IMAGE_TAG`.
- Set alert thresholds and on-call routing from `docs/INCIDENT_RESPONSE.md`.
- Set OTLP tracing sampling explicitly when a collector is available.

## Security checks

- CI runs dependency scanning and container scanning.
- Run an independent penetration test before the first production customer.
- Keep the tenant-isolation/RLS tests green and repeat the database RLS inventory for every new
  tenant table.
- Rate-limit login, invitations, uploads and exports at the edge or in the application.
- Add backup encryption and restore-access audit before storing real customer data.

## Tenant lifecycle

Implemented:

- Platform admins can export a tenant ZIP from the tenant detail page or
  `GET /api/v1/platform/tenants/{id}/export`. It contains tenant JSON, one JSON file per
  tenant-owned table, manifest/checksums and available MinIO assets.
- Suspended tenants can be permanently deleted from the tenant detail page or
  `DELETE /api/v1/platform/tenants/{id}?confirmSlug=...`. Deletion requires `If-Match`, removes
  tenant data in dependency order, then cleans up Keycloak users and MinIO assets after commit.
- Inactive subscriptions (`CANCELED`, `UNPAID`, expired trial, past-due beyond grace) block tenant
  writes with `422 subscription-inactive` while reads remain available. Subscription checkout,
  portal, preview and Stripe webhook routes are exempt so tenants can restore billing.

Still outstanding:

- Seat/overage policy.

Do not begin paid acquisition until export, deletion and restore procedures are credible.

## Deployments and load tests

- `.github/workflows/release.yml` builds, scans and publishes immutable SHA/version images to GHCR.
- `.github/workflows/deploy.yml` deploys to a GitHub Environment (`staging` or `production`) over SSH.
  Configure required reviewers on the production environment and these environment secrets:
  `DEPLOY_HOST`, `DEPLOY_PORT`, `DEPLOY_USER`, `DEPLOY_PATH`, `DEPLOY_SSH_KEY`,
  `DEPLOY_KNOWN_HOSTS`.
- `ops/production/deploy.sh` pulls the immutable tag, waits for health and rolls back if readiness
  fails. `ops/production/rollback.sh` restores the previous recorded tag.
- Configure `PAMS_BACKEND_IMAGE`, `PAMS_WEB_IMAGE` and an immutable `PAMS_IMAGE_TAG` in the host's
  secret-managed `.env.production`.
- Run `ops/backup/verify.sh` after each backup and record load-test results in `docs/RUNBOOK.md`.
- CI/CD workflow code is present; staging/production accounts, SSH targets and required reviewers
  are still operational configuration.

## Remaining operational tasks

- Separate staging and production accounts and set the GitHub Environment secrets above.
- Managed PostgreSQL with automated PITR; managed Redis with an HA decision.
- Object-storage versioning/lifecycle and encrypted backup storage.
- Status page, on-call routing and external monitoring/error-tracking accounts.
- Run the peak/headroom load test against production-like staging and record results.
- Release notes and customer communication process.
- DPA, privacy policy, terms of service, retention policy and audit-access policy.
