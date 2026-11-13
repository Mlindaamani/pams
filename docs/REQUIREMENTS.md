# Procurement & Approval Management System — Requirements & Engineering Standards

> **Instructions to the coding agent:** Read this entire document before writing code. Build the system in the phases defined in section 15. After each phase, stop, make sure the build and all tests pass, and summarize what was done before proceeding. Do not skip ahead. If a requirement is ambiguous, state your assumption in `docs/DECISIONS.md` (ADR style) and continue. Prefer explicit, readable code over clever abstractions.
>
> **Commercial roadmap:** Production SaaS readiness, go-to-market phases, pricing, payment rails and
> the five core SaaS metrics are documented in `docs/SAAS_ROADMAP.md`.

---

## 1. Project Overview

A multi-tenant enterprise web application that manages the full purchase lifecycle: **purchase requests → multi-level approvals → vendor selection → purchase orders → receiving → reporting**, with a complete audit trail.

- **Backend:** Java 21, Spring Boot 3.x (latest stable 3.x), Maven
- **Database:** PostgreSQL 16+
- **Frontend:** React + TypeScript (Vite), TanStack Query, generated OpenAPI client
- **Infra (local and prod):** Docker, Docker Compose
- **Identity:** Keycloak (OAuth2 / OIDC), backend acts as a JWT Resource Server
- **Other:** Redis (cache), MinIO (S3-compatible file storage), Mailpit (dev email), Flyway (migrations)

### Goals
1. Production-grade API quality: secure, observable, tested, documented.
2. A clean modular monolith that could be split later.
3. Teach and demonstrate enterprise patterns (workflow, audit, idempotency, optimistic locking, outbox).

### Non-goals (v1)
- Payment processing, ERP integration, mobile app, e-signature, multi-currency conversion.

---

## 2. Actors and Roles (RBAC)

| Role | Description |
|---|---|
| `REQUESTER` | Creates and submits purchase requests; views own requests |
| `APPROVER` | Approves or rejects requests assigned to them at their level |
| `PROCUREMENT_OFFICER` | Manages vendors, converts approved requests to purchase orders |
| `FINANCE` | Manages budgets, views all spend, verifies invoices |
| `ADMIN` | Manages users, departments, approval policies, system settings |
| `AUDITOR` | Read-only access to everything, including the audit log |

Rules:
- Roles are issued by Keycloak in the JWT (`realm_access.roles`) and mapped to Spring `GrantedAuthority` as `ROLE_*`.
- A user belongs to exactly one **organization (tenant)** and one **department**.
- A requester can never approve their own request (enforced in the service layer, not only the UI).

---

## 3. Functional Requirements

### 3.1 Organizations, Departments, Users
- FR-1.1 Admin can CRUD departments within their organization.
- FR-1.2 Users are provisioned in Keycloak; the app creates a local `user_profile` row on first login (JIT provisioning) linking `keycloak_subject` to department, manager and approval limit.
- FR-1.3 Each user can have an **approval limit** (monetary) and a **manager** (used by approval chains).

### 3.2 Vendors
- FR-2.1 Procurement officers CRUD vendors (name, tax ID, contact info, bank details, status: `ACTIVE | SUSPENDED | BLACKLISTED`).
- FR-2.2 Vendor tax ID is unique per organization.
- FR-2.3 Vendors that are not `ACTIVE` cannot be selected on new purchase orders.
- FR-2.4 Soft delete only (`deleted_at`); never hard delete vendors referenced by documents.

### 3.3 Catalog (optional but recommended)
- FR-3.1 Catalog items: SKU, name, unit, default price, category. Requests may reference catalog items or contain free-text line items.

### 3.4 Budgets
- FR-4.1 Finance defines budgets per department per fiscal year (and optionally per category).
- FR-4.2 When a request is **submitted**, the amount is **reserved (committed)** against the budget. On rejection or cancellation, the reservation is released. On PO issue, the reservation converts to a commitment; on invoice, to actual spend.
- FR-4.3 Submission fails with `422` (problem type `budget-exceeded`) if remaining budget is insufficient, unless the request is flagged for a budget exception that requires FINANCE approval.
- FR-4.4 Budget operations must be safe under concurrency (see section 9.4).

### 3.5 Purchase Requests (core aggregate)
- FR-5.1 A requester creates a request in `DRAFT` with header (title, justification, department, needed-by date, currency) and 1..N line items (description, quantity, unit, unit price, optional catalog reference).
- FR-5.2 Totals are computed server-side. Clients never send totals.
- FR-5.3 Drafts can be edited and deleted only by the owner.
- FR-5.4 Attachments (quotes, specs): PDF/PNG/JPG/DOCX/XLSX, max 10 MB each, max 10 per request, stored in MinIO, with content-type sniffing and virus-scan hook (interface only in v1).
- FR-5.5 Submit action moves the request to `SUBMITTED`, resolves the approval chain, and creates approval steps (see 3.6).
- FR-5.6 Search, filter and sort: by status, department, requester, date range, amount range, vendor, free-text on title. Paginated.

### 3.6 Approval Workflow
- FR-6.1 Approval chains are **configurable policies** per organization, e.g.:
  - amount < 1,000 → direct manager
  - 1,000 ≤ amount < 10,000 → manager, then department head
  - amount ≥ 10,000 → manager, department head, FINANCE, then ADMIN/CFO
- FR-6.2 Policies are stored in the database (`approval_policy`, `approval_policy_step`), editable by ADMIN, versioned. A request snapshots the policy version it was submitted under.
- FR-6.3 Each step supports: `PENDING | APPROVED | REJECTED | SKIPPED | DELEGATED`.
- FR-6.4 Approver actions: **approve**, **reject** (comment mandatory), **request changes** (returns to requester, comment mandatory).
- FR-6.5 Approvers can **delegate** to another eligible user for a time range (out-of-office).
- FR-6.6 Escalation: steps pending longer than a configurable SLA trigger a reminder, then escalate to the next level (scheduled job).
- FR-6.7 Every transition validates: current state, actor is the assigned approver (or valid delegate), actor is not the requester, approval limit not exceeded.
- FR-6.8 Two approvers acting simultaneously must not cause a lost update or double transition (optimistic locking + state guard).

### 3.7 Request State Machine

```
DRAFT ──submit──▶ SUBMITTED ──(first step active)──▶ IN_APPROVAL
IN_APPROVAL ──all steps approved──▶ APPROVED
IN_APPROVAL ──reject──▶ REJECTED
IN_APPROVAL ──request changes──▶ CHANGES_REQUESTED ──resubmit──▶ SUBMITTED
DRAFT | SUBMITTED | CHANGES_REQUESTED ──cancel──▶ CANCELLED
APPROVED ──create PO──▶ CONVERTED_TO_PO
```

- Implement as an explicit enum + `canTransitionTo` rules in the domain layer. **No library state machine needed in v1**; keep it inspectable and unit-tested. Illegal transitions throw `IllegalStateTransitionException` → `409 Conflict`.

### 3.8 Purchase Orders
- FR-8.1 Procurement officer creates a PO from an `APPROVED` request: select vendor, confirm prices, payment terms, delivery date.
- FR-8.2 PO statuses: `DRAFT | ISSUED | PARTIALLY_RECEIVED | RECEIVED | CLOSED | CANCELLED`.
- FR-8.3 Unique human-readable PO number per organization (`PO-2026-000123`), generated via a database sequence per org/year (not `MAX()+1`).
- FR-8.4 PO PDF generation (OpenPDF or similar) and download.
- FR-8.5 Issuing a PO emits a `PurchaseOrderIssued` event (outbox) → email to vendor contact (Mailpit in dev).

### 3.9 Goods Receipt & Invoice Matching (phase 2 of the product)
- FR-9.1 Record receipts against PO lines (partial allowed). Cannot exceed ordered quantity.
- FR-9.2 Record vendor invoice, perform **three-way match** (request/PO ↔ receipt ↔ invoice) with configurable tolerance %; mismatches flagged for FINANCE.

### 3.10 Notifications
- FR-10.1 Events: request submitted, approval assigned, approved, rejected, changes requested, PO issued, escalation, delegation.
- FR-10.2 Channels: in-app (stored, with unread count API) and email. Notification sending is **asynchronous** and **never blocks** the main transaction.

### 3.11 Reporting
- FR-11.1 Spend by department / vendor / category / month (date range filters).
- FR-11.2 Approval cycle time (average, p50, p90) per policy and per approver.
- FR-11.3 Open commitments vs. budget.
- FR-11.4 CSV export (streamed, not loaded fully into memory). PDF export optional.
- FR-11.5 Reports use read-optimized queries/projections, are cached with TTL, and respect tenant and role scoping.

### 3.12 Audit Trail
- FR-12.1 Every create/update/delete of business entities and every state transition is recorded: who, when, what changed (before/after), request ID / correlation ID, source IP.
- FR-12.2 Audit records are **append-only** (no update/delete API; DB-level restriction recommended).
- FR-12.3 AUDITOR and ADMIN can query the audit log with filters and pagination.
- Implementation: Hibernate Envers for entity history **plus** an explicit `audit_event` table for business actions (approve/reject/etc.) with a JSONB payload.

---

## 4. Non-Functional Requirements

| Area | Requirement |
|---|---|
| Performance | p95 < 300 ms for list/detail endpoints at 100 concurrent users on modest hardware; no N+1 queries (enforced by tests) |
| Availability | Stateless API, horizontally scalable; graceful shutdown; health/readiness/liveness probes |
| Security | OWASP ASVS L2 baseline; OWASP API Security Top 10 addressed (see section 10) |
| Data integrity | All money in `NUMERIC(19,4)` / `BigDecimal`, never `double`; explicit currency code |
| Observability | Metrics, structured logs, distributed tracing, correlation IDs |
| Maintainability | Modular monolith with enforced module boundaries (ArchUnit / Spring Modulith) |
| Test coverage | ≥ 80% line coverage on service/domain layers; critical flows covered by integration tests |
| Compatibility | API versioned under `/api/v1`; backward-compatible changes only within a version |

---

## 5. Architecture

### 5.1 Style
**Modular monolith**, package-by-feature, with optional hexagonal separation inside complex modules. Use **Spring Modulith** (or ArchUnit) to verify modules only talk through their public API / events.

### 5.2 Modules
```
com.company.procurement
├── shared/            # common kernel: errors, base entities, security utils, pagination, idempotency, outbox
├── organization/      # organizations, departments, user profiles
├── vendor/
├── catalog/
├── budget/
├── request/           # purchase requests, line items, attachments, state machine
├── approval/          # policies, steps, delegation, escalation
├── purchaseorder/
├── receiving/         # receipts, invoices, three-way match
├── notification/
├── reporting/
└── audit/
```

### 5.3 Layers inside each module
```
feature/
├── api/          # @RestController, request/response DTOs (records), mappers
├── application/  # @Service use-cases, transaction boundaries, orchestration
├── domain/       # entities, enums, domain rules, domain events, repository interfaces
└── infrastructure/ # JPA repositories, external clients (storage, email), adapters
```

Rules:
- **Controllers:** HTTP concerns only. No business logic, no repositories injected.
- **Services (application layer):** all business rules and `@Transactional` boundaries. One public method = one use case.
- **Domain:** entities protect their own invariants (no public setters for state-bearing fields; use intention-revealing methods such as `request.submit(...)`).
- **Cross-module calls:** via the other module's public application service interface or via domain events; never reach into another module's repositories or entities.

### 5.4 Design Patterns to Apply (and where)
| Pattern | Where |
|---|---|
| Repository | Spring Data JPA repositories per aggregate |
| DTO + Mapper | MapStruct between API DTOs and domain; entities never leave the service layer |
| Strategy | Approval chain resolution (`ApprovalChainResolver` implementations), PO number generation, notification channels |
| State | Purchase request / PO lifecycle (enum + transition rules in domain) |
| Specification | Dynamic search/filter on requests, POs, audit log (JPA `Specification`) |
| Factory / Builder | Creating aggregates with invariants (static factories); test data builders |
| Observer / Domain Events | `ApplicationEventPublisher` for in-process events (`RequestSubmitted`, `RequestApproved`, ...) |
| Outbox | Reliable async side effects (emails, webhooks): write event row in the same transaction, a poller publishes it |
| Adapter / Port | `FileStoragePort` (MinIO adapter), `MailPort` (SMTP adapter), `PdfPort` |
| Idempotency Key | Submit/approve/create-PO endpoints |
| Optimistic Locking | `@Version` on aggregates |

---

## 6. Data Model (guidelines)

General rules:
- **Primary keys: UUIDs** (UUIDv7 preferred if a library is added, otherwise `gen_random_uuid()`), type `uuid`.
- Every table has: `id`, `organization_id` (tenant), `created_at`, `created_by`, `updated_at`, `updated_by`, `version` (for aggregates).
- Use `timestamptz` and store/process in UTC.
- Enums stored as `varchar` with a CHECK constraint (not ordinal).
- Foreign keys and indexes on every FK and on all common filter/sort columns; add composite indexes for tenant + status + created_at style queries.
- Partial unique indexes for soft-delete-aware uniqueness (e.g., `UNIQUE (organization_id, tax_id) WHERE deleted_at IS NULL`).
- No business logic in the database except constraints, indexes and sequences.

Core tables (indicative; refine as needed):
`organization`, `department`, `user_profile`, `vendor`, `catalog_item`, `budget`, `budget_reservation`, `purchase_request`, `purchase_request_line`, `request_attachment`, `approval_policy`, `approval_policy_step`, `approval_instance`, `approval_step`, `delegation`, `purchase_order`, `purchase_order_line`, `goods_receipt`, `goods_receipt_line`, `invoice`, `notification`, `outbox_event`, `idempotency_record`, `audit_event`, plus Envers `*_aud` tables.

Migrations:
- **Flyway only.** Naming `V{n}__{description}.sql`. Never edit an applied migration. `spring.jpa.hibernate.ddl-auto=validate`.
- Seed data (roles are in Keycloak; sample org/departments for dev) in a separate `R__` repeatable or dev-profile-only migration.

---

## 7. API Design Standards

- Base path `/api/v1`. Resource-oriented, plural nouns: `/purchase-requests`, `/purchase-requests/{id}/line-items`.
- State transitions as **action sub-resources**, not generic PATCH of `status`:
  - `POST /purchase-requests/{id}/submit`
  - `POST /purchase-requests/{id}/cancel`
  - `POST /approvals/{stepId}/approve | reject | request-changes | delegate`
  - `POST /purchase-requests/{id}/purchase-orders`
- HTTP semantics: `201 Created` + `Location` header on creation, `204` on no-content, `409` for state/version conflicts, `422` for business-rule violations, `400` for malformed input, `401/403` for authn/authz, `404` for missing **or not visible to caller** (avoid leaking existence across tenants).
- **Pagination:** `?page=0&size=20&sort=createdAt,desc`; max size capped at 100 server-side. Response envelope with `content`, `page`, `size`, `totalElements`, `totalPages`. For audit log and other large tables, provide **keyset (cursor) pagination**.
- **Filtering:** query params mapped to JPA Specifications; whitelist sortable/filterable fields.
- **Errors:** RFC 7807 `application/problem+json` via `ProblemDetail`, with fields `type`, `title`, `status`, `detail`, `instance`, plus `traceId`, `errors[]` (field-level validation errors) and a stable machine-readable `code`.
- **Validation:** Bean Validation (`@Valid`) on DTOs plus domain validation in services. Custom validators where needed.
- **Optimistic concurrency for clients:** return `ETag` (version) on GET; require `If-Match` on PUT/PATCH/actions that mutate; return `412`/`409` on mismatch.
- **Idempotency:** `Idempotency-Key` header required on `POST` create/submit/approve/PO endpoints. Store key + request hash + response for 24h; replay returns the stored response; same key with different payload → `422`.
- **Dates/money:** ISO-8601 UTC strings; money as `{ "amount": "1234.5000", "currency": "TZS" }` (string amount to avoid float issues).
- **OpenAPI:** springdoc-openapi generates the spec; every endpoint documented with examples, error responses and security requirements. The spec is published at `/v3/api-docs` and committed as `openapi.json` in CI for client generation.
- **Versioning:** URI versioning; deprecations announced via `Deprecation`/`Sunset` headers.
- **Rate limiting:** Bucket4j (or gateway-level) per user/IP on auth-sensitive and write endpoints; return `429` with `Retry-After`.
- **Compression and caching headers:** enable response compression; `Cache-Control: no-store` on sensitive responses.

---

## 8. Spring Boot & JPA Best Practices (mandatory)

### 8.1 General
- **Constructor injection only** (Lombok `@RequiredArgsConstructor` or explicit constructors). No field `@Autowired`.
- Beans are **stateless singletons**.
- Use **records** for DTOs, commands, queries and value objects where possible.
- Use `@ConfigurationProperties` (with `@Validated`) for all custom config; no scattered `@Value`.
- Use `Clock` bean injection for time (never `Instant.now()` directly in business code) so time is testable.
- Prefer `RestClient` (or `WebClient` if reactive) for HTTP calls with timeouts, retries (Resilience4j) and circuit breakers.
- Java 21: enable **virtual threads** (`spring.threads.virtual.enabled=true`); stay on Spring MVC (not WebFlux).
- Keep code **explicit and inspectable**: avoid heavy reflection tricks, custom annotations that hide behavior, or over-generic base classes.

### 8.2 JPA / Hibernate
- `spring.jpa.open-in-view=false`.
- All associations `FetchType.LAZY`; fetch what you need with `@EntityGraph`, `JOIN FETCH` or DTO projections.
- **No N+1:** add a test using Hibernate statistics (or `datasource-proxy` / `QuickPerf`) asserting query counts on list endpoints.
- Read endpoints use **projections** (records/interfaces) or constructor expressions rather than loading full entities.
- `@Transactional(readOnly = true)` on query use-cases; write transactions as short as possible, no remote calls inside them.
- Beware **self-invocation** (proxy bypass) for `@Transactional`/`@Async`/`@Cacheable`.
- Equality: implement `equals/hashCode` on entities based on the ID only (or a business key); never include lazy collections in `toString/equals/hashCode`.
- Use `@Version` for optimistic locking on `PurchaseRequest`, `ApprovalStep`, `Budget`, `PurchaseOrder`.
- Use `jdbc.batch_size`, `order_inserts`, `order_updates` for bulk writes.
- Avoid `CascadeType.REMOVE` / `orphanRemoval` unless the child truly has no independent life.
- Never expose entities from controllers.

### 8.3 Transactions & Concurrency
- Transaction boundary = application service method.
- Budget reservation: use **pessimistic lock** (`SELECT ... FOR UPDATE`) or an atomic conditional `UPDATE ... WHERE remaining >= :amount` to avoid overspend under concurrency. Write a concurrency test proving it.
- State transitions: guard with expected-status check + `@Version`.
- Publish domain events with `@TransactionalEventListener(phase = AFTER_COMMIT)` for in-process reactions; use the **outbox** for anything that must not be lost.

### 8.4 Outbox Pattern
- `outbox_event` table: `id`, `aggregate_type`, `aggregate_id`, `event_type`, `payload (jsonb)`, `created_at`, `processed_at`, `attempts`, `next_attempt_at`.
- Event written in the same transaction as the state change.
- A scheduled poller (`@Scheduled` + `SELECT ... FOR UPDATE SKIP LOCKED`) dispatches events to handlers (email, notification). Retries with exponential backoff, dead-letter after N attempts.
- Handlers must be **idempotent**.
- (Kafka/RabbitMQ is an optional later swap; keep handlers behind an interface.)

### 8.5 Caching
- Spring Cache + Redis for reference data (vendors list, policies, catalog) and reports; explicit TTLs; explicit eviction on writes; cache keys include tenant ID.
- Never cache user-specific authorization decisions without a short TTL.

### 8.6 Scheduling
- Use **ShedLock** (JDBC) so scheduled jobs (escalation, outbox poller, cleanup) run on exactly one instance.

### 8.7 File Handling
- Upload via `multipart/form-data`; stream to MinIO; store only metadata (key, size, content type, checksum, uploader) in DB.
- Validate size, extension and **actual content type** (Apache Tika); generate storage keys server-side (UUID), never use client file names as paths.
- Download via short-lived **pre-signed URLs** or streamed through the API with authorization checks.

---

## 9. Multi-Tenancy

- **Model:** shared database, shared schema, `organization_id` discriminator column on every tenant-owned table.
- Resolve tenant from the JWT (`organization_id` claim mapped in Keycloak) in a filter; store in a request-scoped `TenantContext` (clear it in `finally`; remember virtual-thread/async propagation).
- Enforce isolation in **two layers**:
  1. Hibernate `@Filter` (or `@TenantId` in Hibernate 6.3+) enabled per request;
  2. PostgreSQL **Row Level Security** policies as defense in depth (set `app.current_org` per connection/transaction).
- Include **tenant-isolation tests**: user from org A must never read/modify org B data through any endpoint (return `404`).
- Unique constraints and caches must be tenant-scoped.

---

## 10. Security Requirements

- **Authentication:** Keycloak OIDC; API is a stateless **OAuth2 Resource Server** validating JWT signature, issuer, audience, expiry. No sessions, no custom password handling.
- **Authorization:** URL-level rules in `SecurityFilterChain` (deny by default) + method-level `@PreAuthorize` using roles and ownership checks (e.g., `@requestSecurity.canView(#id)`), plus resource-level checks inside services.
- **CORS:** explicit allow-list of origins from configuration (React dev and prod domains), explicit methods and headers; no wildcard with credentials.
- **CSRF:** disabled for the stateless JWT API (document why); frontend stores tokens in memory (not localStorage), uses Authorization Code + PKCE.
- **Input handling:** parameterized queries only (JPA/Specification); validate all input; limit request body size; reject unknown JSON properties (`FAIL_ON_UNKNOWN_PROPERTIES=true`).
- **Output:** never return stack traces or internal exception messages; sanitize error details.
- **Secrets:** only from environment variables or a secrets manager; nothing committed; `.env.example` provided; `.env` git-ignored.
- **Security headers:** HSTS, `X-Content-Type-Options`, `X-Frame-Options`/CSP via Spring Security headers config (and nginx in front).
- **Mass assignment:** DTOs expose only writable fields; ignore client-supplied `id`, `status`, `organizationId`, `createdBy`.
- **BOLA/IDOR prevention:** every by-ID lookup is scoped by tenant and checked for ownership/role.
- **Dependency hygiene:** OWASP Dependency-Check (or Trivy/Snyk) in CI; Dependabot/Renovate enabled.
- **Logging:** never log tokens, passwords, full PII, or bank details; mask sensitive fields.
- **Audit of auth events:** log failed authz decisions with correlation ID.

---

## 11. Observability

- **Actuator** endpoints: `health` (with `liveness` and `readiness` groups), `info`, `metrics`. Expose management endpoints on a **separate port** not publicly routed.
- **Metrics:** Micrometer/Actuator metrics; custom business metrics (requests submitted, approvals by outcome, outbox lag, cycle time).
- **Logging:** structured JSON (Logback + logstash encoder or Spring Boot 3.4+ structured logging); each line includes `traceId`, `spanId`, `requestId`, `userId`, `orgId`.
- **Correlation:** accept/generate `X-Request-ID`, echo in the response and in error bodies.
- **Tracing:** Micrometer Tracing + OpenTelemetry exporter (OTLP); no collector is bundled in Compose.
- HikariCP metrics enabled; slow query logging threshold configured.

---

## 12. Testing Strategy

| Level | Tools | Scope |
|---|---|---|
| Unit | JUnit 5, Mockito, AssertJ | Domain rules, state machine, services, approval chain resolution, budget math |
| Slice | `@WebMvcTest`, `@DataJpaTest` | Controllers (validation, status codes, error shape, security), repositories/specifications |
| Integration | `@SpringBootTest` + **Testcontainers** (Postgres, Redis, MinIO, Keycloak or mocked JWT) | End-to-end flows, Flyway migrations, outbox, RLS/tenancy |
| Concurrency | Executor/CompletableFuture-based tests | Double-approve, budget overspend, idempotency replay |
| Architecture | ArchUnit / Spring Modulith `verify()` | Layer and module boundaries, no entity in controllers, no field injection |
| Contract | OpenAPI diff in CI (`openapi-diff`) | Prevent accidental breaking API changes |
| Frontend | Vitest + React Testing Library; Playwright for key E2E flows | Forms, permissions, workflow happy path |

Rules:
- **No H2.** Always test against real PostgreSQL via Testcontainers (reuse containers for speed).
- Use test data builders/object mothers; no shared mutable fixtures.
- Security tests for each endpoint: unauthenticated → 401, wrong role → 403, other tenant → 404.
- Mandatory integration scenarios: full happy path (draft → submit → 3-level approval → PO issued), rejection path, change-request loop, cancellation releasing budget, concurrent approvals, idempotent submit, tenant isolation.

---

## 13. DevOps & Docker

### 13.1 Backend image
- **Multi-stage Dockerfile**: build stage (Maven + JDK 21) → runtime stage (`eclipse-temurin:21-jre`, or distroless/alpine variant). Use Spring Boot **layered jars** (`java -Djarmode=layertools`) for better caching.
- Run as a **non-root user**; read-only root filesystem where possible.
- JVM flags: `-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError`; set explicit container memory/CPU limits in Compose.
- `HEALTHCHECK` hitting the readiness endpoint.
- Graceful shutdown: `server.shutdown=graceful`, `spring.lifecycle.timeout-per-shutdown-phase=30s`.
- Image tags: git SHA + semantic version; never deploy `latest`.

### 13.2 Frontend image
- Multi-stage: Node build → **nginx** serving static files with SPA fallback (`try_files ... /index.html`), gzip/brotli, long-cache for hashed assets, no-cache for `index.html`, security headers.
- Runtime config via `/config.js` generated from env at container start (so one image works in all environments).

### 13.3 Docker Compose
Provide:
- `docker-compose.yml` (dev): `postgres`, `redis`, `keycloak` (with realm import JSON), `minio`, `mailpit`, source-mounted `api`, and Vite `web`.
- `docker-compose.prod.yml` override: no dev tools, resource limits, restart policies, internal networks, secrets via env/secret files.
- Named volumes for persistent data; healthchecks and `depends_on: condition: service_healthy`.
- A `Makefile` (or `justfile`) with: `up`, `down`, `logs`, `test`, `build`, `migrate`, `openapi`, `lint`.

### 13.4 Configuration
- `application.yml` with profiles `dev`, `test`, `prod`; prod values come from environment variables only.
- Connection pool tuned (HikariCP) and sized deliberately; connection timeout and max lifetime set.

### 13.5 CI/CD (GitHub Actions or GitLab CI)
Pipeline stages: **lint/format → compile → unit tests → integration tests (Testcontainers) → static analysis (SpotBugs/Checkstyle/Sonar) → dependency & image vulnerability scan → build and push image → deploy**. Fail the build on test failure, vulnerability above threshold, or OpenAPI breaking change.

---

## 14. Frontend Requirements (React + TypeScript)

- **Stack:** Vite, React 18+, TypeScript (`strict: true`), React Router, **TanStack Query**, React Hook Form + Zod, a component library (MUI, Mantine or shadcn/ui), i18n-ready (English first; Swahili-ready).
- **API client:** generated from the backend OpenAPI spec (`openapi-typescript` + `openapi-fetch`, or `orval`). No hand-written duplicate types. Add an npm script `api:generate`.
- **Auth:** `oidc-client-ts` / `react-oidc-context` with Authorization Code + PKCE against Keycloak; access token in memory; silent refresh; route guards by role.
- **Data fetching:** TanStack Query with sensible `staleTime`, query-key factories per feature, optimistic updates only where safe, mutation error handling mapped from `ProblemDetail`.
- **Idempotency & concurrency:** auto-generate `Idempotency-Key` (UUID) per submit-like mutation attempt; send `If-Match` with ETag on edits; on `409/412` show a "data changed, reload" flow.
- **Structure:** feature-based folders (`features/requests`, `features/approvals`, ...), shared `components/`, `lib/`, `routes/`.
- **Screens (v1):** Dashboard (my requests, pending my approval), Request list (filters, pagination, sort), Request create/edit (dynamic line items, attachments), Request detail (timeline of approvals + audit), Approval inbox (approve/reject/request-changes with comment), Vendors, Budgets, POs, Reports (charts + CSV export), Admin (departments, policies), Audit log viewer.
- **UX quality:** loading skeletons, empty states, error boundaries, form validation mirroring server rules, accessible (WCAG AA: keyboard nav, labels, contrast), responsive.
- **Quality gates:** ESLint, Prettier, `tsc --noEmit`, Vitest, Playwright smoke tests in CI.

---

## 15. Delivery Phases (follow in order; stop after each for review)

**Phase 0 – Foundation**
Repo layout (`backend/`, `frontend/`, `ops/`, `docs/`), Maven project, package structure, Docker Compose (Postgres first), `application.yml` profiles, Flyway baseline, global exception handler (ProblemDetail), base entity/audit fields, OpenAPI setup, ArchUnit tests, CI skeleton, `docs/DECISIONS.md`.

**Phase 1 – Reference data & CRUD**
Organization/department/user-profile, vendors, catalog. DTOs + MapStruct, validation, pagination, filtering (Specifications), soft delete, unit + slice + Testcontainers tests.

**Phase 2 – Security & tenancy**
Keycloak in Compose with realm import, JWT resource server, role mapping, method security, CORS, tenant context + Hibernate filter + RLS, security and tenant-isolation tests.

**Phase 3 – Purchase requests & budgets**
Request aggregate, line items, totals, attachments (MinIO), budget reservation with concurrency safety, optimistic locking, ETag/If-Match, idempotency keys, search/filter, N+1 tests.

**Phase 4 – Approval workflow**
Policies, chain resolver (Strategy), approval instance/steps, state machine, delegation, escalation job (ShedLock), audit events + Envers, domain events.

**Phase 5 – Purchase orders & notifications**
PO creation and numbering (sequences), PDF generation, outbox pattern, notification module (in-app + email via Mailpit), idempotent handlers.

**Phase 6 – Reporting & caching**
Projection-based reports, Redis caching with eviction, streamed CSV export, keyset pagination for audit log.

**Phase 7 – Frontend**
OpenAPI client generation, auth, all screens listed in section 14, Vitest + Playwright tests, nginx image.

**Phase 8 – Observability & production hardening**
Actuator separation, OTLP tracing hooks, structured logging, rate limiting, prod Compose, image hardening, vulnerability scans, performance test (k6 or Gatling) with documented results, runbook in `docs/RUNBOOK.md`.

**Phase 9 (stretch)** – Goods receipt, invoice three-way match, webhooks, Kafka replacing the outbox dispatcher.

---

## 16. Definition of Done (applies to every task)

- [ ] Code compiles with zero warnings treated as errors where configured; formatting/lint pass.
- [ ] Unit and integration tests written and passing; new code meets coverage targets.
- [ ] No N+1 queries introduced; query counts asserted for list endpoints.
- [ ] Authorization and tenant isolation tested for every new endpoint.
- [ ] Flyway migration included for every schema change; app boots with `ddl-auto=validate`.
- [ ] OpenAPI documentation updated; error responses documented.
- [ ] No secrets, tokens, or PII in code or logs.
- [ ] Audit events recorded for state-changing actions.
- [ ] README/docs updated; decision recorded in `docs/DECISIONS.md` if a trade-off was made.
- [ ] Works via `docker compose up` from a clean checkout.

---

## 17. Coding Conventions

- Java: Google Java Format (or Spotless with the same rules); Checkstyle + SpotBugs in CI; meaningful names; small methods; no commented-out code.
- Prefer immutability: `final` fields, records, unmodifiable collections.
- No `null` returns from services for "not found": throw domain exceptions (`ResourceNotFoundException`) or return `Optional` internally.
- Exceptions: domain exceptions extend a common `DomainException` with an error code; mapped centrally to ProblemDetail.
- Logging: SLF4J with parameterized messages; correct levels (`INFO` for business events, `WARN` for recoverable anomalies, `ERROR` for failures needing attention).
- Commit messages: Conventional Commits. Small, focused PRs.
- TypeScript: strict mode, no `any`, no unused exports, functional components + hooks, colocated tests.

---

## 18. Deliverables Checklist

- `backend/` Spring Boot application (modular monolith)
- `frontend/` React + TS application
- `ops/` Compose extras, Dockerfiles, Keycloak realm export, nginx config
- `docs/` `ARCHITECTURE.md` (with diagrams), `DECISIONS.md`, `API.md` (or link to OpenAPI), `RUNBOOK.md`, `SECURITY.md`
- `README.md` with quick start (`make up`), default dev credentials, and how to run tests
- `.env.example`, `Makefile`, CI pipeline config
- Seed/demo data for dev: one organization, three departments, users for each role, sample vendors and budgets