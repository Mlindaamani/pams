-- Phase 5: purchase orders, numbering, outbox and notifications.

CREATE TABLE purchase_order_sequence (
    organization_id UUID   NOT NULL REFERENCES organization (id),
    fiscal_year     INTEGER NOT NULL,
    next_value      BIGINT  NOT NULL,
    CONSTRAINT pk_purchase_order_sequence PRIMARY KEY (organization_id, fiscal_year)
);

CREATE TABLE purchase_order (
    id                  UUID           NOT NULL DEFAULT gen_random_uuid(),
    organization_id     UUID           NOT NULL REFERENCES organization (id),
    purchase_request_id UUID           NOT NULL REFERENCES purchase_request (id),
    requester_subject   VARCHAR(200)   NOT NULL,
    po_number           VARCHAR(40)    NOT NULL,
    vendor_id           UUID           NOT NULL REFERENCES vendor (id),
    status              VARCHAR(30)    NOT NULL,
    currency            VARCHAR(3)     NOT NULL,
    total_amount        NUMERIC(19, 4) NOT NULL,
    payment_terms       VARCHAR(500),
    delivery_date       DATE,
    issued_at           TIMESTAMPTZ,
    budget_id           UUID           REFERENCES budget (id),
    budget_reserved_amount NUMERIC(19, 4),
    created_at          TIMESTAMPTZ    NOT NULL,
    created_by          VARCHAR(100)   NOT NULL,
    updated_at          TIMESTAMPTZ    NOT NULL,
    updated_by          VARCHAR(100)   NOT NULL,
    version             BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT pk_purchase_order PRIMARY KEY (id),
    CONSTRAINT ck_purchase_order_status CHECK (status IN (
        'DRAFT', 'ISSUED', 'PARTIALLY_RECEIVED', 'RECEIVED', 'CLOSED', 'CANCELLED')),
    CONSTRAINT ck_purchase_order_total CHECK (total_amount >= 0)
);
CREATE UNIQUE INDEX uq_purchase_order_request ON purchase_order (purchase_request_id);
CREATE UNIQUE INDEX uq_purchase_order_number ON purchase_order (organization_id, po_number);
CREATE INDEX ix_purchase_order_org_status ON purchase_order (organization_id, status);
CREATE INDEX ix_purchase_order_vendor ON purchase_order (vendor_id);
CREATE INDEX ix_purchase_order_org_created ON purchase_order (organization_id, created_at DESC);

CREATE TABLE purchase_order_line (
    id                UUID           NOT NULL DEFAULT gen_random_uuid(),
    organization_id   UUID           NOT NULL REFERENCES organization (id),
    purchase_order_id UUID           NOT NULL REFERENCES purchase_order (id) ON DELETE CASCADE,
    sequence          INTEGER        NOT NULL,
    description       VARCHAR(500)   NOT NULL,
    quantity          NUMERIC(19, 4) NOT NULL,
    unit              VARCHAR(20)    NOT NULL,
    unit_price        NUMERIC(19, 4) NOT NULL,
    line_total        NUMERIC(19, 4) NOT NULL,
    created_at        TIMESTAMPTZ    NOT NULL,
    created_by        VARCHAR(100)   NOT NULL,
    updated_at        TIMESTAMPTZ    NOT NULL,
    updated_by        VARCHAR(100)   NOT NULL,
    version           BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT pk_purchase_order_line PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uq_purchase_order_line_sequence
    ON purchase_order_line (purchase_order_id, sequence);
CREATE INDEX ix_purchase_order_line_order ON purchase_order_line (purchase_order_id);

CREATE TABLE outbox_event (
    id              UUID          NOT NULL DEFAULT gen_random_uuid(),
    organization_id UUID          NOT NULL REFERENCES organization (id),
    aggregate_type  VARCHAR(50)   NOT NULL,
    aggregate_id    UUID          NOT NULL,
    event_type      VARCHAR(100)  NOT NULL,
    payload         JSONB         NOT NULL,
    attempts        INTEGER       NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ   NOT NULL,
    processed_at    TIMESTAMPTZ,
    dead_lettered_at TIMESTAMPTZ,
    created_at      TIMESTAMPTZ   NOT NULL,
    created_by      VARCHAR(100)  NOT NULL,
    updated_at      TIMESTAMPTZ   NOT NULL,
    updated_by      VARCHAR(100)  NOT NULL,
    version         BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT pk_outbox_event PRIMARY KEY (id)
);
CREATE INDEX ix_outbox_event_due
    ON outbox_event (next_attempt_at)
    WHERE processed_at IS NULL AND dead_lettered_at IS NULL;

CREATE TABLE notification (
    id                UUID          NOT NULL DEFAULT gen_random_uuid(),
    organization_id   UUID          NOT NULL REFERENCES organization (id),
    recipient_subject VARCHAR(200)  NOT NULL,
    recipient_email   VARCHAR(320),
    type              VARCHAR(50)   NOT NULL,
    title             VARCHAR(300)  NOT NULL,
    body              VARCHAR(2000) NOT NULL,
    event_id          UUID          NOT NULL,
    read_at           TIMESTAMPTZ,
    created_at        TIMESTAMPTZ   NOT NULL,
    created_by        VARCHAR(100)  NOT NULL,
    updated_at        TIMESTAMPTZ   NOT NULL,
    updated_by        VARCHAR(100)  NOT NULL,
    version           BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT pk_notification PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uq_notification_event_recipient
    ON notification (organization_id, event_id, recipient_subject);
CREATE INDEX ix_notification_recipient
    ON notification (organization_id, recipient_subject, created_at DESC);

GRANT SELECT, INSERT, UPDATE, DELETE ON purchase_order_sequence TO pams_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON purchase_order TO pams_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON purchase_order_line TO pams_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON outbox_event TO pams_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON notification TO pams_app;

ALTER TABLE purchase_order ENABLE ROW LEVEL SECURITY;
ALTER TABLE purchase_order FORCE ROW LEVEL SECURITY;
CREATE POLICY purchase_order_tenant_isolation ON purchase_order
    USING (organization_id = NULLIF(current_setting('app.current_org', true), '')::uuid)
    WITH CHECK (organization_id = NULLIF(current_setting('app.current_org', true), '')::uuid);

ALTER TABLE purchase_order_line ENABLE ROW LEVEL SECURITY;
ALTER TABLE purchase_order_line FORCE ROW LEVEL SECURITY;
CREATE POLICY purchase_order_line_tenant_isolation ON purchase_order_line
    USING (organization_id = NULLIF(current_setting('app.current_org', true), '')::uuid)
    WITH CHECK (organization_id = NULLIF(current_setting('app.current_org', true), '')::uuid);

ALTER TABLE notification ENABLE ROW LEVEL SECURITY;
ALTER TABLE notification FORCE ROW LEVEL SECURITY;
CREATE POLICY notification_tenant_isolation ON notification
    USING (organization_id = NULLIF(current_setting('app.current_org', true), '')::uuid)
    WITH CHECK (organization_id = NULLIF(current_setting('app.current_org', true), '')::uuid);

ALTER TABLE purchase_order_sequence ENABLE ROW LEVEL SECURITY;
ALTER TABLE purchase_order_sequence FORCE ROW LEVEL SECURITY;
CREATE POLICY purchase_order_sequence_tenant_isolation ON purchase_order_sequence
    USING (organization_id = NULLIF(current_setting('app.current_org', true), '')::uuid)
    WITH CHECK (organization_id = NULLIF(current_setting('app.current_org', true), '')::uuid);

-- outbox_event intentionally has no RLS: the outbox poller processes all tenants with
-- FOR UPDATE SKIP LOCKED and handlers re-enter the owning tenant context.
