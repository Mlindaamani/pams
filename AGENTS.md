# AGENTS.md — Procurement & Approval Management System

Enterprise, multi-tenant procurement API with approval workflows.
**Stack:** Java 21, Spring Boot 3.x (Maven), PostgreSQL 16, Flyway, Redis, Keycloak (OIDC), MinIO, Docker Compose. Frontend: React + TypeScript (Vite), TanStack Query, generated OpenAPI client.

## Source of truth
- Full requirements, data model, API standards and delivery phases: `docs/REQUIREMENTS.md`.
- When starting a phase, read `docs/REQUIREMENTS.md` first (sections relevant to that phase) and implement **only that phase**.
- Record any assumption or trade-off in `docs/DECISIONS.md` (short ADR: context, decision, consequences).

## Workflow rules
1. Work one phase at a time (Phase 0 → 9). Never start the next phase unprompted.
2. Before coding: state a brief plan (files to add/change). After coding: run build and tests, fix failures, then summarize what changed, what was tested, and open questions.
3. Keep changes small and focused. Do not refactor unrelated code.
4. Never invent requirements. If something is ambiguous, pick the simplest reasonable option, document it in `DECISIONS.md`, and continue.
5. Never commit secrets. Use environment variables; keep `.env.example` updated.
6. Don't add a dependency unless needed; mention any new dependency and why in the summary.
7. Don't edit an already-applied Flyway migration. Add a new one.

## Architecture
- **Modular monolith, package-by-feature**: `organization`, `vendor`, `catalog`, `budget`, `request`, `approval`, `purchaseorder`, `receiving`, `notification`, `reporting`, `audit`, plus `shared`.
- Inside each feature: `api/` (controllers, DTOs, mappers), `application/` (services, transactions), `domain/` (entities, rules, events), `infrastructure/` (adapters, JPA).
- Modules talk only via another module's public application service or domain events. Never reach into another module's repositories or entities.
- Boundaries are verified by ArchUnit / Spring Modulith tests. Keep them green.

## Code conventions
- Constructor injection only. No field `@Autowired`. Beans are stateless.
- Controllers: HTTP only, no business logic, no repositories.
- Services: all business rules and `@Transactional` boundaries; one public method per use case.
- Entities: **UUID primary keys**; protect invariants with intention-revealing methods; no public setters for state fields; `equals/hashCode` on ID only.
- **Never return entities from controllers.** Use record DTOs and MapStruct.
- Prefer explicit, inspectable code over clever abstractions or hidden annotation magic.
- Inject `Clock`; never call `Instant.now()` in business code.
- Use `BigDecimal` / `NUMERIC(19,4)` for money, with a currency code. Never `double`.
- Domain errors: throw `DomainException` subclasses with stable error codes; map centrally in `@RestControllerAdvice` to RFC 7807 `ProblemDetail`.
- Config via `@ConfigurationProperties` (validated), not scattered `@Value`.
- No `null` returns for "not found"; throw `ResourceNotFoundException`.
- Logging: SLF4J, parameterized messages; never log tokens, passwords, or PII.
- Format with Spotless (Google Java Format). TypeScript: `strict: true`, no `any`.

## JPA / database
- `spring.jpa.open-in-view=false`; `ddl-auto=validate`; schema changes via Flyway only (`V{n}__desc.sql`).
- All associations `LAZY`. Fetch with `@EntityGraph`, `JOIN FETCH`, or projections. **No N+1**: assert query counts in tests for list endpoints.
- `@Transactional(readOnly = true)` for queries. No remote calls inside transactions. Watch for self-invocation proxy bypass.
- `@Version` on `PurchaseRequest`, `ApprovalStep`, `Budget`, `PurchaseOrder`.
- Every table: `id`, `organization_id`, `created_at/by`, `updated_at/by`; use `timestamptz` (UTC); index every FK and common filter column; partial unique indexes for soft-deleted rows.
- Budget reservation must be concurrency-safe (atomic conditional UPDATE or `SELECT ... FOR UPDATE`) and covered by a concurrency test.

## API standards
- Base path `/api/v1`, plural resource nouns. State changes are action endpoints (`POST /purchase-requests/{id}/submit`), not PATCH of `status`.
- Status codes: `201` + `Location`, `204`, `400` malformed, `401/403` authn/authz, `404` missing **or not visible to caller** (never leak across tenants), `409` state or version conflict, `422` business-rule violation.
- Pagination (`page`, `size` capped at 100, `sort`); keyset pagination for audit log. Whitelist sortable/filterable fields; filters via JPA Specifications.
- `Idempotency-Key` required on create/submit/approve/PO endpoints. `ETag` on GET and `If-Match` on mutations.
- Errors are `application/problem+json` with `code`, `traceId`, and field-level `errors[]`.
- Every endpoint documented in OpenAPI (springdoc) with examples, errors, and security.

## Security
- Stateless OAuth2 Resource Server (Keycloak JWT). Deny by default in `SecurityFilterChain`; add `@PreAuthorize` and ownership checks in services.
- Roles: `REQUESTER`, `APPROVER`, `PROCUREMENT_OFFICER`, `FINANCE`, `ADMIN`, `AUDITOR`. A requester can never approve their own request (enforced in the service layer).
- Multi-tenancy: shared schema with `organization_id`; Hibernate filter **and** PostgreSQL RLS. Every new endpoint needs a tenant-isolation test.
- Ignore client-supplied `id`, `status`, `organizationId`, `createdBy` (no mass assignment). Reject unknown JSON properties.
- CORS: explicit origin allow-list from config. No stack traces in responses.

## Events, async, scheduling
- In-process events via `ApplicationEventPublisher` with `@TransactionalEventListener(AFTER_COMMIT)`.
- Anything that must not be lost (emails, notifications, webhooks) goes through the **outbox table** (written in the same transaction; poller uses `FOR UPDATE SKIP LOCKED`; retries with backoff; idempotent handlers).
- Scheduled jobs use ShedLock.
- Enable virtual threads (`spring.threads.virtual.enabled=true`). Stay on Spring MVC.

## Testing
- JUnit 5, Mockito, AssertJ. Slice tests (`@WebMvcTest`, `@DataJpaTest`). Integration tests with **Testcontainers (real PostgreSQL, never H2)**.
- For every endpoint test: unauthenticated → 401, wrong role → 403, other tenant → 404.
- Required scenarios: happy path (draft → submit → multi-level approval → PO), rejection, change-request loop, cancel releases budget, concurrent approvals, idempotent submit, tenant isolation.
- Target ≥ 80% line coverage on service/domain layers.

## Docker / ops
- Backend: multi-stage Dockerfile, layered jar, non-root user, `-XX:MaxRAMPercentage=75`, health check on readiness, graceful shutdown.
- Frontend: Node build → nginx with SPA fallback and runtime `/config.js`.
- `docker compose up` must work from a clean checkout. Use healthchecks and `depends_on: condition: service_healthy`. Provide a `Makefile` (`up`, `down`, `logs`, `test`, `build`, `lint`).
- Observability: Actuator metrics on a separate management port, OTLP tracing hooks (collector optional), JSON logs with `traceId`, `X-Request-ID` correlation. No Grafana, Prometheus or Tempo services are provisioned.

## Frontend (React + TS)
- Feature-based folders (`features/requests`, `features/approvals`, ...). Feature-specific types and interfaces live in `features/<feature>/types`; shared generated API types stay in `@/api`. API client **generated from OpenAPI**; don't hand-write duplicate types.
- Auth: OIDC Authorization Code + PKCE, token in memory, role-based route guards.
- TanStack Query for server state; React Hook Form + Zod for forms; map `ProblemDetail` errors to UI; handle `409/412` with a "data changed, reload" flow.
- Loading, empty, and error states on every screen; keyboard accessible.
- Prefer full pages for data-entry and management flows. Reserve modals/dialogs for short confirmations.

## Definition of done (every task)
- [ ] Builds clean; lint/format pass
- [ ] Tests written and passing; no N+1 introduced
- [ ] Authz and tenant isolation tested for new endpoints
- [ ] Flyway migration for every schema change
- [ ] OpenAPI updated
- [ ] No secrets or PII in code or logs
- [ ] Audit event recorded for state-changing actions
- [ ] Docs / `DECISIONS.md` updated
- [ ] Works via `docker compose up`