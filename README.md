# PAMS — Procurement & Approval Management System

Multi-tenant procurement API: purchase requests → multi-level approvals → purchase orders →
receiving → reporting, with a complete audit trail.

This repository is built phase by phase following `docs/REQUIREMENTS.md`. Architecture decisions
and trade-offs are recorded in `docs/DECISIONS.md`. The commercial build, go-to-market and
SaaS metrics plan is in `docs/SAAS_ROADMAP.md`. Subscription and Stripe capabilities are documented
in `docs/SUBSCRIPTION_BILLING.md`.

## Repository layout

```
backend/   Spring Boot 3.5 (Java 21) modular monolith
frontend/  React + TypeScript (delivered in Phase 7)
ops/       Keycloak and PostgreSQL operational scripts, load tests
docs/      Requirements, decisions, runbook, architecture, SaaS roadmap, billing
```

## Prerequisites

- JDK 21+ (a full JDK with `javac`; the build targets Java 21)
- Docker + Docker Compose
- Node.js 20+ (frontend, Phase 7)

## Quick start

```bash
cp .env.example .env
make up                 # PostgreSQL, Keycloak, MinIO, Mailpit, Redis and the web container
./backend/mvnw -f backend/pom.xml spring-boot:run
```

The API listens on `http://localhost:8080`; OpenAPI JSON is available at
`http://localhost:8080/openapi.json` (or `/v3/api-docs`), Swagger UI at `/swagger-ui.html`, and
Actuator (separate management port) at `http://localhost:9090/actuator`. The OpenAPI document is at
`http://localhost:8080/openapi.json`.
Keycloak is at `http://localhost:8081` (realm `pams`). The master admin comes from `KEYCLOAK_ADMIN`/`KEYCLOAK_ADMIN_PASSWORD` (`admin`/`admin` in `.env.example`).

All API endpoints require a Keycloak bearer token. Demo users (password = username): `admin`
(ADMIN), `officer` (PROCUREMENT_OFFICER), `requester` (REQUESTER), `approver` (APPROVER), `finance`
(FINANCE), `auditor` (AUDITOR). Get a token with:

```bash
curl -s -X POST http://localhost:8081/realms/pams/protocol/openid-connect/token \
  -d grant_type=password -d client_id=pams-cli -d client_secret=change-me-pams-cli-secret \
  -d username=admin -d password=admin
```

The web app is at `http://localhost:3000`. In development Compose runs Vite with the frontend source
bind-mounted for HMR, and the API runs from the mounted backend source with Spring Boot DevTools.
After backend edits run `make backend-compile`; DevTools restarts the API. Production images are
still built through `docker compose -f docker-compose.prod.yml build`.

The SPA signs in with the public `pams-frontend` client; `pams-cli` is only for local API tools and
smoke tests. Keycloak users and clients persist in the dedicated `keycloak` PostgreSQL database.

The dev profile seeds the demo organization `11111111-1111-1111-1111-111111111111`; the JWT
`organization_id` claim carries the tenant. Use `curl` or any OpenAPI-capable client against
`http://localhost:8080/openapi.json`.

Flyway runs automatically on startup. To apply migrations without starting the app:

```bash
make migrate
```

## Build and test

```bash
make test     # unit + Testcontainers integration tests (real PostgreSQL)
make build    # verify: format check, tests, coverage report
make lint     # Spotless (Google Java Format) check
make format   # apply formatting
```

Tests use Testcontainers and therefore require a running Docker daemon. H2 is never used.

If you use the VS Code Java extension, disable its autobuild for this workspace
(`"java.autobuild.enabled": false` in `.vscode/settings.json`) — the Eclipse-based language server
may overwrite Maven's `target/classes` with partial ECJ output while you run the build.

## Configuration

Profiles: `dev` (default), `prod`. Dev defaults are in `.env.example`; production values come from
environment variables only. Never commit `.env` or secrets.

## Current status

Phases 0–8 and product Phase 9 (receipts, invoices, webhooks) are complete, plus permission-based authorization and configurable workflow actions:

- Phase 0: Maven/Spring Boot skeleton, package-by-feature layout, Flyway baseline, base/tenant
  entities with auditing, RFC 7807 error handling with correlation ids, OpenAPI, ArchUnit rules,
  Testcontainers persistence tests, PostgreSQL Compose, Makefile and CI skeleton.
- Phase 1: organizations, departments, user profiles (manager chain + approval limit), vendors
  (status lifecycle, soft delete) and catalog items. Pagination/filtering/sort whitelists, MapStruct
  DTOs and tenant-isolation tests.
- Phase 2: Keycloak realm import, JWT resource server, realm-role mapping to `ROLE_*`, method
  security per endpoint, CORS allow-list, tenant resolution from the `organization_id` JWT claim,
  Hibernate tenant filter plus PostgreSQL RLS (`pams_app` role), RFC 7807 401/403 responses and
  security/RLS tests.
- Phase 3: departmental budgets with concurrency-safe reservations, purchase request aggregate with
  server-computed totals and explicit state machine, submit/cancel with budget reservation/release,
  ETag/`If-Match`, `Idempotency-Key` replay, search/filter/sort with N+1-safe list queries,
  attachments in MinIO with Tika validation and streamed downloads.
- Phase 4: versioned amount-tier approval policies, chain resolution strategy, approval
  instances/steps, approve/reject/request-changes, approval limits, delegation, reminder/escalation
  job with ShedLock, append-only business audit events, and request status/budget outcomes applied
  through synchronous domain events.
- Phase 5: purchase orders with per-organization/year numbering, conversion from approved requests,
  budget commitment conversion, issue/cancel actions, on-demand PDF, transactional outbox with a
  ShedLock poller (retries/dead-letter), in-app notifications and SMTP email via Mailpit.
- Phase 6: projection-based spend/cycle-time/commitment reports, Redis caching with TTL and
  invalidation events, streamed CSV export, and a keyset-paginated audit log for ADMIN/AUDITOR.
- Phase 7: React + TypeScript SPA with generated OpenAPI client, OIDC Authorization Code + PKCE,
  CASL role-based UI permissions, TanStack Query, RHF + Zod forms, screens for requests, approvals,
  vendors, budgets, POs, reports, policies and audit, plus Vitest/Playwright scaffolds and a hardened
  nginx image served by Compose.

`docs/DECISIONS.md` records the Phase 3 trade-offs (department-level budget reservation,
submission-year fiscal year, idempotency scope, attachment streaming).
