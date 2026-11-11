-- Configurable subscription feature catalogue and plan-feature mapping.

CREATE TABLE subscription_feature (
    code          VARCHAR(50)  NOT NULL,
    name          VARCHAR(100) NOT NULL,
    description   VARCHAR(500) NOT NULL,
    category      VARCHAR(50)  NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order    INTEGER      NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    created_by    VARCHAR(100) NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    updated_by    VARCHAR(100) NOT NULL,
    CONSTRAINT pk_subscription_feature PRIMARY KEY (code),
    CONSTRAINT ck_subscription_feature_category CHECK (category IN (
        'CORE', 'PROCUREMENT', 'FINANCE', 'INTEGRATIONS', 'SECURITY', 'SUPPORT'))
);
CREATE INDEX ix_subscription_feature_active_sort
    ON subscription_feature (active, sort_order);

CREATE TABLE subscription_plan_feature (
    plan_code     VARCHAR(30) NOT NULL REFERENCES subscription_plan (code) ON DELETE CASCADE,
    feature_code  VARCHAR(50) NOT NULL REFERENCES subscription_feature (code) ON DELETE RESTRICT,
    CONSTRAINT pk_subscription_plan_feature PRIMARY KEY (plan_code, feature_code)
);
CREATE INDEX ix_subscription_plan_feature_feature
    ON subscription_plan_feature (feature_code);

INSERT INTO subscription_feature
    (code, name, description, category, active, sort_order, created_at, created_by, updated_at, updated_by)
VALUES
    ('requests', 'Purchase requests',
     'Create, submit and track purchase requests through approval.',
     'CORE', TRUE, 10, now(), 'system', now(), 'system'),
    ('approvals', 'Multi-level approvals',
     'Configurable approval policies, approval inbox and delegation.',
     'CORE', TRUE, 20, now(), 'system', now(), 'system'),
    ('vendors', 'Vendor management',
     'Maintain vendors, contacts and active/inactive status.',
     'CORE', TRUE, 30, now(), 'system', now(), 'system'),
    ('purchase_orders', 'Purchase orders',
     'Issue, update, receive and close purchase orders.',
     'CORE', TRUE, 40, now(), 'system', now(), 'system'),
    ('core_reports', 'Core reports',
     'Spend, commitments and basic procurement reporting.',
     'CORE', TRUE, 50, now(), 'system', now(), 'system'),
    ('receipts', 'Goods receipts',
     'Record partial and complete goods receipts against purchase orders.',
     'PROCUREMENT', TRUE, 60, now(), 'system', now(), 'system'),
    ('invoices', 'Invoice matching',
     'Capture vendor invoices and run three-way matching controls.',
     'FINANCE', TRUE, 70, now(), 'system', now(), 'system'),
    ('advanced_reports', 'Advanced reports',
     'Extended procurement analytics, filters and exports.',
     'FINANCE', TRUE, 80, now(), 'system', now(), 'system'),
    ('webhooks', 'Webhooks',
     'Signed outbound event delivery with retries and delivery logs.',
     'INTEGRATIONS', TRUE, 90, now(), 'system', now(), 'system'),
    ('sso', 'Single sign-on (SSO)',
     'Enterprise identity provider sign-in and centralized access control.',
     'SECURITY', TRUE, 100, now(), 'system', now(), 'system'),
    ('audit_export', 'Audit export',
     'Export tenant audit history for compliance and review.',
     'SECURITY', TRUE, 110, now(), 'system', now(), 'system'),
    ('priority_support', 'Priority support',
     'Priority support response and onboarding assistance.',
     'SUPPORT', TRUE, 120, now(), 'system', now(), 'system');

INSERT INTO subscription_plan_feature (plan_code, feature_code)
SELECT sp.code, feature.code
  FROM subscription_plan sp
 CROSS JOIN LATERAL jsonb_array_elements_text(sp.features) AS feature(code)
  JOIN subscription_feature sf ON sf.code = feature.code;

ALTER TABLE subscription_plan DROP COLUMN features;

GRANT SELECT, INSERT, UPDATE, DELETE ON subscription_feature TO pams_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON subscription_plan_feature TO pams_app;
