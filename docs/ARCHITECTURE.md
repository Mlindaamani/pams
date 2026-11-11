# PAMS Architecture

## Context

```
Browser SPA (React/TS, OIDC PKCE)
        │  REST /api/v1 (Bearer JWT, Idempotency-Key, If-Match)
        ▼
Spring Boot modular monolith ── PostgreSQL 16 (Flyway, RLS)
        │        │        │        └── Redis (report cache)
        │        │        └── MinIO (attachments)
        │        └── SMTP (Mailpit dev / provider prod)
        └── Keycloak (OIDC) + outbox/notifications + OTLP → Tempo
```

## Modules

- `organization` — organizations, departments, user profiles, lookups
- `vendor`, `catalog` — reference data with soft delete
- `budget` — allocations, atomic reservations/commitments
- `request` — purchase request aggregate, lines, attachments, state machine
- `approval` — policies, chain resolver, instances/steps, delegations, escalation
- `purchaseorder` — numbering, PO lifecycle, PDF
- `notification` — in-app notifications, HTML email templates
- `reporting` — read-model projections, Redis cache, CSV
- `audit` — append-only business audit and keyset query
- `shared` — tenant/security/error/outbox/idempotency/storage/mail/config

Each feature uses `api` (HTTP/DTO/mappers), `application` (use cases/transactions), `domain`
(aggregates/rules/ports/events), `infrastructure` (JPA/adapters). ArchUnit verifies dependencies.

## Key flows

- **Request submit**: validates ownership/state → reserves budget atomically → resolves policy →
  creates approval instance/steps → status `IN_APPROVAL`; all in one transaction.
- **Approval action**: eligibility + optimistic locking → step transition → synchronous event to
  update request/budget → audit event.
- **PO issue**: validates approved request → allocates PO number (`INSERT … ON CONFLICT`) → converts
  reservation to commitment → writes outbox `PurchaseOrderIssued`.
- **Outbox poller**: `FOR UPDATE SKIP LOCKED`, per-event tenant context, retries/dead letters;
  idempotent notification/email handlers.
- **Reports**: SQL projections with tenant-scoped cache keys; write events evict caches.
- **Attachments**: Tika validation → MinIO object → metadata row; downloads streamed through API.

## Failure and consistency

- Optimistic locking (`@Version`) on aggregates and approval steps.
- Idempotency-Key replay for create/submit/approve/PO operations.
- `If-Match`/ETag on mutable resources.
- Outbox guarantees at-least-once delivery for email/notifications.
- Audit table is append-only; Envers is deferred (ADR-024).
