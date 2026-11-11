-- SaaS subscription plans and tenant billing state.

CREATE TABLE subscription_plan (
    code                  VARCHAR(30)    NOT NULL,
    name                  VARCHAR(100)   NOT NULL,
    description           VARCHAR(500)   NOT NULL,
    monthly_price         NUMERIC(19, 4) NOT NULL,
    annual_price          NUMERIC(19, 4) NOT NULL,
    currency              VARCHAR(3)     NOT NULL,
    trial_days            INTEGER        NOT NULL DEFAULT 14,
    max_users             INTEGER,
    max_departments       INTEGER,
    max_requests_month    INTEGER,
    max_storage_mb        INTEGER,
    features              JSONB          NOT NULL,
    active                BOOLEAN        NOT NULL DEFAULT TRUE,
    sort_order            INTEGER        NOT NULL,
    stripe_price_monthly  VARCHAR(100),
    stripe_price_annual   VARCHAR(100),
    created_at            TIMESTAMPTZ    NOT NULL,
    updated_at            TIMESTAMPTZ    NOT NULL,
    CONSTRAINT pk_subscription_plan PRIMARY KEY (code),
    CONSTRAINT ck_subscription_plan_prices CHECK (monthly_price >= 0 AND annual_price >= 0)
);

INSERT INTO subscription_plan
    (code, name, description, monthly_price, annual_price, currency, trial_days,
     max_users, max_departments, max_requests_month, max_storage_mb, features, active, sort_order,
     created_at, updated_at)
VALUES
    ('STARTER', 'Starter', 'Core procurement for small teams.', 29, 290, 'USD', 14,
     10, 3, 100, 2048, '["requests","approvals","vendors","purchase_orders","core_reports"]'::jsonb,
     TRUE, 10, now(), now()),
    ('GROWTH', 'Growth', 'Full procurement with receipts, invoices and webhooks.', 99, 990, 'USD', 14,
     30, 10, 1000, 20480,
     '["requests","approvals","vendors","purchase_orders","core_reports","receipts","invoices","webhooks","advanced_reports"]'::jsonb,
     TRUE, 20, now(), now()),
    ('ENTERPRISE', 'Enterprise', 'Unlimited scale, SSO and priority support.', 299, 2990, 'USD', 14,
     NULL, NULL, NULL, 102400,
     '["requests","approvals","vendors","purchase_orders","core_reports","receipts","invoices","webhooks","advanced_reports","sso","audit_export","priority_support"]'::jsonb,
     TRUE, 30, now(), now());

CREATE TABLE tenant_subscription (
    id                       UUID           NOT NULL DEFAULT gen_random_uuid(),
    organization_id          UUID           NOT NULL REFERENCES organization (id),
    plan_code                VARCHAR(30)    NOT NULL REFERENCES subscription_plan (code),
    status                   VARCHAR(30)    NOT NULL,
    billing_interval         VARCHAR(20)    NOT NULL DEFAULT 'MONTHLY',
    stripe_customer_id       VARCHAR(100),
    stripe_subscription_id   VARCHAR(100),
    trial_ends_at            TIMESTAMPTZ,
    current_period_start     TIMESTAMPTZ,
    current_period_end       TIMESTAMPTZ,
    cancel_at_period_end     BOOLEAN        NOT NULL DEFAULT FALSE,
    seats                    INTEGER,
    created_at               TIMESTAMPTZ    NOT NULL,
    created_by               VARCHAR(100)   NOT NULL,
    updated_at               TIMESTAMPTZ    NOT NULL,
    updated_by               VARCHAR(100)   NOT NULL,
    version                  BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT pk_tenant_subscription PRIMARY KEY (id),
    CONSTRAINT ck_tenant_subscription_status CHECK (status IN (
        'TRIALING', 'ACTIVE', 'PAST_DUE', 'UNPAID', 'CANCELED', 'INCOMPLETE')),
    CONSTRAINT ck_tenant_subscription_interval CHECK (billing_interval IN ('MONTHLY', 'ANNUAL')),
    CONSTRAINT ck_tenant_subscription_seats CHECK (seats IS NULL OR seats > 0)
);
CREATE UNIQUE INDEX uq_tenant_subscription_org ON tenant_subscription (organization_id);
CREATE UNIQUE INDEX uq_tenant_subscription_stripe ON tenant_subscription (stripe_subscription_id)
    WHERE stripe_subscription_id IS NOT NULL;
CREATE INDEX ix_tenant_subscription_status ON tenant_subscription (status, current_period_end);

CREATE TABLE stripe_event (
    event_id     VARCHAR(200) NOT NULL,
    event_type   VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ  NOT NULL,
    payload      JSONB        NOT NULL,
    CONSTRAINT pk_stripe_event PRIMARY KEY (event_id)
);

CREATE TABLE tenant_provisioning (
    id               UUID         NOT NULL DEFAULT gen_random_uuid(),
    organization_id  UUID         NOT NULL REFERENCES organization (id),
    admin_email      VARCHAR(320) NOT NULL,
    admin_name       VARCHAR(200) NOT NULL,
    status           VARCHAR(30)  NOT NULL,
    attempts         INTEGER      NOT NULL DEFAULT 0,
    last_error       VARCHAR(1000),
    completed_at     TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_tenant_provisioning PRIMARY KEY (id),
    CONSTRAINT ck_tenant_provisioning_status CHECK (status IN ('PENDING', 'ACTIVE', 'FAILED'))
);
CREATE UNIQUE INDEX uq_tenant_provisioning_org ON tenant_provisioning (organization_id);

GRANT SELECT, INSERT, UPDATE, DELETE ON subscription_plan TO pams_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON tenant_subscription TO pams_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON stripe_event TO pams_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON tenant_provisioning TO pams_app;

INSERT INTO permission (code, description) VALUES
    ('subscription:read', 'View subscription and usage'),
    ('subscription:manage', 'Manage subscription, checkout and billing portal');

INSERT INTO role_permission (role_name, permission_code) VALUES
    ('ADMIN', 'subscription:read'),
    ('ADMIN', 'subscription:manage'),
    ('PLATFORM_ADMIN', 'subscription:read'),
    ('PLATFORM_ADMIN', 'subscription:manage');

INSERT INTO tenant_subscription
    (organization_id, plan_code, status, billing_interval, trial_ends_at,
     created_at, created_by, updated_at, updated_by)
SELECT o.id, 'GROWTH', 'TRIALING', 'MONTHLY', now() + interval '14 days',
       now(), 'system', now(), 'system'
  FROM organization o
 WHERE NOT EXISTS (SELECT 1 FROM tenant_subscription ts WHERE ts.organization_id = o.id);

-- Subscription tables are read across tenants by the platform admin.
-- API queries and billing handlers filter by organization_id explicitly.
