# PAMS Runbook

## Local stack

```bash
cp .env.example .env
make up                 # PostgreSQL, Keycloak, MinIO, Redis, Mailpit, API and Vite web
```

Development Compose mounts the frontend source for Vite HMR and the backend source for Spring Boot
DevTools. After backend edits run:

```bash
make backend-compile    # compile in the API container and trigger DevTools restart
# After frontend dependency changes, recreate the web container or run npm ci in it.
```

| Service | URL |
|---|---|
| Web | http://localhost:3000 |
| API | http://localhost:8080 |
| API management | http://localhost:9090/actuator |
| Keycloak | http://localhost:8081 |
| Mailpit | http://localhost:8025 |
| MinIO console | http://localhost:9001 |

## Keycloak persistence and clients

Keycloak stores realms, users, roles, sessions and client configuration in PostgreSQL database
`keycloak`. Fresh PostgreSQL volumes create it through `ops/postgres/init/01-keycloak-db.sql`; the API
database `pams` is not used by Keycloak. Do not switch back to the embedded H2 database; it is wiped
when the container is recreated.

For an existing PostgreSQL volume created before that init script, create the database once:

```bash
docker exec pams-postgres psql -U pams -d postgres -c "CREATE DATABASE keycloak;"
```

| Client | Type | Used by |
|---|---|---|
| `pams-frontend` | Public, Authorization Code + PKCE | React SPA |
| `pams-api` | Confidential, no interactive flow | API/resource-server identifier |
| `pams-admin` | Confidential service account | Spring Boot Keycloak Admin API |
| `pams-cli` | Confidential, password grant | Local curl and k6 smoke tests only |

`KEYCLOAK_REALM_IMPORT` points at the realm JSON imported on first boot. The committed
`ops/keycloak/realm-export.json` includes the clients, service-account roles, `kc-admin-api` scope,
`organization_id` user-profile attribute and current demo users. To preserve a running realm during
an upgrade, export it first and point `KEYCLOAK_REALM_IMPORT` at that file for the first boot. Realm
import is skipped when the realm already exists.

```bash
# Export the realm with users before replacing Keycloak storage.
docker compose stop keycloak
mkdir -p /tmp/pams-keycloak-export && chmod 777 /tmp/pams-keycloak-export
docker run --rm --network pams_default \
  -v /tmp/pams-keycloak-export:/export \
  -e KC_DB=postgres \
  -e KC_DB_URL=jdbc:postgresql://postgres:5432/keycloak \
  -e KC_DB_USERNAME=pams -e KC_DB_PASSWORD=pams \
  quay.io/keycloak/keycloak:26.8.2 export --db postgres \
  --file /export/pams-realm.json --realm pams --users same_file
docker compose up -d keycloak

# Verify users and clients survive container recreation.
docker compose up -d --force-recreate keycloak
ops/keycloak/verify-admin-client.sh
```

## Health and readiness

- Liveness: `GET :9090/actuator/health/liveness`
- Readiness: `GET :9090/actuator/health/readiness`
- Metrics: `GET :9090/actuator/metrics` (management port)
- Admin UI: `/admin/system` shows outbox backlog, dead letters, pending approvals and build info.

## Rate limiting

Write endpoints under `/api/v1` are limited per user/IP (`pams.rate-limit.*`, default 120 requests
per 60s). Exceeding the bucket returns `429` with `Retry-After`. Disable only in tests.

## Tracing and logs

- Dev: readable logs with `requestId`/`traceId`.
- Prod: JSON logs with `service`, `traceId`, `spanId`, `requestId`.
- OTLP tracing is disabled (`TRACING_SAMPLING_PROBABILITY=0`) while no collector is provisioned.
  Set `OTLP_TRACING_ENDPOINT` and sampling again when Tempo or another collector is restored.
- Every request accepts/generates `X-Request-ID`; it is echoed and included in error responses.

## Outbox and schedules

- Outbox poller runs every 5s (ShedLock ensures a single runner). Watch `outboxPending` and
  `outboxDeadLettered` on `/admin/system`.
- Dead letters require operator inspection of `outbox_event.payload` and, after fixing the handler,
  clearing `dead_lettered_at`/`processed_at` to replay.
- Approval escalation runs hourly with reminders then escalation.
- ShedLock data lives in the `shedlock` table.

### Local token for API tools

The SPA uses `pams-frontend`; password grants are disabled there. Use the local-only `pams-cli`
client for curl and k6:

```bash
curl -s -X POST http://localhost:8081/realms/pams/protocol/openid-connect/token \
  -d grant_type=password -d client_id=pams-cli \
  -d client_secret=change-me-pams-cli-secret -d username=admin -d password=admin
```

## Database backup/restore

```bash
docker exec pams-postgres pg_dump -U pams -Fc pams > pams.dump
docker exec pams-postgres pg_dump -U pams -Fc keycloak > keycloak.dump
docker exec -i pams-postgres pg_restore -U pams -d pams --clean --if-exists < pams.dump
docker exec -i pams-postgres pg_restore -U pams -d keycloak --clean --if-exists < keycloak.dump
```

Back up `pams` and `keycloak` together: the application profiles reference Keycloak subject IDs.
Flyway migrations run on startup; never edit an applied migration.

## Production

```bash
docker compose -f docker-compose.prod.yml build
docker compose -f docker-compose.prod.yml up -d
```

Required environment: `POSTGRES_PASSWORD`, `KEYCLOAK_ADMIN_PASSWORD`, `KEYCLOAK_ISSUER_URI`,
`MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`, `MAIL_*`, `PAMS_PUBLIC_API_URL`, `PAMS_PUBLIC_OIDC_AUTHORITY`,
`PAMS_APP_URL`, `OTLP_TRACING_ENDPOINT`.

The API image runs as a non-root user with a layered jar, graceful shutdown and a readiness
healthcheck. Tag images with the git SHA/semantic version; never deploy `latest`.

## Performance

```bash
k6 run ops/k6/pams-smoke.js   # VUS=20 DURATION=60s to stress
```

The script logs in with a Keycloak password grant, then hits request, vendor and spend-report
endpoints with `p95 < 300ms` and error-rate `< 1%` thresholds. Record results here per release.

| Date | Version | VUs | Duration | p95 | Error rate | Notes |
|---|---|---|---|---|---|---|
| — | — | — | — | — | — | Pending first production-like run |

## Incident checklist

1. Check `/admin/system` and readiness.
2. Inspect API logs by `traceId`/`requestId`.
3. Check outbox backlog/dead letters and `shedlock` locks.
4. Verify Keycloak availability and issuer URL, then DB/Redis/MinIO health.
5. Roll back the API/web image tag; migrations are forward-only — restore the DB backup if a bad
   migration was applied.

## Webhooks

- Configure endpoints in **Administration → Webhooks** (ADMIN only). The signing secret is shown
  once; rotate it from the endpoint list if lost or exposed.
- Delivery rows expose event, attempt count, response status/body and error. Use **Retry** for
  `PENDING` failures or `DEAD_LETTER` rows after fixing the receiver.
- Consumers verify `X-PAMS-Signature` using HMAC-SHA256 over `<timestamp>.<raw body>` and reject old
  timestamps. Deduplicate using `X-PAMS-Delivery` or the body `id`.
- Delivery/retry knobs live under `pams.webhooks` (`max-attempts`, batch size, timeouts, poll
  interval). Manual retries reset the attempt counter.
- Actuator metrics stay on the management port. In production that port is not published externally.

## Platform administration

- Use a `PLATFORM_ADMIN` user for `/platform/dashboard` and `/platform/tenants`.
- Suspending a tenant blocks its normal API traffic within the tenant-status cache window (30s).
  Activate it to restore access.
- Platform analytics is aggregate-only, multi-currency (no FX conversion) and cached briefly.
- Platform actions are recorded in `platform_audit_event`; tenant audit remains separate.

## SaaS subscriptions and onboarding

- Plans and tenant subscription state are in `subscription_plan` and `tenant_subscription`.
- Configure Stripe test mode with `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`, and the per-plan
  `stripe_price_monthly` / `stripe_price_annual` IDs in `subscription_plan`.
- Stripe webhook endpoint: `POST /api/v1/webhooks/stripe` using the signing secret above.
- Stripe Checkout/Billing Portal are initiated from **Administration → Billing & subscription**.
- Platform tenant onboarding can queue a tenant System Admin invitation and starts a trial
  subscription.
- Tenant System Admin invitations use the Keycloak Admin API. Configure:
  - `KEYCLOAK_ADMIN_BASE_URL`
  - `KEYCLOAK_ADMIN_REALM`
  - `KEYCLOAK_ADMIN_CLIENT_ID` (`pams-admin` in the realm export)
  - `KEYCLOAK_ADMIN_CLIENT_SECRET`
- The `pams-admin` service account needs realm-management rights to create users, read roles,
  assign realm roles and disable users (`manage-users`, `view-users`, `query-users`).
- Keycloak email is not used for invitations in this increment. A temporary password is created and
  sent through the application mail adapter (Mailpit in dev).
- If Keycloak admin or Stripe credentials are absent, the corresponding actions fail with a clear
  validation message; tenants and trial records are still created.

### Keycloak admin client verification and repair

The committed realm export creates `pams-admin`, assigns `manage-users`, `view-users`,
`query-users` and `view-realm`, and attaches the `kc-admin-api` default client scope. Verify a clean
import with:

```bash
ops/keycloak/verify-admin-client.sh
```

If the Admin API returns `401`, repair the client with the master admin password:

```bash
KEYCLOAK_ADMIN_PASSWORD='<master-admin-password>' \
KEYCLOAK_ADMIN_CLIENT_SECRET='change-me-keycloak-admin-secret' \
ops/keycloak/setup-admin-client.sh
ops/keycloak/verify-admin-client.sh
```

Ensure `.env` contains the same `KEYCLOAK_ADMIN_CLIENT_SECRET`; restart the API after changing it.

### Keycloak user-profile attribute for tenant claims

The realm export declares the `organization_id` user-profile attribute, and the client protocol
mappers emit it as a JWT claim. It is visible to admins and users, editable by admins only and not
multivalued. If an older realm is imported without it, add it under **Realm settings → User profile**,
then restart the API and sign in again.

### Keycloak admin authentication fallback

The preferred and default setup is the `pams-admin` service account. The master-admin password
fallback is an emergency-only option for local repair:

```text
KEYCLOAK_ADMIN_AUTH_REALM=master
KEYCLOAK_ADMIN_FALLBACK_USER=admin
KEYCLOAK_ADMIN_FALLBACK_PASSWORD=<master admin password>
```

Use it only to repair a broken service account, then remove the fallback values. Production must use
`pams-admin`.

### Existing orphaned profiles

If Keycloak was previously recreated without persistent storage, some PostgreSQL profiles may point
at Keycloak subjects that no longer exist. Report them with:

```bash
python3 ops/keycloak/reconcile_orphan_profiles.py
python3 ops/keycloak/reconcile_orphan_profiles.py --json
```

The script is a dry run and never changes data. Action meanings:

- `REINVITE_AND_RELINK` — user owns current requests, POs, favorites or notifications; re-invite, then relink their profile and operational references.
- `REINVITE_OR_REASSIGN` — user is only referenced as a manager/delegator; re-invite or reassign those references.
- `REASSIGN_REFS_THEN_SOFT_DELETE` — duplicate/inactive identity with references; reassign them, then soft-delete the profile.
- `REVIEW_DUPLICATE_HISTORY` — duplicate email with historical approvals/audit; review before merging or soft-deleting.
- `SOFT_DELETE` / `SOFT_DELETE_DUPLICATE` — no activity or references; soft-delete is safe after backup.
- `--fail-on-orphans` exits with status `2`, useful for operational checks.

Reassign managers, delegations and favorites before soft-deleting. Do not mutate historical audit subjects.
