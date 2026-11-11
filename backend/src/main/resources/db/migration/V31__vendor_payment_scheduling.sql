-- AP payment scheduling and recording.

ALTER TABLE vendor_invoice
    ADD COLUMN paid_amount NUMERIC(19, 4) NOT NULL DEFAULT 0;

ALTER TABLE vendor_invoice
    DROP CONSTRAINT ck_vendor_invoice_status;

ALTER TABLE vendor_invoice
    ADD CONSTRAINT ck_vendor_invoice_status CHECK (status IN (
        'MATCHED', 'MISMATCHED', 'APPROVED', 'DISPUTED', 'REJECTED', 'VOID',
        'PARTIALLY_PAID', 'PAID'));

ALTER TABLE vendor_invoice
    ADD CONSTRAINT ck_vendor_invoice_paid_amount
        CHECK (paid_amount >= 0 AND paid_amount <= total_amount);

CREATE TABLE vendor_payment (
    id              UUID           NOT NULL DEFAULT gen_random_uuid(),
    organization_id UUID           NOT NULL REFERENCES organization (id),
    vendor_id       UUID           NOT NULL REFERENCES vendor (id),
    status          VARCHAR(30)    NOT NULL,
    payment_date    DATE           NOT NULL,
    method          VARCHAR(30)    NOT NULL,
    reference       VARCHAR(200),
    currency        VARCHAR(3)     NOT NULL,
    total_amount    NUMERIC(19, 4) NOT NULL,
    notes           VARCHAR(1000),
    paid_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ    NOT NULL,
    created_by      VARCHAR(100)   NOT NULL,
    updated_at      TIMESTAMPTZ    NOT NULL,
    updated_by      VARCHAR(100)   NOT NULL,
    version         BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT pk_vendor_payment PRIMARY KEY (id),
    CONSTRAINT ck_vendor_payment_status CHECK (status IN (
        'SCHEDULED', 'APPROVED', 'PAID', 'FAILED', 'CANCELED')),
    CONSTRAINT ck_vendor_payment_total CHECK (total_amount > 0)
);
CREATE INDEX ix_vendor_payment_org_status
    ON vendor_payment (organization_id, status, payment_date);

CREATE TABLE vendor_payment_allocation (
    id              UUID           NOT NULL DEFAULT gen_random_uuid(),
    organization_id UUID           NOT NULL REFERENCES organization (id),
    payment_id      UUID           NOT NULL REFERENCES vendor_payment (id) ON DELETE CASCADE,
    invoice_id      UUID           NOT NULL REFERENCES vendor_invoice (id),
    amount          NUMERIC(19, 4) NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL,
    CONSTRAINT pk_vendor_payment_allocation PRIMARY KEY (id),
    CONSTRAINT ck_vendor_payment_allocation_amount CHECK (amount > 0)
);
CREATE UNIQUE INDEX uq_vendor_payment_allocation_invoice
    ON vendor_payment_allocation (payment_id, invoice_id);
CREATE INDEX ix_vendor_payment_allocation_org
    ON vendor_payment_allocation (organization_id, invoice_id);

ALTER TABLE vendor_payment ENABLE ROW LEVEL SECURITY;
ALTER TABLE vendor_payment FORCE ROW LEVEL SECURITY;
CREATE POLICY vendor_payment_tenant_isolation ON vendor_payment
    USING (organization_id = NULLIF(current_setting('app.current_org', true), '')::uuid)
    WITH CHECK (organization_id = NULLIF(current_setting('app.current_org', true), '')::uuid);

ALTER TABLE vendor_payment_allocation ENABLE ROW LEVEL SECURITY;
ALTER TABLE vendor_payment_allocation FORCE ROW LEVEL SECURITY;
CREATE POLICY vendor_payment_allocation_tenant_isolation ON vendor_payment_allocation
    USING (organization_id = NULLIF(current_setting('app.current_org', true), '')::uuid)
    WITH CHECK (organization_id = NULLIF(current_setting('app.current_org', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON vendor_payment TO pams_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON vendor_payment_allocation TO pams_app;

INSERT INTO permission (code, description) VALUES
    ('payment:read', 'View vendor payments and AP aging'),
    ('payment:manage', 'Schedule, approve and record vendor payments')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permission (role_name, permission_code) VALUES
    ('ADMIN', 'payment:read'),
    ('ADMIN', 'payment:manage'),
    ('FINANCE', 'payment:read'),
    ('FINANCE', 'payment:manage')
ON CONFLICT DO NOTHING;

INSERT INTO workflow_status (entity_type, status_code, display_name, sort_order, terminal) VALUES
    ('vendor_payment', 'SCHEDULED', 'Scheduled', 10, FALSE),
    ('vendor_payment', 'APPROVED', 'Approved', 20, FALSE),
    ('vendor_payment', 'PAID', 'Paid', 30, TRUE),
    ('vendor_payment', 'FAILED', 'Failed', 40, TRUE),
    ('vendor_payment', 'CANCELED', 'Canceled', 50, TRUE)
ON CONFLICT DO NOTHING;

INSERT INTO workflow_action_rule (entity_type, from_status, action_code, required_permission) VALUES
    ('vendor_payment', 'SCHEDULED', 'APPROVE', 'payment:manage'),
    ('vendor_payment', 'APPROVED', 'MARK_PAID', 'payment:manage'),
    ('vendor_payment', 'SCHEDULED', 'CANCEL', 'payment:manage'),
    ('vendor_payment', 'APPROVED', 'CANCEL', 'payment:manage')
ON CONFLICT DO NOTHING;
