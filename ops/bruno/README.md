# PAMS Bruno collection

Prerequisite: `docker compose up -d` (PostgreSQL, Keycloak and the API). The dev profile seeds the
demo organization `11111111-1111-1111-1111-111111111111`.

1. **Import the collection folder** `ops/bruno/pams` in Bruno and select the `local` environment.
   Importing only `http://localhost:8080/openapi.json` gives you the protected API endpoints but not
   the Keycloak login request, so every API call will return `401`.
2. Run `auth/Token (admin)` first. Its post-response script stores `accessToken`; the other
   requests use `Authorization: Bearer {{accessToken}}`. Use the requester/approver/officer/
   finance/auditor token requests for role-specific testing.

The API does not have a login endpoint by design: Keycloak is the identity provider. The token
endpoint is:

```text
POST http://localhost:8081/realms/pams/protocol/openid-connect/token
Content-Type: application/x-www-form-urlencoded

grant_type=password
client_id=pams-api
username=admin
password=admin
```

Copy `access_token` from the response into Bruno auth as a Bearer token, or add that request to an
OpenAPI-imported collection with a post-response script setting `accessToken`.
3. Run requests in order where IDs are needed:
   - `departments/Create department` stores `departmentId`
   - `users/Create user profile` stores `userId`
   - `vendors/Create vendor` stores `vendorId`
   - `catalog/Create catalog item` stores `catalogItemId`
   - `budgets/Create budget` stores `budgetId` (run with the `finance` or `admin` token)
   - `purchase-requests/Create purchase request` stores `requestId` and `requestEtag`

Demo users (password = username): `admin` (ADMIN), `officer` (PROCUREMENT_OFFICER),
`requester` (REQUESTER), `approver` (APPROVER), `finance` (FINANCE), `auditor` (AUDITOR).
Use `auth/Token (requester)` to verify that procurement mutations return 403.

Purchase request create/submit/cancel send `Idempotency-Key` (Bruno `{{$uuid}}`), and submit/cancel
send the stored `If-Match` ETag. Run `purchase-requests/Get` after editing to refresh `requestEtag`
before another action, or if a `412 precondition-failed` is returned.

After submitting, use `approvals/Inbox` with an approver token (run `auth/Token (approver)`), then
`approvals/Approve`, `Reject` or `Request changes` (each needs `Idempotency-Key`). The demo policies
are seeded: amounts below 1000 need APPROVER, 1000–9999 need APPROVER then FINANCE, and 10000+ add
ADMIN. `approvals/Delegation` creates a time-boxed delegation for the current user.

After a request is APPROVED, use `purchase-orders/Create` (officer token, `{{vendorId}}`), then
`Issue` with the returned ETag. `purchase-orders/Pdf` streams the generated PDF and
`notifications/List` shows the in-app notification once the outbox poller runs (or Mailpit at
http://localhost:8025 shows the vendor email).

`reports/Spend`, `Approval cycle time` and `Commitments` are cached in Redis for 5 minutes and
evicted whenever budgets or purchase orders change. `reports/Spend CSV` streams the export.
`audit/List` returns an opaque `nextCursor`; call it again with the cursor to fetch the next page.
