# PAMS Security Notes

## Authentication and authorization

- Keycloak OIDC, Authorization Code + PKCE for the SPA; tokens stay in memory.
- API is a stateless OAuth2 Resource Server validating JWT signature/issuer/expiry.
- Realm roles map to `ROLE_*`: REQUESTER, APPROVER, PROCUREMENT_OFFICER, FINANCE, ADMIN, AUDITOR.
- Deny-by-default filter chain plus `@PreAuthorize` per endpoint; services enforce ownership and
  tenant checks. Requesters cannot approve their own requests.
- CASL mirrors the role matrix in the UI but is **not** a security boundary.

## Tenancy

- `organization_id` on every tenant table, resolved from the JWT `organization_id` claim.
- Defense in depth: Hibernate tenant filter and PostgreSQL RLS (`pams_app` role).
- Cross-tenant lookups return `404`; tenant-isolation tests cover new endpoints.

## Data and transport

- Money uses `NUMERIC(19,4)`/`BigDecimal`; no floating point.
- Validation rejects unknown JSON properties and protects against mass assignment.
- Errors are RFC 7807 without stack traces/internal messages; `traceId` included.
- Security headers/CSP from nginx; API is stateless with CSRF disabled by design.
- File uploads: content sniffing (Tika), size/type/count limits, server-generated object keys.

## Secrets and operations

- Secrets only via environment/secret manager; `.env` is git-ignored, `.env.example` is not secret.
- Never log tokens, passwords, bank details or full PII. Mask sensitive values.
- Dependencies/images scanned in CI (Trivy); rotate Keycloak/DB/MinIO credentials regularly.
- Rate limiting protects write endpoints; 429 includes `Retry-After`.
- Actuator management port must not be publicly routed; only health probes may be exposed.

## Webhooks

- Endpoints are ADMIN-only and URL-validated to absolute HTTP(S) URLs without embedded credentials.
- Payloads are signed with HMAC-SHA256; secrets are 256-bit random values displayed only on creation
  or rotation. Secrets are never returned by list APIs or logged.
- Consumers must verify signatures, enforce timestamp freshness and be idempotent (at-least-once
  delivery, retries possible).
- Restrict outbound egress for the API process where SSRF to internal services is a concern.

## Authorization and workflow configuration

- API authorization is permission-based (`permission` + `role_permission`), not role-name-based.
- Role mappings are managed under **System → Configuration**. ADMIN always has every permission and is
  immutable in the UI.
- Workflow action visibility is computed server-side from `workflow_action_rule`, status, ownership and
  current approval eligibility. Clients must not infer actions from status alone.
- Core state transitions remain code-enforced; configurable action rules cannot bypass illegal state
  transitions.

## Platform administration

- Platform endpoints require `platform:*` permissions under `/api/v1/platform` and intentionally
  operate across tenants. Tenant `ADMIN` must not be granted these permissions in production.
- Suspended tenants are blocked by a cached tenant-status filter (`tenant-suspended`), except for
  platform endpoints.
- Platform audit records are stored separately from tenant audit records.
- Platform analytics exposes aggregates only; it must not return request text, vendor bank details or
  user PII.

## Keycloak admin credentials

- Production must use a dedicated Keycloak service account with only user-management permissions.
- The development password-grant fallback (`KEYCLOAK_ADMIN_USER`/`KEYCLOAK_ADMIN_PASSWORD`) must not
  be used in production.
- Tenant users must have the `organization_id` user-profile attribute; otherwise they cannot be
  tenant-scoped even if Keycloak creates the account.
