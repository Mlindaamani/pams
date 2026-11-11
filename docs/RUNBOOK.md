# PAMS Runbook

## Local stack

```bash
cp .env.example .env
make up                 # PostgreSQL, Keycloak, MinIO, Redis, Mailpit, web, monitoring
./backend/mvnw -f backend/pom.xml spring-boot:run
```

| Service | URL |
|---|---|
| Web | http://localhost:3000 |
| API | http://localhost:8080 |
| API management | http://localhost:9090/actuator |
| Keycloak | http://localhost:8081 |
| Mailpit | http://localhost:8025 |
| MinIO console | http://localhost:9001 |
| Tempo | http://localhost:3200 (OTLP 4317/4318) |

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
- OTLP traces are sent to `${OTLP_TRACING_ENDPOINT}` (Tempo in Compose). Sampling defaults to 100%
  in dev and 10% in prod.
- Every request accepts/generates `X-Request-ID`; it is echoed and included in error responses.

## Outbox and schedules

- Outbox poller runs every 5s (ShedLock ensures a single runner). Watch `outboxPending` and
  `outboxDeadLettered` on `/admin/system`.
- Dead letters require operator inspection of `outbox_event.payload` and, after fixing the handler,
  clearing `dead_lettered_at`/`processed_at` to replay.
- Approval escalation runs hourly with reminders then escalation.
- ShedLock data lives in the `shedlock` table.

## Database backup/restore

```bash
docker exec pams-postgres pg_dump -U pams -Fc pams > pams.dump
docker exec -i pams-postgres pg_restore -U pams -d pams --clean --if-exists < pams.dump
```

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

1. Check `/admin/system`, readiness and Tempo trace ingestion.
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

### One-time Keycloak admin client setup

Run with the Keycloak master admin password:

```bash
KEYCLOAK_ADMIN_PASSWORD='<master-admin-password>' \
KEYCLOAK_ADMIN_CLIENT_SECRET='change-me-keycloak-admin-secret' \
ops/keycloak/setup-admin-client.sh
```

This creates/updates the confidential `pams-admin` service-account client and grants
`manage-users`, `view-users` and `query-users` from `realm-management`. Ensure `.env` contains the
same `KEYCLOAK_ADMIN_CLIENT_SECRET`, then restart the API:

```bash
docker compose up -d --force-recreate api
```

Alternatively create it in the Keycloak UI: **Clients → Create client → pams-admin**, enable
client authentication and service accounts, copy the client secret to `.env`, then assign the three
realm-management roles under **Service accounts roles**.

### Keycloak user-profile attribute for tenant claims

New Keycloak users need an `organization_id` user-profile attribute so the JWT mapper can emit the
tenant claim. In **Realm settings → User profile**, add:

- Attribute: `organization_id`
- Display name: `Organization ID`
- View: admin, user
- Edit: admin
- Multivalued: off

Without this, Keycloak may create the user but silently discard the `organization_id` attribute.
Existing users can be fixed by setting the attribute under **Users → user → Attributes**.

### Keycloak admin authentication fallback

The preferred production setup is the `pams-admin` service account. In dev, if the service-account
Admin API token is rejected, configure:

```text
KEYCLOAK_ADMIN_AUTH_REALM=master
KEYCLOAK_ADMIN_USER=admin
KEYCLOAK_ADMIN_PASSWORD=<master admin password>
```

The backend then obtains an Admin API token with the master admin password grant. Remove these
values in production and use a working service account.
