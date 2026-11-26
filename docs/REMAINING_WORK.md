# Remaining Work

**Last updated:** 2026-11-26

**Current branch:** `feature/production-readiness`

**Status:** core procurement flows and repository-controlled production scaffolding are in place; PAMS is **not yet cleared for a paying production customer**.

This file is the canonical list of known unfinished work. `docs/SAAS_ROADMAP.md` remains the
strategy and sequencing document; this file tracks concrete launch gaps.

## Legend

| Marker | Meaning |
|---|---|
| **Operator** | Needs real infrastructure, accounts, DNS, credentials or an external provider |
| **Engineering** | Needs repository changes, tests or documentation |
| **Legal** | Needs counsel, policy approval or contractual review |
| **Commercial** | Needs pricing/customer/sales decisions rather than code |
| **Optional** | Deferred until customer evidence justifies it |

## Snapshot

| Area | State |
|---|---|
| Core requests, approvals, POs, receiving, invoices, payments, budgets | Implemented |
| Multi-tenancy, RLS, audit, Keycloak roles and platform administration | Implemented |
| Tenant export, deletion and inactive-subscription read-only access | Implemented |
| Backup/restore scripts, backup verification and local restore validation | Implemented |
| CI scanning, immutable images, deploy/rollback automation | Implemented in repository; environments not configured |
| Alert thresholds and incident-response runbook | Implemented in repository; paging/status accounts not configured |
| Peak/headroom load-test script | Implemented; production-like run and recorded result missing |
| Phase A production launch gates | **Incomplete** |
| Phase B onboarding/self-serve activation | **Incomplete** |
| Sales, support and SaaS-analytics instrumentation | **Mostly incomplete** |

---

## 1. Production Launch Blockers

These items must be closed before the first paying tenant carries business-critical data.

### 1.1 Infrastructure and Operations

- [ ] **Separate staging and production environments.** *(Operator)*
  - Separate accounts/projects, databases, Keycloak realms, object buckets, Stripe keys and DNS.
  - Production data must never be copied into staging without anonymization.
- [ ] **Managed PostgreSQL with automated backups and PITR.** *(Operator)*
  - Set retention and recovery targets; verify PITR, not just logical dumps.
- [ ] **Object-storage durability and lifecycle.** *(Operator)*
  - Enable versioning, lifecycle/expiry rules and off-host encrypted backup storage.
- [ ] **Redis production decision.** *(Operator)*
  - Current assumption is a single AOF-backed Redis. Document why that is acceptable or provision HA.
- [ ] **Production secret manager.** *(Operator)*
  - Remove production secrets from host env files; load from Vault/SSM/Secrets Manager/etc.
  - Define rotation and emergency-revocation procedures.
- [ ] **TLS, HSTS, secure cookies and WAF/edge controls.** *(Operator)*
  - Deploy the nginx template or equivalent behind a managed load balancer/WAF.
  - Add login, invitation and upload limits at the edge. Application write/export limits already exist.
- [ ] **Keycloak production hardening.** *(Operator)*
  - Set the real hostname/TLS/proxy headers, disable demo imports, retain the dedicated Keycloak DB,
    and schedule Keycloak DB backups with the application database.
- [ ] **Configure GitHub Environments and deployment targets.** *(Operator)*
  - Create `staging` and `production`, require production reviewers, and set `DEPLOY_HOST`,
    `DEPLOY_PORT`, `DEPLOY_USER`, `DEPLOY_PATH`, `DEPLOY_SSH_KEY`, `DEPLOY_KNOWN_HOSTS`.
  - Log the deployment host in to GHCR and keep `.env.production` in the secret manager.
- [ ] **Monitoring, error tracking and centralized logs.** *(Operator + Engineering)*
  - Provision uptime/synthetic checks, error tracking with release tags, JSON-log shipping and
    PII redaction/retention. Thresholds are specified in `docs/INCIDENT_RESPONSE.md`.
- [ ] **Status page, on-call routing and escalation contacts.** *(Operator)*
  - Fill the TODO contacts and status-page fields in `docs/INCIDENT_RESPONSE.md`.
- [ ] **Production-like load test.** *(Engineering + Operator)*
  - Run `ops/k6/pams-load.js` at expected peak plus 1.5x headroom against staging.
  - Record date, version, VUs, p95/p99, error rate and bottlenecks in `docs/RUNBOOK.md`.

### 1.2 Security and Compliance

- [ ] **Independent penetration test.** *(Operator + Engineering)*
  - Remediate critical/high findings and retain the report before onboarding customers.
- [ ] **Tenant-isolation evidence report for every endpoint and report.** *(Engineering)*
  - Convert the existing test suite into an endpoint/report coverage matrix with results.
- [ ] **Documented RLS inventory for every tenant-owned table.** *(Engineering)*
  - Include new tables in the review checklist and record the audit evidence.
- [ ] **Audit-log retention and access policy.** *(Legal + Operator)*
  - Define retention, legal hold, export access and deletion rules.
- [ ] **DPA, privacy policy, terms of service and retention policy.** *(Legal)*
- [ ] **Backup encryption and restore-access audit.** *(Operator + Engineering)*
  - Encrypt backups at rest, restrict restore credentials and record who restored what.
- [ ] **Breach notification and incident legal process.** *(Legal + Operator)*
  - Align the repository runbook with contractual and regulatory deadlines.

### 1.3 Billing and Tenant Lifecycle

- [ ] **Live Stripe onboarding for the target country.** *(Commercial + Operator)*
  - Business verification, live keys, live webhook endpoint and supported-country validation.
- [ ] **Tax/VAT and compliant invoices.** *(Commercial + Legal + Operator)*
  - Configure Stripe Tax or a local tax engine; decide invoice/receipt ownership.
- [ ] **Refunds, credit notes and chargebacks.** *(Commercial + Operator)*
  - Document the support workflow, accounting entries and who is authorised.
- [ ] **Revenue recognition and accounting export.** *(Commercial + Engineering)*
  - Define the ledger/reporting feed and reconciliation process.
- [ ] **Payment failure and recovery playbook.** *(Commercial + Operator)*
  - Confirm Stripe retry settings, PAMS dunning copy, escalation and service suspension policy.
- [ ] **Seat management and overage policy.** *(Commercial + Engineering)*
  - Current behaviour is a hard `maxUsers` cap. Decide whether to sell seats/overage and implement
    quantity, proration and metering if required.
- [ ] **Customer billing details.** *(Commercial + Engineering)*
  - Collect billing email, legal name, address and tax ID where needed.
- [ ] **Stripe lifecycle regression evidence.** *(Engineering + Operator)*
  - Run `make stripe-test-clock` with configured Stripe test clocks and retain the result.

---

## 2. Product and Onboarding Gaps

These are Phase B items needed for repeatable self-serve or low-touch onboarding.

- [ ] **Self-serve trial/checkout entry point.** *(Engineering + Commercial)*
  - Public pricing CTA currently leads to demo/contact flow, not direct tenant creation.
- [ ] **Tenant setup wizard.** *(Engineering)*
  - Organization profile, first department and administrator in one guided flow.
- [ ] **Approval-policy starter templates.** *(Engineering + Product)*
  - Provide common threshold and multi-level templates instead of a blank policy.
- [ ] **Guided first request or safe sample data.** *(Engineering + Product)*
  - Help a new tenant reach first value without a sales engineer.
- [ ] **Support ticketing/email workflow.** *(Operator)*
- [ ] **Help center, FAQs and known-issues page.** *(Product + Support)*
- [ ] **SLA for paid plans.** *(Commercial + Legal)*
- [ ] **Customer feedback loop and public release notes.** *(Product)*

---

## 3. Sales, Marketing and Commercial Assets

These are intentionally outside the application but remain unimplemented.

- [ ] One-page ICP and value proposition.
- [ ] 10-slide demo deck and 20-minute demo script.
- [ ] Pricing and packaging sheet validated with real buyers.
- [ ] Public security/trust page.
- [ ] Written customer case study/reference.
- [ ] Proposal and paid-pilot agreement template.
- [ ] CRM pipeline, stages and follow-up cadence.
- [ ] Support/dispute escalation process for billing.

---

## 4. SaaS Metrics Instrumentation

The revenue dashboard exists, but these inputs are still missing for reliable CAC, churn and NRR.

- [ ] Acquisition source and campaign on tenant/prospect records.
- [ ] Sales and marketing spend ledger for CAC and payback.
- [ ] Cancellation reason and cancellation date.
- [ ] Expansion/downgrade event history.
- [ ] Gross-margin inputs: hosting, support and payment fees.
- [ ] Cohort retention and trial-conversion charts.
- [ ] Monthly board-style metrics summary.
- [ ] Document and configure the Stripe dunning/retry schedule.
- [ ] Decide Stripe event retention/archival policy.
- [ ] Decide whether PAMS or Stripe owns invoice/receipt email delivery.
- [ ] Add reviewed bulk reconciliation repair (current reconciliation audit is read-only).

---

## 5. Documentation Hygiene

- [ ] Reconcile stale audit-export notes in `docs/SUBSCRIPTION_BILLING.md` and `docs/DECISIONS.md`;
  the CSV export endpoint now exists and is feature-gated.
- [ ] Record the first staging deploy, rollback drill and production-like load-test result.
- [ ] Add the final staging/production URLs and support contacts to `docs/RUNBOOK.md` and
  `docs/INCIDENT_RESPONSE.md`.
- [ ] Update `docs/SAAS_ROADMAP.md` phase checkboxes as operational items are completed.

---

## 6. Deferred / Optional Phase C+

Not required for the first paying customer; implement only with customer evidence.

- Supplier portal and quote comparison.
- Mobile-friendly approvals.
- Email-to-request intake.
- Invoice OCR/document capture.
- ERP/accounting integrations.
- Slack/Teams/WhatsApp notifications.
- SSO/SAML and enterprise identity controls.
- Multi-currency pricing and local payment rails.
- Budget forecasting and advanced spend analytics.
- Contract/renewal tracking.
- Premium compliance packs/audit bundles.
- Custom domains (tenant logo/favicon/email branding already exists).
- SOC 2 / ISO 27001, multi-region and disaster recovery programme.

---

## Launch Gate

PAMS can be declared launch-ready when:

- [ ] Backup, PITR, encrypted off-host retention and a staging restore drill are evidenced.
- [ ] Staging promotion, production approval and a rollback drill are completed.
- [ ] Monitoring/alerting, on-call, status page and incident contacts are live.
- [ ] A production-like load test meets p95/p99 and error-rate targets with recorded headroom.
- [ ] Pen-test findings, legal documents and RLS/tenant-isolation evidence are approved.
- [ ] Live Stripe, tax/invoice, refund and failure-recovery workflows have been exercised.
- [ ] A tenant can export, delete and restore without manual database edits.
- [ ] No endpoint or report leaks data across tenants.
- [ ] Payment failure cannot silently preserve full access indefinitely.
- [ ] First-customer support and onboarding ownership is assigned.

---

## Related Documents

- `docs/PRODUCTION_READINESS.md` — implemented baseline, RPO/RTO, deploy/load operations.
- `docs/RUNBOOK.md` — day-2 operations, release, rollback, backups and performance results.
- `docs/INCIDENT_RESPONSE.md` — severities, alert thresholds, breach and postmortem process.
- `docs/SAAS_ROADMAP.md` — phased commercial strategy and broader product candidates.
- `docs/SUBSCRIPTION_BILLING.md` — subscription, Stripe and entitlement design.
- `docs/SECURITY.md` — security model and operational security notes.
