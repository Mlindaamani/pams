# Architecture Decisions

Short ADRs capturing assumptions and trade-offs made while implementing the phases in
`docs/REQUIREMENTS.md`.

---

## ADR-001 — Pin Spring Boot 3.5.16 on Java 21

**Context.** The requirements mandate Java 21 and Spring Boot 3.x. Newer major versions (Spring Boot
4.x) are available.

**Decision.** Pin Spring Boot `3.5.16` (latest stable 3.5 line) and compile with `--release 21`,
even though the local JDK may be newer.

**Consequences.** Matches the requirements and keeps a stable, well-documented baseline. Upgrading to
Boot 4 becomes a deliberate, separate change.

---

## ADR-002 — `organization` is the tenant root and has no `organization_id`

**Context.** Every tenant-owned table must carry `organization_id`, but the organization row itself
has no parent tenant.

**Decision.** `organization` extends `AuditableEntity` (identity + audit + version only). All other
tenant-owned aggregates extend `TenantEntity`, which adds an immutable `organization_id`.

**Consequences.** Avoids a self-referential/NOT NULL placeholder column. Tenant scoping, Hibernate
filters and RLS policies apply to every table except `organization` and global reference data.

---

## ADR-003 — Audit attribution is "system" until Phase 2

**Context.** Audit columns (`created_by`, `updated_by`) are mandatory from the start, but
authentication and the Keycloak integration are delivered in Phase 2.

**Decision.** Provide an `AuditorAware<String>` bean that returns `system`. Phase 2 replaces it with
the authenticated subject; the entity model and migrations do not change.

**Consequences.** No anonymous/null audit rows. Tests can assert deterministic `system` attribution.

---

## ADR-004 — UUID v4 identifiers for now

**Context.** UUIDv7 gives better index locality but requires an extra library or custom generator.

**Decision.** Use Hibernate's standard UUID generation (`@GeneratedValue` on `UUID`), with
`gen_random_uuid()` as the column default. Revisit UUIDv7 only if insert-heavy benchmarks show a
problem.

**Consequences.** Simpler dependency graph; no behavioural difference for API consumers.

---

## ADR-005 — Repository ports in `domain`, Spring Data adapters in `infrastructure`

**Context.** The architecture places repository interfaces in the domain layer and JPA adapters in
infrastructure, while Spring Data repositories are infrastructure concerns.

**Decision.** Each module defines a plain Java repository port in `domain`; `infrastructure`
contains a Spring Data interface plus a thin `@Repository` adapter implementing the port. ArchUnit
forbids application/API code from depending on `infrastructure`.

**Consequences.** A little boilerplate per aggregate, but module boundaries and testability are
preserved.

---

## ADR-006 — Error responses use `urn:pams:error:<code>` types

**Context.** RFC 7807 requires a `type`; the project also needs a stable machine-readable `code`.

**Decision.** `type` is `urn:pams:error:<code>` and `code` repeats the stable value. `traceId`
currently equals the request id (`X-Request-ID`) until distributed tracing arrives in Phase 8;
`timestamp` is rendered from the injected `Clock`.

**Consequences.** Clients can switch on either `type` or `code`. Trace correlation works from day
one without committing to a tracing backend.

---

## ADR-007 — Integration tests run in the default `test` phase

**Context.** Testcontainers tests need Docker and are slower; separate failsafe/`verify` runs add
ceremony.

**Decision.** Persistence/context tests are named `*Test` and run under Surefire with Testcontainers.
Revisit (move to failsafe) if the suite becomes slow or CI lanes need splitting.

**Consequences.** `make test` and `./mvnw verify` both exercise real PostgreSQL. No H2 anywhere.

---

## ADR-008 — CSRF stays disabled (stateless JWT API)

**Context.** Spring Security defaults assume cookie-based sessions.

**Decision.** When security is added in Phase 2, configure a stateless OAuth2 Resource Server with
CSRF disabled, documenting that the API is called with bearer tokens held in memory by the SPA.

**Consequences.** No CSRF token plumbing in the SPA; cookie-based auth is explicitly out of scope.

---

## ADR-009 — Temporary tenant resolution via `X-Organization-Id` (Phase 1)

**Context.** Phase 1 delivers tenant-owned CRUD before Keycloak/JWT integration (Phase 2).

**Decision.** `TenantContextFilter` populates a request-scoped `TenantContext` from the
`X-Organization-Id` header. Services call `TenantContext.require()`, which fails with `400
invalid-request` when absent. Phase 2 replaces the header source with the JWT `organization_id`
claim; the rest of the code (services, repositories) stays unchanged.

**Consequences.** Endpoints are usable and tenant-isolation tests are meaningful now, but the header
is spoofable. The API must not be exposed outside development until Phase 2 lands.

---

## ADR-010 — Soft delete with `deleted_at` and `@SQLRestriction`

**Context.** Departments, user profiles, vendors and catalog items must disappear from normal reads
without hard deletes.

**Decision.** `SoftDeletableEntity` stores `deleted_at`/`deleted_by`; each entity is annotated with
`@SQLRestriction("deleted_at IS NULL")`. Uniqueness is enforced by partial unique indexes that only
cover live rows (for example `uq_vendor_org_tax_id ... WHERE deleted_at IS NULL`). A second
`softDelete` call is a no-op.

**Consequences.** Deleted rows remain for audit/reporting but are invisible to JPA queries and can be
recreated under the same natural key. Phase 6 may add a privileged audit view that bypasses the
restriction.

---

## ADR-011 — Money is `NUMERIC(19,4)` and serialized as strings

**Context.** Binary floating point must not be used for money, and clients should not lose precision
in JSON.

**Decision.** Amounts are `BigDecimal` columns (`NUMERIC(19,4)`). Requests accept `{ "amount":
"1234.5000", "currency": "TZS" }` with the amount as a string (`MoneyRequest`); responses always
normalize to four decimal places (`MoneyDto`). Currency is a 3-letter uppercase ISO-4217 code.
User approval limits are nullable; absence means "no limit".

**Consequences.** Clients get stable formatting and no float artifacts. Currency conversion is out
of scope (v1 single-currency per amount).

---

## ADR-012 — ETag/`If-Match` and `Idempotency-Key` are deferred to Phase 3

**Context.** API standards require optimistic concurrency and idempotency for mutations, while the
Phase 3 scope explicitly introduces ETag/`If-Match` and idempotency keys for purchase requests.

**Decision.** Phase 1 mutations rely on `@Version` optimistic locking (409 on conflict) without
exposing ETag/`If-Match` or requiring `Idempotency-Key`. Phase 3 adds these headers consistently
across mutable resources.

**Consequences.** Clients must handle 409/412 only after Phase 3. The entity model already exposes
`version`, so no migration is needed.

---

## ADR-013 — Organization endpoints are unscoped until Phase 2

**Context.** Creating the first organization cannot require a tenant context, and role-based
authorization (`ADMIN`-only) is not available before Phase 2.

**Decision.** `/api/v1/organizations` allows create/list/get/rename without a tenant header. All
other Phase 1 endpoints are tenant-scoped. Phase 2 restricts organization management to `ADMIN` and
narrows visibility to the caller's tenant.

**Consequences.** Development deployments expose the organization registry; acceptable only because
Phase 2 immediately precedes any non-local use. Organization deletion is intentionally not exposed.

---

## ADR-014 — Tenant comes from the JWT `organization_id` claim

**Context.** Phase 1 used a spoofable `X-Organization-Id` header as a temporary tenant source.

**Decision.** Keycloak issues `organization_id` from a user attribute via a protocol mapper. A
`TenantContextFilter` runs inside the security chain (after bearer authentication) and stores the
claim in the request-scoped `TenantContext`; the header is no longer accepted.

**Consequences.** Phase 1's header-based requests stop working. Users must exist in Keycloak with the
`organization_id` attribute. A missing claim produces `400 invalid-request` on tenant-owned
endpoints; unauthenticated requests get `401`.

---

## ADR-015 — Two-layer tenant isolation: Hibernate filter + PostgreSQL RLS

**Context.** Requirements demand defense in depth for multi-tenancy, and tests run against a
PostgreSQL superuser (which bypasses RLS).

**Decision.** Tenant entities carry a Hibernate `@Filter` enabled by `TenantFilterAspect` inside the
service transaction. PostgreSQL RLS is enabled with `FORCE ROW LEVEL SECURITY` and the policy compares
`organization_id` to `NULLIF(current_setting('app.current_org', true), '')::uuid`. A datasource
wrapper sets `app.current_org` from `TenantContext` on every connection checkout, so pooled
connections cannot leak tenants. The `pams_app` non-login role exists for RLS verification tests.

**Consequences.** Application-level isolation is enforced even when RLS is bypassed (tests,
superuser); database-level isolation protects against missing predicates. The datasource wrapper adds
one `set_config` round trip per connection checkout.

---

## ADR-016 — Role matrix for Phase 1/2 endpoints

**Context.** Roles exist in the realm, but fine-grained authorization belongs in the service layer
for ownership rules (Phase 3+).

**Decision.** Use `@PreAuthorize` per controller method:

| Resource | Mutations | Reads |
|---|---|---|
| Organizations | `ADMIN` | `ADMIN` (scoped to caller tenant) |
| Departments | `ADMIN` | any authenticated |
| User profiles | `ADMIN` | `ADMIN`, `AUDITOR` |
| Vendors | `ADMIN`, `PROCUREMENT_OFFICER` | any authenticated |
| Catalog items | `ADMIN`, `PROCUREMENT_OFFICER` | any authenticated |

Organization listing/get is additionally restricted to the caller's tenant id. Ownership checks
(e.g. requesters editing their own drafts) are added with the request aggregate in Phase 3.

**Consequences.** A requester cannot mutate reference data. Tenant isolation tests use tokens from
two organizations; role tests assert 401/403 problem responses.

---

## ADR-017 — JIT user provisioning deferred

**Context.** FR-1.2 describes creating a local `user_profile` on first login, but a profile requires
a department and the JWT carries no department claim.

**Decision.** Phase 2 keeps user profiles admin-managed via `/api/v1/users`. Automatic provisioning
will land together with approval policies (Phase 4), when a configurable default department/role
mapping exists. Existing Keycloak demo users can be created through Bruno.

**Consequences.** A token for a user without a local profile can authenticate but cannot be assigned
approval work; this is acceptable until the workflow phase. Documented so the gap is intentional.

**Note (development).** The Docker PostgreSQL image creates `POSTGRES_USER` as a superuser, and
superusers bypass row level security even with `FORCE`. The Hibernate filter therefore provides the
application-level guarantee in local development. RLS enforcement is verified in tests through the
non-login `pams_app` role; production must connect with a non-superuser role that is subject to the
policies.

---

## ADR-018 — Budget reservation model and fiscal year

**Context.** Budgets are defined per department, fiscal year and optionally category; requests have a
department and currency but no category.

**Decision.** Request submission reserves against the department-level budget (`category IS NULL`) for
the **calendar year of submission** (UTC `Clock`). Reservation uses a single conditional
`UPDATE ... WHERE reserved + committed + actual + amount <= allocated`, which is concurrency-safe
without pessimistic locks; a zero-row update maps to `422 budget-exceeded`. Cancellation releases the
reservation with a guarded decrement. Requests flagged `budgetExceptionRequested` continue without a
reservation when the budget is insufficient and are marked `budgetException=true` for FINANCE to
handle in the approval workflow. `BudgetService.reserveForDepartment` uses
`@Transactional(noRollbackFor = BudgetExceededException.class)` so a caller can catch the business
exception and continue in the same transaction.

**Consequences.** Category-level budgets exist and can be managed, but automatic request reservation
is department-level until line-item categories are wired into the workflow. The conditional update
serialises competing submissions on the budget row.

---

## ADR-019 — ETag, If-Match and Idempotency-Key in Phase 3

**Context.** Purchase requests are mutable aggregates with money side effects; duplicate submits must
not double-reserve budget.

**Decision.** Detail/create/update/submit/cancel responses expose `ETag: "<version>"`. `PUT`/`DELETE`
and the `submit`/`cancel` actions require `If-Match` (`428` when missing, `412` on mismatch).
`Idempotency-Key` is required on create/submit/cancel and enforced by a filter registered inside the
security chain after tenant resolution. It hashes method + path + body, replays the stored response
(including `Location`/`ETag`) for 24 hours, and returns `422 idempotency-key-reuse` when the same key
is used with a different payload. Only 2xx responses are stored.

**Consequences.** Clients must send both headers on the listed mutations. Attachments are subresource
operations and do not require `If-Match`; they do not bump the request version.

---

## ADR-020 — Attachments are streamed through the API (no pre-signed URLs in v1)

**Context.** MinIO can issue pre-signed URLs, but they bypass API authorization checks.

**Decision.** `FileStoragePort` has MinIO and in-memory adapters. Uploads are limited to 10 MB and 10
per request, content type is sniffed with Apache Tika against PDF/PNG/JPEG/DOCX/XLSX, checksums are
SHA-256, and storage keys are server-generated UUIDs. Downloads stream through the API with the same
tenant/ownership checks as the request. The MinIO client image is `bitnamilegacy/minio` because the
official Docker Hub repository is no longer publicly pullable.

**Consequences.** No public object URLs and no extra signing configuration; large downloads consume
API bandwidth. Virus scanning remains an interface hook for a later phase.

---

## ADR-021 — Amount-tier policies resolved by a Strategy bean

**Context.** Approval chains are configurable per organization and selected by request amount. Policy
steps may target a role, a specific user, or the requester's manager.

**Decision.** `ApprovalPolicy` rows are versioned: updating a policy deactivates the current row and
creates a new row with `policy_version + 1`, preserving the steps used by in-flight requests. The
`ApprovalChainResolver` strategy (`AmountTierApprovalChainResolver`) selects the single active band
`[amount_min, amount_max)` and resolves `ROLE`, `USER` and `REQUESTER_MANAGER` steps. Budget-exception
submissions prepend a `FINANCE` step. Requests snapshot `approval_policy_id` and
`approval_policy_version`; the approval instance duplicates the snapshot.

**Consequences.** Overlapping active bands resolve to the highest `amount_min`; operators should keep
bands contiguous and non-overlapping. A missing policy or manager fails submission with `422` and
rolls back the budget reservation.

---

## ADR-022 — Request/approval orchestration uses synchronous domain events

**Context.** The approval module must advance the purchase request status, but a direct dependency on
the request application service would create a module cycle (requests already call approvals on
submit).

**Decision.** `ApprovalService` publishes Spring events (`ApprovalEvents.*`) and
`RequestApprovalOutcomeListener` in the request module applies them with `@EventListener` inside the
same transaction. Approval actions carry `@Version` on the instance and step, so concurrent approvals
fail with `409`.

**Consequences.** Status changes and budget release happen atomically with the approval action.
Listeners throw on invalid transitions, rolling back the whole transaction. Phase 5 replaces these
synchronous notifications with outbox-driven notifications.

---

## ADR-023 — Delegation is time-boxed; escalation reminds then skips-and-advances

**Context.** Approvers need out-of-office cover, and overdue steps must not block a request forever.

**Decision.** `Delegation` rows link a delegator user profile to a delegate for `[starts_at,
ends_at)`. An assigned user step is actionable by the assignee or by a delegate with an active
delegation; the step itself stays `PENDING` (the `DELEGATED` enum value is reserved for a future
step-level hand-over). Escalation runs a ShedLock-protected scheduled job per organization: the first
run sets `reminder_sent_at` and publishes a reminder event; the next run marks the step `SKIPPED`
("Escalated"), records `escalated_at`, and advances to the next step. Advancing past the last step
completes the instance.

**Consequences.** Delegation is opt-in per user, not per step. Escalation cannot resurrect rejected
or cancelled instances. Notification delivery is a Phase 5 concern; only events and audit records are
emitted now.

---

## ADR-024 — Explicit business audit events now; Hibernate Envers deferred

**Context.** Phase 4 asks for entity history (Envers) plus business audit events. Envers requires
`*_aud` tables, and with `ddl-auto=validate` those must be maintained by Flyway for every audited
entity, significantly expanding the migration surface.

**Decision.** Implement the append-only `audit_event` table now (submit, approve, reject, changes,
cancel, reminders, escalation, delegation) and publish domain events. Defer Envers to the
production-hardening phase together with the audit viewer/keyset pagination, when audited entities and
their schema can be frozen.

**Consequences.** Business actions are fully auditable today; column-level history is not yet
available. This is recorded as technical debt for Phase 6/8.

---

## ADR-025 — Purchase orders are numbered with an atomic per-organization/year counter

**Context.** `PO-YYYY-NNNNNN` numbers must be unique per organization and must not use `MAX()+1`.

**Decision.** `purchase_order_sequence` holds `(organization_id, fiscal_year, next_value)`. A single
`INSERT ... ON CONFLICT DO UPDATE ... RETURNING next_value` reserves the next number atomically.
Creating a PO marks the source request `CONVERTED_TO_PO` and copies its lines and budget reference;
a unique index enforces one PO per request. Cancelling an issued PO reverses the commitment.

**Consequences.** Numbering is gap-free per year (rollbacks can create gaps), and concurrent PO
creation is safe without application locks.

---

## ADR-026 — Transactional outbox with a ShedLock poller

**Context.** PO issuance must send email and create notifications without losing events or blocking
the request transaction.

**Decision.** `outbox_event` is written in the same transaction as the PO issue. The poller locks a
batch with `SELECT ... FOR UPDATE SKIP LOCKED`, sets the tenant context per event (including a
`set_config('app.current_org')` on the transaction connection, because notification inserts are
RLS-protected), dispatches to every matching handler, and marks the event processed. Failures
increment `attempts`, use exponential backoff, and dead-letter after 5 attempts. Handlers are
idempotent (notifications use a unique `(organization_id, event_id, recipient_subject)` index).

`outbox_event` deliberately has no RLS because the poller must process all tenants; it is a system
table like `shedlock`. Email is sent inside the poller transaction for v1 — a Phase 8 hardening item
is to split delivery into a second asynchronous hop.

**Consequences.** At-least-once delivery with idempotent consumers; PO issue never fails because
Mailpit is unavailable (the outbox retries).

---

## ADR-027 — Notifications are in-app rows plus SMTP email via Mailpit

**Context.** Users need an unread badge and vendors need the PO email.

**Decision.** `notification` is tenant/RLS-scoped and addressed by Keycloak subject. The
`PurchaseOrderIssued` outbox event has two handlers: one creates the requester's in-app notification,
one sends the vendor email through `MailPort`. `SmtpMailAdapter` is active when `pams.mail.enabled=true`
(Mailpit on 1025 in dev, UI on 8025); tests use `InMemoryMailAdapter`. Notification APIs expose list,
unread count, and mark-read for the current subject only.

**Consequences.** No notification is delivered to another tenant or user. Email failures retry via
the outbox; in-app notifications remain consistent because they share the outbox transaction.

---

## ADR-028 — PO PDFs are generated on demand

**Context.** Phase 5 requires a downloadable PO PDF; storing every generated PDF adds lifecycle work.

**Decision.** OpenPDF renders the PO from its persisted lines and vendor data on each request and
streams it with `Content-Disposition: attachment`. No PDF object is stored.

**Consequences.** Rendering cost is paid per download; the document always reflects current PO data.
A future phase can cache rendered PDFs in MinIO if needed.

---

## ADR-029 — Reports use read-only SQL projections cached in Redis with tenant-scoped keys

**Context.** Spend, cycle-time and commitment reports are read-heavy and must respect tenancy.

**Decision.** Report queries are hand-written `JdbcTemplate` projections (no entity loading) against
the existing tables, filtered by `organization_id`. Cache keys are built as
`organizationId|report|filters` so tenants can never share an entry. `RedisCacheManager` supplies a
TTL (`pams.reporting.cache-ttl`, default 5 minutes) for the report caches. Writes that change reporting
data (budget, PO creation/issue/cancel) publish `ReportingDataChangedEvent`; the reporting module
clears all report caches for that event.

**Consequences.** Cache entries are coarse-grained (all tenants are evicted on any reporting write)
but safe. Tests use Spring's simple concurrent cache so Redis is not required in CI.

---

## ADR-030 — Spend CSV is streamed and never cached

**Context.** CSV exports can be large and must not be fully materialised in memory.

**Decision.** `/api/v1/reports/spend.csv` writes rows directly to the servlet response output stream
from a JDBC `RowCallbackHandler`; values are CSV-escaped. The export bypasses the cache so it always
reflects current data.

**Consequences.** Memory use is proportional to one row; the HTTP response is not resumable. PDF
exports remain optional/out of scope for reports.

---

## ADR-031 — Audit log uses opaque keyset cursors

**Context.** The audit trail grows continuously and offset pagination degrades.

**Decision.** `GET /api/v1/audit-events` orders by `(occurred_at DESC, id DESC)` and accepts an opaque
base64 cursor encoding the last seen `(epochMillis, id)`. It fetches `limit + 1` rows to decide whether
to return `nextCursor`; limit defaults to 25 and is capped at 100. Only ADMIN and AUDITOR can read the
log, and queries are tenant-scoped (RLS + explicit organization filter).

**Consequences.** Stable pagination while new events arrive. Cursors are opaque but not signed; they
only encode a position, and every query remains tenant-scoped.

---

## ADR-032 — Frontend stack, generated API client and CASL authorization

**Context.** Phase 7 needs a typed SPA whose UI permissions mirror the backend role model without
hand-maintained API types.

**Decision.** Vite + React 18 + TypeScript (strict), MUI, TanStack Query, React Hook Form + Zod,
React Router, and OIDC Authorization Code + PKCE via `react-oidc-context`. The API client is
generated from the committed `openapi.json` with `openapi-typescript`; `openapi-fetch` adds bearer
tokens and `X-Request-ID`, while mutations add `Idempotency-Key`/`If-Match` as required. Authorization
uses CASL (`@casl/ability` + `@casl/react`): a role-to-ability map defines actions on subjects,
`<Can>` gates controls, and `RequireAbility` guards routes. Ownership and tenant checks remain
server-side; CASL is a UX layer, not a security boundary.

**Consequences.** UI affordances match backend roles, and regenerating the client after API changes is
one command (`npm run api:generate`). CASL rules must be kept in sync when backend policies change.

---

## ADR-033 — Frontend runtime configuration and container hardening

**Context.** One built image should serve dev/staging/prod, and tokens must not be persisted to
localStorage.

**Decision.** `docker-entrypoint.sh` writes `/config.js` from `PAMS_API_BASE_URL`,
`PAMS_OIDC_AUTHORITY`, `PAMS_OIDC_CLIENT_ID` at container start. Nginx serves the SPA with fallback,
immutable hashed assets, no-store for `index.html`/`config.js`, security headers and gzip. Access
tokens stay in memory (no storage), so a page reload requires re-authentication. The `web` Compose
service builds the multi-stage image and waits for Keycloak health.

**Consequences.** The same image works across environments without rebuilding. Reloads sign users in
again; silent refresh works while the tab remains open. CSP permits API/Keycloak connections over
http(s) for local development.

---

## ADR-034 — Milestone 1: admin completeness, bulk actions and draft PO editing

**Context.** The v1 screens were present, but several reference-data pages, CRUD operations and
productivity features were missing.

**Decision.**
- Added Users, Catalog, Organization, Delegations, Profile, System, 403 and 404 pages.
- Completed vendor edit, budget edit/delete, policy version editing, draft PO terms editing,
  attachments during request creation, notification read-all, audit date/aggregate filters and
  report date filters/charts.
- Bulk deletes (vendors, catalog items, departments) and bulk approvals are **all-or-nothing** within
  one transaction. This preserves audit/approval invariants; a future iteration can add per-row
  partial results if the UX requires it.
- Draft PO editing is limited to payment terms and delivery date for now. Line-price editing is
  deferred until the budget-reservation delta rules are designed in Phase 9.
- Bulk-approve endpoint requires `Idempotency-Key` like single approval actions.

**Consequences.** Reference data can be administered from the UI, selection-based operations are
available, and PO documents can be corrected before issue without repricing risk.

---

## ADR-035 — Milestone 2: dark mode, collapsible navigation and modern shell

**Context.** The UI needed a modern shell, persistent theme choice, grouped navigation and safer
destructive workflows.

**Decision.**
- Light/dark MUI theme with a persisted preference (`pams.theme`, falls back to
  `prefers-color-scheme`). The app bar is a solid PAMS blue (no gradient); the reverse logo is used
  on it and the primary logo on light surfaces.
- Sidebar groups: Procurement, Finance, Administration, Insights, with smooth MUI `Collapse`
  animations (disabled for `prefers-reduced-motion`), persisted expansion state and auto-expand for
  the active group. Closed groups use a right chevron; open groups use a down chevron.
- Breadcrumbs, `Ctrl/Cmd+K` command palette, notification badge and theme toggle in the shell.
- A reusable MUI `ConfirmDialog` gates destructive actions (deletes, cancels, blacklisting, bulk
  operations) instead of browser `confirm()`.
- Shared `DataTable` gained selection toolbars and sortable columns (`sortValue` per column).

**Consequences.** The shell is keyboard-accessible, responsive, theme-aware and consistent. New
destructive actions must use `ConfirmDialog`; new tables can opt into sorting with `sortValue`.

---

## ADR-036 — Milestone 3: observability, rate limiting and production packaging

**Context.** Phase 8 requires operational visibility, abuse protection and a deployable stack.

**Decision.**
- Actuator stays on a separate management port (`9090`) with liveness/readiness probes and metrics.
  `/api/v1/admin/system/status` (ADMIN) exposes tenant-aware approval/outbox counts and build
  information for the System UI page.
- Structured JSON logging is enabled only in `prod`; dev/test keep readable logs. Both include
  `requestId`/`traceId`. Micrometer Tracing exports OTLP to Tempo (100% dev, 10% prod by default).
- Write endpoints under `/api/v1` are rate limited per authenticated user (or IP) with Bucket4j;
  exceeding the bucket returns `429` plus `Retry-After`. Disabled in tests.
- The backend image is a multi-stage layered-jar build running as non-root with a readiness
  healthcheck. `docker-compose.prod.yml` provides the production-shaped stack.
- CI runs backend `verify`, frontend lint/tests/build, Trivy filesystem scans and image builds.
- k6 smoke/load script targets list and report endpoints with p95 < 300 ms.

**Consequences.** Operators have health, metrics and trace visibility; write abuse is bounded; images
are reproducible. Production secrets/issuer URLs must be supplied via environment.

---

## ADR-037 — Milestone 4: goods receipts and purchase-order receipt state

**Context.** FR-9.1 requires partial receipts while guaranteeing that cumulative received quantities
never exceed the ordered quantity, including concurrent submissions.

**Decision.**
- `purchase_order_line.received_quantity` is the authoritative cumulative counter. A database check
  constraint enforces `0 <= received_quantity <= quantity` as a second line of defence.
- Receipt recording locks the purchase-order row with `SELECT ... FOR UPDATE` and then applies
  quantities through the `PurchaseOrder` aggregate. This serializes concurrent receipts per order;
  `@Version`/`If-Match` additionally reject stale UI submissions with `412`.
- Receipts live in the `receiving` module (`goods_receipt`, `goods_receipt_line`). It calls the public
  `PurchaseOrderService` application API and never accesses purchase-order repositories or entities.
- A receipt may contain one entry per PO line. Entries snapshot description/unit. Over-receipt is a
  business-rule violation (`422`); receiving against a draft/cancelled/closed/RECEIVED order is a
  state conflict (`409`).
- PO status becomes `PARTIALLY_RECEIVED` until every line is fully received, then `RECEIVED`.
- Receipt create requires `Idempotency-Key`; the response ETag is the updated PO version so clients
  can refresh their optimistic-lock token.

**Consequences.** Receipts are durable and auditable. Invoice matching in Milestone 5 can compare
received quantities from these tables against PO lines. Cancelling/closing orders after receipts
needs a future business decision; currently cancellation is blocked once `RECEIVED`.

---

## ADR-038 — Milestone 5: vendor invoices and three-way matching

**Context.** FR-9.2 requires invoice capture and three-way matching against request/PO, receipts and
invoice lines, with a configurable tolerance and Finance review of mismatches.

**Decision.**
- `organization.invoice_tolerance_percent` (default `2.00`, range 0–100) is editable in Organization
  settings. Each invoice snapshots the tolerance used at capture time.
- Invoice creation locks the PO row before reading cumulative invoiced quantities. This serializes
  concurrent invoice capture per PO; existing invoice lines (including mismatched ones) count toward
  cumulative invoiced quantity.
- Matching rules per PO line:
  - invoice quantity + previously invoiced quantity must not exceed received quantity;
  - absolute unit-price variance from the PO price must not exceed the organization tolerance.
  Currency must equal the PO currency (hard validation, `422`).
- Mismatches are stored, not rejected: invoice status `MISMATCHED` with per-line reasons. Fully
  matching invoices are `MATCHED`. Finance/Admin can approve a mismatch (`APPROVED`) with `If-Match`
  and an audit event; approval records the acting subject and timestamp.
- Invoice numbers are unique per organization (case-insensitive). Receipts/invoices are in the
  `receiving` feature and call public purchase-order/organization application APIs rather than
  reaching into their repositories or entities.
- UI adds invoice list/detail, Finance approval, and PO-level invoice capture with PO price defaults.

**Consequences.** Finance has an explicit exception queue and override trail. A separate credit-note
or void workflow is deferred; mismatched invoices remain counted until that is designed.

---

## ADR-039 — Milestone 6: signed webhooks

**Context.** Phase 9 requires outbound webhooks without losing events or blocking business
transactions.

**Decision.**
- Admins configure tenant webhook subscriptions with one or more supported events:
  `PurchaseOrderIssued`, `GoodsReceiptRecorded`, `VendorInvoiceRecorded`.
- Events are bridged through the existing transactional outbox. `WebhookOutboxHandler` creates one
  `webhook_delivery` row per matching active subscription in the same transaction as the source
  event. `(subscription_id, event_id)` is unique, making the bridge idempotent.
- A ShedLock-guarded delivery poller sends due rows outside database transactions. Requests include
  `X-PAMS-Event`, `X-PAMS-Delivery`, `X-PAMS-Timestamp`, `X-PAMS-Attempt` and
  `X-PAMS-Signature: sha256=<HMAC-SHA256(secret, timestamp + "." + body)>`. Consumers must verify the
  signature, enforce a timestamp window and deduplicate by `X-PAMS-Delivery`/body `id`.
- Retries use exponential backoff (30s doubling, capped at 1h); failures become `DEAD_LETTER` after
  `pams.webhooks.max-attempts` (default 8). Admins can inspect deliveries and manually re-queue them.
- Signing secrets are generated with `SecureRandom`, shown only on creation/rotation, never returned
  by list APIs and never logged. URL validation permits only absolute `http`/`https` URLs without
  embedded credentials.
- Like `outbox_event`, webhook tables intentionally do not use RLS because the delivery poller works
  across all tenants. All API queries filter by `organization_id`, and tenant isolation is covered by
  integration tests. Endpoints are ADMIN-only.

**Consequences.** Delivery is at-least-once, retryable and auditable. Consumers must be idempotent.
SSRF remains possible for an ADMIN-configured endpoint; deployments should restrict egress where
required.

---

## ADR-040 — Permission-based authorization and configurable role mappings

**Context.** Role names were hardcoded across controllers and the frontend. Changing who can do what
required code changes, and UI capabilities could drift from API rules.

**Decision.**
- `permission` is the authorization vocabulary; `role_model` describes roles; `role_permission`
  maps Keycloak roles to permission codes in PostgreSQL.
- Backend endpoints use `@PreAuthorize("@permissions.has('...')")`. `PermissionService` resolves the
  current user's roles from the JWT and loads permission codes from the database (30-second cache).
  `ADMIN` is seeded with every permission and cannot be edited from the UI.
- `GET /api/v1/me/permissions` exposes effective permissions. The React CASL ability is built from
  those permission codes, not from role names.
- ADMINs manage mappings in **System → Configuration → Roles & permissions**. Each role has an
  explicit description of its responsibility. Changes evict the permission cache.
- Approval-step eligibility uses `approval:act:<ROLE>` permissions, so changing a role mapping can
  change who may act without editing Java code. Admin retains fallback access to every role step.

**Consequences.** Roles remain the identity-provider grouping, but authorization decisions are
permission-based and configurable. A short cache delay applies after role mapping changes.

---

## ADR-041 — Database-driven workflow actions and status models

**Context.** UI buttons were derived from status checks plus broad permissions, causing actions such
as Edit/Cancel to appear where the server would reject them.

**Decision.**
- `workflow_status` stores displayable status models per entity (currently purchase requests).
- `workflow_action_rule` maps `(entity_type, from_status, action_code)` to a required permission.
- `GET /api/v1/purchase-requests/{id}/actions` calculates the effective action set from database
  rules, status, ownership and approval eligibility. The request detail page renders actions only
  from this server response.
- Owner-only actions (`EDIT`, `DELETE`, `SUBMIT`, `CANCEL`) are suppressed for non-owners. Approval
  actions require an actionable current step. Approved requests therefore expose only `CREATE_PO`
  where permitted.
- ADMINs edit action rules in **System → Configuration → Workflow actions**. Approval chains remain
  edited through database-backed **Approval policies**.
- Core state enums remain in Java as invariants for illegal-transition protection. The database
  controls available actions, permissions and display models; it does not let arbitrary status names
  bypass the state machine.

**Consequences.** UI and API authorization stay aligned. Fully dynamic custom state machines (new
statuses without code changes) remain a future phase; this design prioritizes data integrity while
making who-can-do-what configurable.

---

## ADR-042 — Invoice internal reference, formal PDF and copy controls

**Context.** Vendor invoice numbers are external legal document identifiers, but users also need a
stable PAMS reference and a printable formal document.

**Decision.**
- The vendor invoice number remains required and must be copied from the vendor document.
- PAMS generates a separate internal reference `INV-YYYY-NNNNNN` per organization/year using an
  atomic database sequence. Existing invoices are backfilled deterministically.
- Invoice detail displays both numbers; the internal reference has a copy-to-clipboard control.
- `GET /api/v1/vendor-invoices/{id}/pdf` renders a formal PDF with vendor billing details, PO,
  dates, line items, totals, match status and internal reference.
- The invoice list and PO invoice capture surface the internal reference/label guidance.

**Consequences.** Vendor reconciliation still works with the external number, while internal support
and document retrieval can use a stable PAMS reference.

---

## ADR-043 — Self-service profiles and profile pictures

**Context.** Users without a local `user_profile` could not delegate approvals. Profile pictures also
needed a tenant-safe storage model.

**Decision.**
- `PUT /api/v1/users/me` lets the authenticated user create or update their own display name,
  department and manager. Approval limits remain admin-managed.
- Creating a profile resolves the delegation blocker; no administrator provisioning is required for
  basic participation.
- `POST/GET/DELETE /api/v1/users/me/avatar` stores JPEG/PNG/WebP images (max 5MB) in MinIO under
  `avatars/{organizationId}/{profileId}/...`; old images are removed after replacement.
- `UserProfileResponse.hasAvatar` drives an authenticated blob fetch. The shared `UserAvatar`
  component is used in the app bar and profile page.
- The profile query disables retry-on-mount to avoid a refetch loop when no profile exists yet.

**Consequences.** Users can self-provision enough profile data to delegate and upload a picture.
Department and manager remain validated within the tenant.

---

## ADR-044 — Remove Grafana and Prometheus

**Context.** Grafana and Prometheus added operational overhead without being part of the core
procurement workflow.

**Decision.**
- Remove the Grafana and Prometheus services, configuration, dashboards, ports and volumes from dev
  and production Compose.
- Keep Actuator health/metrics on the management port and Tempo OTLP tracing.
- The System page no longer links to external dashboards; it reports application/outbox/approval
  health directly.
- Metrics remain observable through the Actuator management endpoint for whichever external
  collector an operator chooses.

**Consequences.** Fewer running services and lower resource usage. Operators needing dashboards must
connect their own collector; Tempo trace ingestion is unchanged.

---

## ADR-046 — Platform administration and cross-tenant analytics

**Context.** Tenant-scoped ADMIN users must not see or manage other tenants. At the same time,
platform operators need tenant provisioning, suspension and aggregate adoption metrics.

**Decision.**
- Add `PLATFORM_ADMIN` role and permissions `platform:tenant_read`, `platform:tenant_manage`,
  `platform:analytics_read`.
- Tenant `ADMIN` is granted platform permissions in this bootstrap phase so the existing demo admin
  can operate the platform UI; deployments should move these permissions to the dedicated
  `PLATFORM_ADMIN` Keycloak user.
- `organization.status` supports `ACTIVE` / `SUSPENDED`; suspension metadata is recorded and a
  cached request filter rejects suspended tenants with `403 tenant-suspended`.
- Platform APIs live under `/api/v1/platform` and deliberately ignore tenant scoping. Tenant
  cross-tenant actions are written to `platform_audit_event`, separate from tenant audit logs.
- Tenant lifecycle is create/edit/suspend/activate. Hard deletion is intentionally unsupported.
- Platform analytics provides aggregate-only metrics: tenant/user/request counts, pending approvals,
  invoice mismatches, outbox dead letters, spend by currency, monthly spend/request trends and
  per-tenant performance. No request justifications, line items, bank details or PII are exposed.
- Cross-tenant analytics loops tenants under each tenant's RLS context and aggregates in memory;
  results are cached in Redis and evicted on reporting data changes. A future phase can add daily
  materialized metrics or a dedicated read-only analytics datasource.
- Multi-currency amounts are shown per currency; no FX conversion is invented.

**Consequences.** Platform operators can manage tenants and see adoption/health signals without
opening tenant data. Platform permissions must be separated from tenant ADMIN before exposing more
than one production tenant.

---

## ADR-047 — SaaS plans and Stripe test-mode billing foundation

**Context.** The product is moving to a SaaS model with platform onboarding and recurring
subscriptions.

**Decision.**
- Plans are database records (`subscription_plan`) with USD prices, trial days, seat/department/
  request/storage limits and feature flags.
- Initial plans: Starter ($29/$290), Growth ($99/$990), Enterprise ($299/$2,990), all 14-day trial.
- Each tenant has one `tenant_subscription` row with local status (`TRIALING`, `ACTIVE`, `PAST_DUE`,
  `UNPAID`, `CANCELED`, `INCOMPLETE`), interval, trial/period dates and Stripe references.
- New tenants created through platform administration start on a Growth trial unless another plan is
  selected.
- Stripe test mode uses Checkout Sessions, Billing Portal and signed webhooks. `stripe_event` is an
  idempotency ledger. Stripe price IDs live on plan records and are configured in test mode.
- Subscription tables are not RLS-protected because platform/billing handlers need cross-tenant
  access; all tenant-facing queries use `TenantContext` explicitly.
- Tenant administration gets a Billing & subscription page with plan, status, usage meters, plan
  cards, checkout and portal links.
- Plan limits are stored now; enforcement/gating will be wired into user/department/request/storage
  operations in the next increment.
- Platform tenant creation accepts optional system-admin email/name and stores a provisioning record;
  Keycloak Admin API invitation/provisioning remains a following increment.

**Consequences.** The SaaS billing foundation is deployable in Stripe test mode. Until Stripe keys
and price IDs are configured, checkout is disabled and the UI warns explicitly.

---

## ADR-048 — Tenant admin provisioning and user invitations

**Context.** Platform onboarding and tenant self-service user creation require identity operations
beyond profile rows.

**Decision.**
- Add a `KeycloakAdminPort` adapter using a confidential `pams-admin` service-account client.
- Platform tenant creation accepts optional system-admin email/name and queues a provisioning record.
  A post-commit listener creates the Keycloak user, sets `organization_id`, assigns tenant `ADMIN`,
  and records success/failure.
- Tenant users are invited via `POST /api/v1/users/invitations`. Roles are restricted to tenant roles;
  `PLATFORM_ADMIN` is never assignable by a tenant admin.
- Invitations create a Keycloak user with a temporary password and send credentials through the
  application mail adapter. Keycloak SMTP invitations are deferred.
- If local profile creation fails after Keycloak user creation, the Keycloak user is disabled.
- Keycloak administrative configuration is environment-driven; missing configuration produces a
  business error rather than silently creating unusable local users.

**Consequences.** The runtime requires a configured Keycloak service account for invitations and
onboarding. Subscription limit enforcement and provisioning-status UI remain follow-up work.

---

## ADR-049 — Dedicated platform permissions, admin invites and branded credential emails

**Context.** A dedicated platform account now exists. Tenant administrators still inherited platform
permissions from the initial bootstrap, existing tenants had no way to add a system administrator,
and account credentials were sent as bare inline HTML.

**Decision.**
- Tenant `ADMIN` no longer receives `platform:tenant_read`, `platform:tenant_manage` or
  `platform:analytics_read`. Platform access requires `PLATFORM_ADMIN`.
- Platform tenants can invite a system administrator after creation through
  `POST /api/v1/platform/tenants/{id}/administrator-invite`.
- Tenant-admin and user invitations share `templates/email/account-invitation.html`, matching the
  existing PAMS email branding. Services send both a plain-text fallback and the rendered HTML.
- A platform administrator without a tenant profile gets one lazily provisioned in their associated
  organization so self-service display name and avatar updates work. The profile UI hides tenant-only
  department and manager fields for platform-only accounts.

**Consequences.** Platform and tenant administration are separated by default. Tenant admins can no
longer open platform analytics or tenant management. Platform profiles remain stored in the platform
user's associated tenant organization because profiles require an organization and department.

---

## ADR-050 — Platform revenue analytics and non-Stripe subscription administration

**Context.** Platform administrators inherited tenant `subscription:*` permissions and could open the
tenant billing page, which opened Stripe Checkout/Billing Portal for the platform user's own tenant.
That is not how platform operators should manage customer subscriptions, and operational analytics
did not expose SaaS revenue.

**Decision.**
- Remove tenant `subscription:*` and `webhook:manage` from `PLATFORM_ADMIN`; platform billing is
  controlled only through `platform:tenant_read`, `platform:tenant_manage` and
  `platform:analytics_read`.
- Add a dedicated **Revenue & subscriptions** page for MRR, ARR, ARPU, trial pipeline, at-risk MRR,
  new paying tenants, plan mix, lifecycle mix and 30-day renewals.
- MRR is active-subscription recurring value normalized monthly (annual plans divided by 12).
  Trials and past-due amounts are shown separately rather than inflating MRR.
- Add daily `subscription_mrr_snapshot` history and a ShedLock-protected snapshot job so MRR trend
  builds from real snapshots. No historical MRR is fabricated before snapshots exist.
- Platform subscription actions are audited and do not redirect to Stripe: change plan/interval,
  extend trial, activate/comp, cancel at period end, cancel immediately and reactivate.
- Add a tenant detail page with overview, usage, provisioning state, subscription summary, edit,
  resend invitation, activate and suspend actions.
- Resending an invitation now creates the Keycloak user if missing or resets an existing user's
  temporary password and refreshes tenant attributes/roles.

**Consequences.** Platform operators manage revenue and tenant lifecycle without entering Stripe as a
customer. Tenant administrators retain the tenant-facing Billing page and Stripe Checkout/Portal.
MRR trend is meaningful after two daily snapshots.

---

## ADR-051 — Subscription plan CRUD and tenant actions on detail pages

**Context.** Platform operators needed to manage the SaaS catalogue, not only assign existing plans.
The tenant list also carried too many row actions and became crowded.

**Decision.**
- Add platform subscription-plan CRUD under `/api/v1/platform/subscription-plans`:
  create, list active/inactive plans, update and deactivate. Deactivation is a soft delete: existing
  tenants keep the plan, but it is no longer offered for new tenants or checkout.
- Add a **Subscription plans** page with pricing, trial, limits, features, Stripe price IDs and
  active/inactive status.
- Plan changes are recorded in `platform_audit_event`; tenant subscriptions still use audited
  lifecycle actions (change plan, extend trial, activate, cancel, reactivate).
- Remove the tenant table Actions column. Edit, invite/resend, activate and suspend now live on the
  tenant detail page. The tenant list only navigates to details and supports copying tenant IDs.

**Consequences.** The platform catalogue is manageable without SQL. The tenant list stays compact
and destructive/administrative actions require opening the tenant context first.

---

## ADR-052 — Page-first platform administration and Revenue menu

**Context.** Platform management flows had accumulated dialogs for tenant creation/edit, invites,
subscription lifecycle and plan editing. These forms are too large for comfortable modal use and are
harder to link to directly.

**Decision.**
- Use dedicated pages for platform data-entry and management:
  `/platform/tenants/new`, `/platform/tenants/{id}/edit`, `/platform/tenants/{id}/invite`,
  `/platform/subscriptions/{id}`, `/platform/plans/new` and `/platform/plans/{code}/edit`.
- Keep dialogs only for short destructive confirmations.
- Rename the Platform menu item **Revenue & subscriptions** to **Revenue**; the page retains the
  detailed SaaS metrics and subscription table.
- Tenant list has no Actions column. Tenant actions live on the tenant detail page and its linked
  pages.

**Consequences.** Platform forms are bookmarkable, easier to validate and no longer hidden behind
modal state. Navigation requires one extra route transition for management actions, which is
intentional.

---

## ADR-053 — Combined tenant list and performance tabs

**Context.** Tenants and Tenant performance were separate menu entries showing closely related
cross-tenant data. Slugs also duplicated tenant names in list views.

**Decision.**
- Merge Tenant performance into `/platform/tenants` as a **Tenant performance** tab.
- Keep `/platform/tenants?tab=performance` deep-linkable; `/platform/performance` redirects there.
- Remove the standalone Tenant performance menu entry.
- Remove tenant slugs from the tenant and performance tables; slug remains on the tenant detail page.

**Consequences.** Platform tenant operations and performance review live in one place. Existing
performance bookmarks continue to work through the redirect.

---

## ADR-054 — Configurable subscription feature catalogue

**Context.** Plan features were free-text JSONB codes entered manually in the platform plan form.
There was no catalogue, validation or reusable selector, and the tenant plan cards did not explain
what each plan included.

**Decision.**
- Add `subscription_feature` as the global, platform-managed feature catalogue with name,
  description, category, active state and sort order.
- Add `subscription_plan_feature` as the normalized many-to-many plan mapping. The old
  `subscription_plan.features` JSONB column is dropped after backfill.
- Feature codes are immutable lowercase identifiers. Deactivation is soft and keeps existing plan
  assignments; inactive features cannot be newly assigned.
- Platform feature CRUD lives in the **Subscription plans → Features** tab with page-first
  create/edit forms.
- The plan form replaces free text with grouped, searchable checkbox selection.
- Tenant billing plan cards show included features as green success-circle checklists.
- Features are display entitlements for now. `resolveForAssignment` provides validation; module
  gating can use the same catalogue later without changing the data model.

**Consequences.** The customer-facing plan comparison now reflects the configured catalogue, and
operators cannot create typo-based feature codes. Feature enforcement remains an explicit future
decision.

---

## ADR-055 — Subscription feature enforcement

**Context.** The feature catalogue was display-only. Plans advertised modules that tenants could still
use regardless of entitlement, so plan limits were enforceable but feature packaging was not.

**Decision.**
- Enforce feature entitlements in the backend with `@RequiresFeature`, an AOP guard and
  `SubscriptionAccessService.hasFeature()`.
- Missing entitlements return `403` with stable code `feature-not-enabled` and an upgrade message.
- Expose current entitlements at `GET /api/v1/me/features`.
- Enforce: requests, approvals, vendors, purchase orders, receipts, invoices, webhooks,
  core reports and advanced report CSV export.
- Frontend abilities combine permissions **and** feature entitlements, hiding navigation, route
  access and actions that the current plan does not include.
- `sso` remains a Keycloak/commercial configuration; `audit_export` remains informational until an
  audit export endpoint exists; `priority_support` is commercial only.
- Rename the Platform navigation item **Platform dashboard** to **Dashboard** (route unchanged).

**Consequences.** Starter tenants no longer see or can call invoice, receipt, webhook or report-export
features. Growth gains those except the Enterprise-only catalogue items. Feature changes take effect
for a user after their entitlement cache refreshes or they sign in again.

---

## ADR-056 — Subscription checkout entitlement refresh

**Context.** Stripe Checkout redirects back to the billing page before or while webhook processing
finishes. The frontend cached feature entitlements for five minutes, so a successful upgrade could
show the new plan but still hide newly purchased modules.

**Decision.**
- Checkout success and cancel URLs are handled on the tenant billing page.
- On success, invalidate subscription, plan and entitlement queries immediately and again after
  2, 5 and 10 seconds to cover webhook lag.
- Feature entitlements use a 30-second stale time and refetch on window focus.
- Remove the `checkout` query parameter after showing feedback.
- Expose `useRefreshEntitlements()` for other flows that change plan state.

**Consequences.** Upgrades unlock navigation and actions without requiring sign-out. A webhook that
arrives later than ten seconds still resolves on the next focus/refetch cycle.

---

## ADR-057 — Flat platform navigation

**Context.** Platform administration has only four destinations. A collapsible parent added an extra
interaction and hid all platform items by default.

**Decision.**
- Remove the collapsible **Platform** parent group.
- Render Platform Dashboard, Tenants, Revenue and Subscription plans as flat navigation items.
- Remove the decorative **Platform controls** badge from the Revenue page header.

**Consequences.** Platform users reach all administration pages directly. The tenant-only Dashboard
entry is not visible to `PLATFORM_ADMIN`, so there is no duplicate Dashboard item.

---

## ADR-058 — Revenue URL and tenant subscription tab

**Context.** The Revenue page URL still said `subscriptions`, and its tenant subscription table
duplicated tenant administration. Revenue should be a performance dashboard; subscription records
belong with tenant operations.

**Decision.**
- Move the platform Revenue page to `/platform/revenue`.
- Redirect `/platform/subscriptions` to `/platform/revenue`; redirect legacy detail URLs
  `/platform/subscriptions/{id}` to `/platform/revenue/{id}`.
- Remove the Tenant subscriptions table from Revenue.
- Add a **Subscriptions** tab to `/platform/tenants?tab=subscriptions` containing the tenant
  subscription table and management links.
- Subscription management links and the detail page back-link use `/platform/revenue`.
- Revenue keeps MRR/ARR/ARPU, trial pipeline, at-risk revenue, plan mix, status mix and renewals.

**Consequences.** Revenue is focused on SaaS performance, while tenant subscription records are
managed alongside tenants. Existing subscription URLs continue to resolve through redirects.

---

## ADR-059 — Subscription billing integrity and price snapshots

**Context.** The first implementation always created a Stripe Checkout subscription and platform
subscription actions changed only local rows. Active tenants could therefore accumulate duplicate
Stripe subscriptions, and editing a plan price could change local MRR while Stripe kept charging the
old price.

**Decision.**
- `tenant_subscription` records an explicit `billing_source`: `TRIAL`, `STRIPE` or `MANUAL`.
- Commercial terms are snapshotted per subscription: monthly price, annual price, currency and Stripe
  price ID. MRR and trial pipeline use these snapshots rather than current plan prices.
- Checkout only creates a Stripe subscription when no live Stripe subscription exists.
- Existing Stripe subscriptions are changed with a prorated subscription-item update.
  `CheckoutResponse` now includes `updated=true` when no browser redirect is required.
- Platform plan changes sync to Stripe when the subscription is Stripe-backed.
- Platform activation is rejected for Stripe-backed subscriptions; Stripe remains the source of
  truth for activation.
- Platform cancellation/reactivation updates the Stripe subscription when applicable.
- Stripe trials cannot be extended locally; they must be extended in Stripe.
- Expanded webhook handling for paid/payment-succeeded invoices, payment-action-required, paused,
  resumed and expired checkout sessions.
- Existing duplicate Stripe subscriptions must be reconciled manually; the new checkout path prevents
  creating new ones.

**Consequences.** Local MRR no longer changes merely because a plan price was edited, plan changes do
not create a second subscription, and Stripe-backed lifecycle operations stay aligned. Older
duplicate Stripe subscriptions still require a one-time reconciliation audit.

---

## ADR-060 — Subscription lifecycle notices and Stripe reconciliation

**Context.** Trial expiry blocked writes but left subscriptions in `TRIALING` forever, and admins had
no way to repair a Stripe/local mismatch without SQL.

**Decision.**
- Add an `EXPIRED` subscription state for non-Stripe trials whose trial end has passed.
- Add a daily ShedLock job for trial-ending reminders, trial expiry, past-due and cancellation notices.
- Notifications go through the outbox and a shared branded subscription email template; recipients
  prefer the tenant provisioning admin email and fall back to the first active tenant user.
- Trial reminder timestamps prevent duplicate messages.
- `invoice.paid` / `invoice.payment_succeeded` clear past-due notification markers so a later failure
  can notify again.
- Add a platform **Reconcile with Stripe** action for Stripe-backed subscriptions. It re-reads the
  Stripe subscription and updates local plan, status, period, billing source and price snapshot.
- Expose `billingSource` on subscription views and summaries.

**Consequences.** Trial and dunning communication runs automatically, expired trials no longer appear
as active pipeline, and platform operators can repair individual Stripe drift without direct database
access. Bulk reconciliation and an exact proration preview remain follow-up work.

---

## ADR-061 — Plan-change preview and bulk Stripe reconciliation

**Context.** Tenants could confirm a plan change without seeing the prorated amount, and platform
operators could reconcile only one subscription at a time.

**Decision.**
- Add `POST /api/v1/subscription/change-preview` using Stripe's upcoming-invoice preview for
  Stripe-backed subscriptions. It returns amount due, next payment date, proration lines and the
  effective billing source.
- Trial/manual subscriptions return an immediate-change preview with no payment due.
- Replace direct plan confirmation with a page-first `/admin/billing/change-plan` review flow.
- Add `GET /api/v1/platform/subscriptions/reconciliation`, which checks every Stripe-backed tenant
  against Stripe plan, interval, status, price ID and current period end.
- Add a **Run reconciliation audit** action to Tenants → Subscriptions with inline mismatch details
  and links to the affected subscription.
- HTTP Stripe access supports GET query parameters for preview/reconciliation calls.

**Consequences.** Tenants get an explicit proration review before confirming, and platform operators
can detect drift across all Stripe-backed tenants without SQL. Reconciliation remains read-only; each
tenant can be repaired with the existing individual reconcile action.

---

## ADR-062 — Stripe test-clock regression and PAMS access branding

**Context.** Subscription lifecycle correctness needed a repeatable real-Stripe regression, and the
Keycloak login screens still used the stock theme. Tenant branding was designed as an Enterprise
feature but had no data model or UI.

**Decision.**
- Add an opt-in `StripeTestClockIntegrationTest` that creates a Stripe test clock, customer,
  payment method and subscription, links it to a temporary PAMS tenant, advances the clock through a
  renewal, and verifies reconciliation advances the local period.
- Expose `make stripe-test-clock` using `STRIPE_TEST_CLOCK_E2E=true` and the test-mode secret.
- Add a PAMS Keycloak login theme with PAMS logo, palette, typography and form styling; mount it from
  `ops/keycloak/themes` and configure `loginTheme` in the realm export.
- Add the Enterprise `custom_branding` feature.
- Add `tenant_branding` with display name, logo, primary/secondary colors, email sender name and
  support email. Store logos in MinIO; enforce RLS and Enterprise entitlement.
- Add authenticated `GET/PUT /api/v1/branding`, logo upload/read/remove endpoints and an
  Administration → Branding page.
- Add a frontend BrandingProvider that applies tenant colors, workspace name, document title and logo
  after login. Keycloak login remains PAMS-branded as agreed.
- Email sender name/support email are stored now; email-template use is a follow-up increment.

**Consequences.** Stripe lifecycle changes can be regression-tested against real test clocks, and
Enterprise tenants can customize the app identity without changing the shared Keycloak realm.

---

## ADR-063 — Email template branding and tenant favicon

**Context.** Tenant branding covered the app header and colors, but notification emails still used
the PAMS name and the browser tab kept the PAMS icon.

**Decision.**
- Email handlers resolve tenant branding at send time and pass `brandName`, `brandColor` and
  `supportEmail` into purchase-order, subscription and invitation templates.
- Email template headers, CTA colors, greetings and footers use tenant branding, falling back to
  PAMS defaults.
- Add tenant favicon storage, upload/read/remove APIs and a branding-page control.
- BrandingProvider applies the tenant favicon to the browser tab and restores the PAMS icon when no
  tenant favicon exists.
- Email `From` address remains the configured system address; tenant `emailFromName` is used as the
  display brand in subjects and message bodies.

**Consequences.** Tenant branding now reaches outbound notification emails and the browser tab. A
per-tenant SMTP From display name and custom domains remain follow-up work.

---

## ADR-064 — Centered PAMS login and per-tenant email sender name

**Context.** The Keycloak login card stretched to the full viewport because the legacy login classes
were not constrained, and the page carried decorative gradients. Tenant email branding still used
the system From display name.

**Decision.**
- Remove login background gradients and constrain the login page to a centered 460px card using the
  actual Keycloak legacy classes (`login-pf-page`, `card-pf`, `pf-c-*`).
- Style both legacy and PatternFly 5 selectors for compatibility across Keycloak themes.
- Extend `MailPort` with an optional From display name. SMTP uses
  `InternetAddress(systemFrom, tenantBrandName)`, while email clients keep the configured From
  address.
- Purchase order, subscription and invitation emails pass tenant branding as the From display name.

**Consequences.** Login is centered and clean at desktop and mobile widths, and tenant branding
reaches email clients' sender name as well as message content.

---

## ADR-065 — Tenant appearance controls

**Context.** Tenant branding covered colors and logos, but workspace layout still looked identical for
every tenant. Arbitrary custom CSS would be unsafe and hard to maintain.

**Decision.**
- Add safe, bounded appearance options to `tenant_branding`: corner radius (0–24), font family
  (`INTER`, `ROBOTO`, `SYSTEM`, `SERIF`) and density (`COMFORTABLE`, `COMPACT`).
- Apply these through the tenant theme provider to MUI shape, typography and compact table/card
  spacing.
- Expose selectors on the Branding page with server-side validation and database constraints.
- Continue to avoid arbitrary custom CSS.

**Consequences.** Enterprise tenants can tune visual density and typography without breaking
accessibility or layout contracts. Custom domains and terminology packs remain future work.

---

## ADR-066 — Tenant terminology packs

**Context.** Appearance options personalized typography and density, but the product nouns remained
fixed. Tenants often call requests, orders, vendors or budgets by different names.

**Decision.**
- Store five tenant terminology labels in `tenant_branding`: Requests, Purchase orders, Vendors,
  Budgets and Catalog items.
- Validate labels as 1–40 characters with safe defaults.
- Expose them in the Branding page terminology section.
- Apply terminology to primary navigation and the corresponding list-page headings.
- Keep technical API names, permissions, routes and database identifiers unchanged.

**Consequences.** Tenant-facing language can match internal terminology without schema or API
fragmentation. Remaining copy in actions, forms and email templates can adopt the same labels
incrementally.

---

## ADR-067 — Branding audit, service-account-first Keycloak auth and login polish

**Context.** Branding changes were not audited, the API preferred the master-realm password grant
even when the service account worked, and the branded login card still carried a primary-color top
border.

**Decision.**
- Record `BRANDING_UPDATED`, `BRANDING_LOGO_UPDATED`, `BRANDING_LOGO_REMOVED`,
  `BRANDING_FAVICON_UPDATED` and `BRANDING_FAVICON_REMOVED` business audit events.
- Prefer Keycloak `client_credentials` whenever `pams-admin` client credentials are configured.
- Move emergency password-grant settings to `KEYCLOAK_ADMIN_FALLBACK_USER/PASSWORD`, separate from
  the master credentials used by setup scripts.
- Remove the primary-color top border from the login card; retain only the neutral card border.

**Consequences.** Branding changes are traceable, normal Keycloak provisioning uses the least-
privileged service account, and the login card is visually clean and centered.

---

## ADR-068 — PAMS Keycloak email theme

**Context.** The login page was PAMS-branded, but Keycloak password-reset and required-action emails
still used the default Keycloak HTML and sender identity.

**Decision.**
- Add `pams` Keycloak email theme inheriting the base email theme, with PAMS-branded password reset,
  email verification, required-action and account-event messages.
- Configure realm `emailTheme=pams`.
- Configure development SMTP to Mailpit with `no-reply@pams.local` and sender name `PAMS`.
- Persist SMTP/theme settings in the realm export for clean environments.
- Verify a real required-action email from Keycloak through Mailpit contains PAMS branding.

**Consequences.** Keycloak-originated account emails now match the PAMS identity. The Keycloak
account console still uses the built-in account v3 application and is a separate customization task.

---

## ADR-069 — PAMS Keycloak account-console theme

**Context.** Keycloak account management still rendered the stock account v3 theme after login and
email branding were customized.

**Decision.**
- Add a `pams` account theme inheriting `keycloak.v3`.
- Override account-console CSS with PAMS primary/secondary colors, PAMS masthead treatment, rounded
  controls, typography and link colors.
- Set realm `accountTheme=pams` and persist it in the realm export.
- Keep the built-in account v3 React assets; no custom account bundle is maintained.

- Assign `default-roles-pams` to seeded demo users and include it in the realm export. Imported
  users otherwise miss the account client roles and receive `401` from the account console APIs.

**Consequences.** Account-console pages use PAMS visual styling and are usable for seeded and newly
created users. New Keycloak users receive `default-roles-pams` automatically.

---

## ADR-070 — Keycloak provisioning fallback and tenant-admin recovery

**Context.** Re-inviting tenant administrators returned an immediate 204 but provisioning failed with
`Could not read Keycloak role`. The service account token contained realm-management roles but the
Keycloak Admin API still returned 401, so user and role lookups failed.

**Decision.**
- Keep service-account configuration, but allow an explicit fallback master admin through
  `KEYCLOAK_ADMIN_FALLBACK_USER/PASSWORD`.
- When fallback credentials are configured, the adapter uses the master admin password grant;
  otherwise it uses `client_credentials`.
- Pass fallback credentials through both Compose files.
- Fix `ops/keycloak/setup-admin-client.sh` to resolve the service-account username from the client ID
  (`service-account-pams-admin`) instead of the client UUID.
- Re-run provisioning for Zeus, Roomy and Warlmat; all three Keycloak admins were recreated and
  provisioning is `ACTIVE`.

**Consequences.** Tenant-admin invitations work reliably again. The service-account Admin API 401
remains a production hardening item; remove the fallback and verify the service account against a
clean Keycloak import before production.

---

## ADR-071 — Keycloak 26.8.2 Admin API service-account scope

**Context.** The service account carried realm-management roles but still received `401` from the
Admin API. Keycloak 26.8.2 introduced an audience check requiring the built-in `kc-admin-api`
client scope.

**Decision.**
- Assign `kc-admin-api` as a default client scope to the `pams-admin` client.
- Update `setup-admin-client.sh` to add that scope and use the correct
  `service-account-pams-admin` username.
- Restore service-account-first authentication in the adapter.
- Keep password fallback only when no service-account credentials are configured.
- Remove fallback credentials from the development `.env`.
- Verify with a disposable tenant: Keycloak user creation and ADMIN role assignment completed with
  the service account, then the test tenant and user were cleaned up.

**Consequences.** Provisioning no longer relies on the master-admin password. The fallback remains
available only as an explicit deployment option.

---

## ADR-072 — Compact account menu and redesigned profile

**Context.** The account menu width was driven by a long disabled roles row, and the profile page
duplicated identity fields between editable and read-only sections while incorrectly requiring a
tenant department for platform administrators.

**Decision.**
- Extract a compact `UserMenu` with a 288px panel, profile header, My profile, Account security and
  Sign out.
- Show the profile display name in the trigger instead of the raw username.
- Remove roles from the menu; show them as compact chips on the profile page.
- Link Account security to the branded Keycloak account console.
- Redesign My profile into Personal details and Account summary cards.
- Hide tenant-only fields for platform administrators and require department only for tenant users.
- Add dirty-state detection and a Reset action.

**Consequences.** The menu no longer grows with role names, the profile page is shorter and clearer,
and platform administrators can save their profile without a tenant department. No backend changes
were needed.

---

## ADR-073 — Synchronize Keycloak account profile changes

**Context.** Updating first name, last name or email in the Keycloak account console did not update
the local PAMS profile, so the user menu and profile page remained stale.

**Decision.**
- Add `GET /users/{subject}` support through `KeycloakAdminPort` using the service account.
- Synchronize the authenticated user's Keycloak email and first/last name into `user_profile` when
  `/api/v1/users/me` is requested.
- Perform the Keycloak lookup outside the database transaction, then apply changes in a local
  transaction.
- Push display-name changes made in PAMS to Keycloak before saving the local profile, keeping both
  sources aligned.
- Refetch `users/me` on window focus in the account menu, avatar and profile page so returning from
  the Keycloak account console refreshes the UI.

**Consequences.** Account-console email and name changes now appear in both tenant and platform PAMS
profiles after the next profile refresh. The service account must retain `view-users` and
`manage-users`; unavailable Keycloak admin access logs a warning without blocking normal reads.

---

## ADR-074 — Allow Keycloak username changes safely

**Context.** The account console showed Username as read-only because the realm had
`editUsernameAllowed=false`. PAMS also looked up accounts by username when resending invitations,
so allowing username changes without adjusting provisioning would create duplicate-email failures.

**Decision.**
- Enable `editUsernameAllowed` in the realm export and running realm.
- Change invitation/reset lookup to resolve Keycloak users by exact email instead of username.
- Preserve the user's existing username when resetting credentials or updating profile fields.
- New users still start with their email as username.
- Keep the internal PAMS identity key on the immutable Keycloak subject, not username.

**Consequences.** Users can now edit usernames in Account Security without breaking invitation resend
or PAMS identity. The profile page's username text may update after the next token refresh, while
name/email changes continue to synchronize through `/users/me`.

---

## ADR-075 — Purchase-order cancellation and invoice-counting correctness

**Context.** Cancelling a draft PO left the request's budget reservation stuck, cancelled POs
prevented recreating an order for the same request, PO issue did not re-check vendor status, and
mismatched invoices consumed invoiceable quantity permanently.

**Decision.**
- Replace the one-PO-per-request unique index with a partial unique index that excludes cancelled
  POs, allowing a new PO after a draft cancellation.
- On draft PO cancellation, reopen the linked request to `APPROVED` and keep its budget reservation
  for the replacement PO.
- On issued PO cancellation, release the committed amount. Block cancellation when receipts or
  invoices exist.
- Re-check vendor `ACTIVE` status before issuing a PO.
- Count only `MATCHED` and `APPROVED` invoices toward already-invoiced quantity.
- Allow matched invoices to be explicitly approved for payment authorization.
- Uppercase request/PO currency values to avoid false invoice currency mismatches.
- Audit vendor create/update/status/delete, organization tolerance changes and draft PO reopen.
- Prevent vendor deletion when referenced by purchase orders or invoices.
- Correct PO UI permission gates and add matched-invoice approval.

**Consequences.** Budget reservations no longer leak on draft cancellation, rejected/mismatched
invoices no longer block valid invoicing, and procurement controls are safer. Vendor archive/bank
verification, richer invoice resolution and payment scheduling remain next-phase work.

---

## ADR-076 — Purchase-order approval gate and entity-specific policies

**Context.** Purchase orders could be issued directly to vendors with only `purchase_order:issue`;
there was no approval step between PO creation and vendor notification. Approval policies were also
request-only.

**Decision.**
- Add `entity_type` to approval policies (`PURCHASE_REQUEST`, `PURCHASE_ORDER`).
- Add PO statuses `PENDING_APPROVAL`, `APPROVED` and `REJECTED`.
- Add tenant-scoped `purchase_order_approval` and step tables with RLS.
- When a PO is created and a matching PO policy exists, approval starts automatically.
- A PO cannot be issued until the approval instance is approved. If no PO policy exists, existing
  draft-issue behavior remains.
- Add submit/resubmit, approve, reject and request-changes endpoints with eligibility checks,
  approval limits, idempotency keys and audit events.
- Rejection reopens the linked request; changes-requested returns the PO to draft.
- Policies UI can select whether a policy applies to purchase requests or purchase orders.
- PO detail shows approval progress and approver actions.

**Consequences.** Tenants can now require configurable amount-tiered PO approval before a vendor
receives an order. Parallel stages, non-amount conditions, quorum and a visual workflow builder
remain the next workflow increment.

---

## ADR-077 — Invoice exception resolution

**Context.** Invoice exceptions could only be approved. A wrong invoice permanently consumed
invoiceable quantity and its number could not be reused, leaving Finance with no supported correction
path.

**Decision.**
- Add invoice statuses `DISPUTED`, `REJECTED` and `VOID`.
- Add resolution reason, actor and timestamp fields.
- Add reject/dispute/void actions with `If-Match`, idempotency and audit events.
- Count only `MATCHED` and `APPROVED` invoices toward already-invoiced quantity.
- Change invoice-number uniqueness to per-vendor and exclude void/rejected invoices so a corrected
  invoice can reuse the number.
- Allow matched invoices to be explicitly approved for payment authorization.
- Surface resolution reason and status in the invoice UI with reject, dispute and void actions.

**Consequences.** Finance can dispose of bad invoices without corrupting matching quantities, and
vendors can submit corrected invoices after rejection/void. Payment remains the next AP increment.

---

## ADR-078 — Vendor payment scheduling and AP recording

**Context.** Approved invoices were a dead end: there was no payment schedule, payment status,
partial payment, payment reference, or AP aging data.

**Decision.**
- Add `vendor_payment` and `vendor_payment_allocation` tables with RLS.
- Payment lifecycle: `SCHEDULED → APPROVED → PAID`, plus `FAILED` and `CANCELED`.
- Only approved or partially paid invoices can be allocated.
- Allocations cannot exceed invoice outstanding amounts and must share a vendor and currency.
- Marking a payment paid updates invoice `paid_amount` and sets `PAID` or `PARTIALLY_PAID`.
- Add `payment:read` and `payment:manage` permissions for ADMIN and FINANCE.
- Add a Finance → Payments list and page-first payment scheduling form.
- Payment execution remains manual; no bank/mobile-money transfer is performed by PAMS.

**Consequences.** Finance can schedule, approve and record vendor payments with a complete audit
trail and accurate invoice balances. Provider execution, remittance advice and AP aging reports are
follow-up increments.

---

## ADR-079 — AP aging report

**Context.** Finance could schedule payments but had no view of outstanding approved invoices by age,
making cash planning and overdue follow-up difficult.

**Decision.**
- Add `GET /api/v1/vendor-payments/aging`, grouped by vendor and currency.
- Buckets: current, 1–30, 31–60, 61–90 and over 90 days based on due date, falling back to invoice
  date.
- Include only `APPROVED` and `PARTIALLY_PAID` invoices with positive outstanding balances.
- Add Finance → Payments → **AP aging** with invoice counts and bucket totals.
- Keep amounts in their native currency; no FX conversion or cross-currency total is invented.

**Consequences.** Finance can see overdue payables and plan payment runs. Remittance advice and
cash-flow forecasting remain follow-ups.

---

## ADR-080 — Public landing, pricing and demo requests

**Context.** The application had no public acquisition surface. Unauthenticated visitors were sent
directly to Keycloak, and there was no way to present pricing or capture demos.

**Decision.**
- Add public `/welcome`, `/pricing` and `/demo` routes.
- Redirect unauthenticated protected routes to `/welcome`, preserving the intended return path.
- Expose a public, read-only plan catalogue without Stripe IDs or tenant data.
- Add a public demo-request form persisted to `demo_request`.
- Change the default post-logout destination to `/welcome`, removing the sign-out login loop.
- Keep assisted signup: demo request → qualification → platform tenant creation.
- Add public endpoint tests and browser coverage for landing, pricing and demo submission.

**Consequences.** Prospects can evaluate PAMS and request a demo without an account. Self-serve
signup, lead administration, rate limiting and CAPTCHA are follow-up increments.

---

## ADR-081 — Parallel approval stages

**Context.** Approval policies could express only a single approver at each sequence. Real
procurement workflows often require several approvers to review the same stage concurrently, for
example Procurement and Finance before an order reaches an administrator.

**Decision.**
- Treat the existing `sequence` value as the stage number and allow multiple steps to share it.
- Drop the per-step uniqueness constraints on request, policy and purchase-order approval steps and
  replace them with non-unique stage indexes (`V33`).
- A request or purchase order remains on the current stage until every pending step in that stage is
  approved. Rejection or change requests short-circuit the remaining stage.
- Skip self-approval steps without blocking the rest of the stage; advance only after the stage has no
  pending steps.
- Add an optional `stage` field to approval-policy step requests. Omitted stages remain sequential.
- Update policy and approval UIs to show grouped stages and allow adding a parallel approver or a new
  stage.
- Cover parallel request and purchase-order approval paths with integration tests.

**Consequences.** Tenants can model parallel review stages for both purchase requests and purchase
orders. Non-amount conditions, quorum rules and a fully visual workflow builder remain follow-up
increments.

---

## ADR-082 — Conditional approval steps and workflow preview

**Context.** Every step in a policy applied to every request or purchase order. Tenants needed
non-amount conditions so that department-specific or budget-exception reviewers only appear when
relevant, and administrators needed to see the resulting workflow before saving.

**Decision.**
- Add `condition_type` (`ALWAYS`, `BUDGET_EXCEPTION`, `DEPARTMENT`) and
  `condition_department_id` to approval-policy steps (`V34`).
- Resolve conditions when the chain is created: the request department is passed into the resolver;
  purchase orders inherit the linked request's department.
- Department conditions require a department ID; budget-exception conditions only apply when the
  request was submitted with that exception.
- Add a workflow preview to the policy editor showing stages, parallel approvers and conditions.
- Keep condition evaluation immutable once an approval instance is created so later policy edits do
  not change in-flight approvals.

**Consequences.** Policies can target specific departments and budget exceptions while preserving
historical approval records. Quorum rules and a fully visual drag-and-drop builder remain follow-up
increments.

---

## ADR-083 — Stage quorum and requester-manager actors

**Context.** Parallel stages required every approver by default. Tenants also needed to model a
stage that completes after a configured number of approvals, and policies could not target the
requester's manager or a specific user from the UI.

**Decision.**
- Add `min_approvals` to policy steps and snapshot it onto request and purchase-order approval steps
  (`V35`).
- Existing stages are backfilled with the number of approvers in the stage, preserving “all must
  approve”.
- A stage completes when approved count reaches the effective quorum, after which remaining pending
  steps are skipped with “Quorum met”.
- Effective quorum is capped by the number of non-skipped steps so self-approval skips cannot create
  an impossible stage.
- Expose actor types in the workflow form: role, requester's manager and specific user.
- Expand role actors to REQUESTER, APPROVER, FINANCE, ADMIN, PROCUREMENT_OFFICER and AUDITOR.
- Do not allow a requester to approve their own request; the existing domain guard remains.
- Add request and PO quorum integration tests.

**Consequences.** Workflow stages can model M-of-N approval, and policies can target managers and
named users. Drag-and-drop polishing and a full visual canvas remain follow-up work.

---

## ADR-084 — Drag-and-drop workflow builder

**Context.** The policy form could configure stages and approvers, but only through flat form rows.
Administrators needed a visual way to reorder stages and move approvers between parallel groups.

**Decision.**
- Add `@dnd-kit/core`, `@dnd-kit/sortable` and `@dnd-kit/utilities`.
- Replace the static workflow preview with a stage board:
  - drag whole stages to reorder them,
  - drag approvers within a stage or between stages,
  - edit stage quorum directly on the stage card.
- Keep the detailed form rows below the board for field editing.
- Use pointer and keyboard sensors with a drag handle and an activation distance to avoid accidental
  drags.
- Renumber stages contiguously after reordering.
- Include requester, approver, finance, admin, procurement officer and auditor role actors, plus
  requester-manager and specific-user actor types.

**Consequences.** Workflow configuration is now visual and keyboard-accessible. Policy changes still
go through the same validated create-version API.

---

## ADR-085 — Tenant/year document reference numbers

**Context.** Purchase orders and invoices had human-readable numbers, but requests, budgets, goods
receipts and vendor payments were identifiable only by UUID. Users needed stable references for
search, email and operational follow-up.

**Decision.**
- Add a shared `document_sequence` table keyed by organization, document type and year.
- Generate immutable references server-side at creation:
  - `PR-{year}-{000001}` for purchase requests,
  - `BUD-{year}-{000001}` for budgets,
  - `GRN-{year}-{000001}` for goods receipts,
  - `PAY-{year}-{000001}` for vendor payments.
- Backfill existing records by document year and creation order in `V36`.
- Add unique tenant-scoped indexes and RLS to the sequence table.
- Expose references in API responses and make them the first column in their tables.
- Allow request search by title or reference.
- Preserve references through edits and state transitions.

**Consequences.** Documents can be referenced safely outside PAMS. Purchase orders and invoices keep
their existing `PO-` and `INV-` numbering.

---

## ADR-086 — Plain-language workflow action configuration

**Context.** Workflow action rules exposed raw status codes, action codes and permission codes. The
tab was limited to purchase requests, hid vendor-payment actions, and gave administrators no way to
understand which buttons a role would actually see.

**Decision.**
- Add a record-type selector for purchase requests and vendor payments.
- Group rules by business status with friendly names and explanations.
- Replace raw action codes with labels and descriptions.
- Show the permission as a plain-language label plus the roles that currently hold it.
- Add an in-page role preview showing which buttons that role would see per status.
- Add warnings for missing essential actions and a discard-changes path.
- Restrict available actions by record type.
- Add the missing `MARK_PAID` action and reject duplicates, unknown statuses and actions that do not
  apply to the selected record type.
- Keep approval-chain configuration in Workflow policies.

**Consequences.** Non-technical administrators can understand and safely change action availability
without reading permission codes. Vendor-payment workflow actions are now configurable.

---

## ADR-087 — Purchase-order workflow actions

**Context.** Workflow action rules were configurable for purchase requests and vendor payments, but
purchase-order buttons were still hardcoded in the UI. That made the Workflow Actions tab incomplete
and prevented tenants from deciding which PO actions appear in each status.

**Decision.**
- Add purchase-order statuses and default action rules in `V37`.
- Add `PurchaseOrderActionService` and `GET /purchase-orders/{id}/actions`.
- Derive PO action visibility from `workflow_action_rule` plus the current user's permissions.
- Support EDIT, SUBMIT_APPROVAL, ISSUE, CANCEL, APPROVE, REJECT, REQUEST_CHANGES, RECEIVE and
  CREATE_INVOICE actions.
- Add Purchase orders to the Workflow Actions record-type selector.
- Replace hardcoded PO detail button conditions with server-provided actions.
- Validate entity-specific actions and keep the plain-language configuration UI.

**Consequences.** Purchase-order buttons are now configurable per status and permission. The workflow
action tab covers all supported record types.

---

## ADR-088 — Requesters cannot approve

**Context.** The REQUESTER role inherited approval inbox and approval-action permissions, so requesters
could see the Approval menu even though they should only prepare and submit requests. Removing read
access outright would also have hidden the approval timeline on their own request.

**Decision.**
- Remove `approval:read`, `approval:act` and `approval:act:REQUESTER` from the REQUESTER role.
- Allow the request approval timeline endpoint to use `purchase_request:read`; the service still
  enforces owner or view-all access.
- Hide the Approval navigation item and dashboard approval card when the user cannot read approvals.
- Remove REQUESTER from selectable approval-policy roles.
- Add an integration test proving requesters cannot access the inbox but can see their own timeline.

**Consequences.** Requesters no longer appear in approval workflows, while retaining visibility of
their own request progress.

---

## ADR-089 — Catalog-first request entry

**Context.** Requesters manually entered description, unit, price and catalog choice for every line.
This created errors and made catalog pricing advisory rather than authoritative.

**Decision.**
- Pre-fill and lock the requester's department from their profile; Procurement/Admin can override
  with `purchase_request:create_for_department`.
- Replace manual lines with catalog-first item cards. Only quantity, notes and justification remain
  editable for catalog items.
- Snapshot catalog name, unit, price and default vendor on the server. Client-supplied catalog
  description/unit/price are ignored.
- Add optional default vendor to catalog items.
- Add non-catalog fallback lines with `is_non_catalog`, notes and `PENDING` review status.
- Enforce one vendor per purchase request.
- Add a catalog admin review queue for pending non-catalog lines.
- Keep request-level justification and optional line notes.

**Consequences.** Request creation is faster and catalog pricing cannot be tampered with. Department
catalog audiences, popularity, favorites and reorder remain follow-up increments.

---

## ADR-090 — Catalog audiences, favorites and popularity

**Context.** Catalog items were tenant-wide. Requesters could not easily find the items their
department actually buys, and recurring users had no personal shortcuts.

**Decision.**
- Add `catalog_item.department_scope` (`ALL` or `SELECTED`) and `catalog_item_department`.
- Requesters see global items plus items assigned to their department; server-side filtering
  enforces the rule.
- Add personal favorites through `catalog_favorite`.
- Expose favorites and “Popular in your department” through catalog discovery endpoints.
- Popularity is computed from non-draft request lines in the requester's department, ordered by
  usage count and recency.
- Add catalog admin controls for department audiences.
- Show favorites and popular items as quick-add actions in the request composer.
- Favorite catalog items from a selected request line.

**Consequences.** Catalog discovery is now personalized and department-aware without duplicating
catalog records. Reorder remains the final follow-up increment.

---

## ADR-091 — Department catalog audiences, favorites, popularity and reorder

**Context.** Department visibility, personal shortcuts and repeat ordering remained after the
catalog-first request form.

**Decision.**
- Catalog items are global or restricted to selected departments through
  `catalog_item_department`.
- Catalog search filters by the requester's department on the server.
- Requesters can favorite catalog items; favorites are personal and tenant-scoped.
- Request form shows favorites and popular items for the requester's department.
- Popularity uses non-draft historical request lines for that department.
- Add a clone endpoint that creates a new draft request with current catalog snapshots.
- Expose Reorder on non-draft request detail pages.

**Consequences.** Requesters can find relevant items faster and repeat recurring purchases without
re-entering line details.

---

## ADR-092 — Purchase-order reorder, vendor locking and starter catalogs

**Context.** Requesters could reorder previous requests, but not directly from a past PO. Catalog
items stored a default vendor, but PO creation still allowed choosing a different vendor. New
tenants also had almost no useful starter catalog.

**Decision.**
- Add `POST /purchase-orders/{id}/reorder`, cloning the linked source request into a new draft.
- Expose Reorder on purchase-order detail pages.
- Include line vendor snapshots in request-to-PO conversion and reject a PO vendor that conflicts
  with the request's locked vendor.
- Pre-fill and lock the PO vendor dialog when the request lines share one vendor.
- Seed starter catalogs in `V41`:
  - ICT monitors, mice, keyboards, laptops, docking, storage, printers, network and software.
  - Finance scanners, shredders, safes, filing, stationery and accounting software.
  - General furniture, stationery, pantry, cleaning, safety and office equipment.
- Department-specific seed items are scoped to the matching department; general items are global.

**Consequences.** Repeated purchasing works from either requests or POs, vendor selection stays
consistent with catalog choices, and new demo/tenant catalogs are immediately useful.
