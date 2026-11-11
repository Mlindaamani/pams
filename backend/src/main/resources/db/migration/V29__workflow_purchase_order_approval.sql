-- Entity-specific approval policies and purchase-order approval gate.

ALTER TABLE approval_policy
    ADD COLUMN entity_type VARCHAR(30) NOT NULL DEFAULT 'PURCHASE_REQUEST';

ALTER TABLE approval_policy
    ADD CONSTRAINT ck_approval_policy_entity_type
        CHECK (entity_type IN ('PURCHASE_REQUEST', 'PURCHASE_ORDER'));

CREATE INDEX ix_approval_policy_entity_amount
    ON approval_policy (organization_id, entity_type, active, amount_min);

ALTER TABLE purchase_order
    DROP CONSTRAINT ck_purchase_order_status;

ALTER TABLE purchase_order
    ADD CONSTRAINT ck_purchase_order_status CHECK (status IN (
        'DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'ISSUED',
        'PARTIALLY_RECEIVED', 'RECEIVED', 'CLOSED', 'CANCELLED'));

DROP INDEX uq_purchase_order_active_request;
CREATE UNIQUE INDEX uq_purchase_order_active_request
    ON purchase_order (purchase_request_id)
    WHERE status NOT IN ('CANCELLED', 'REJECTED');

CREATE TABLE purchase_order_approval (
    id                 UUID         NOT NULL DEFAULT gen_random_uuid(),
    organization_id    UUID         NOT NULL REFERENCES organization (id),
    purchase_order_id  UUID         NOT NULL REFERENCES purchase_order (id) ON DELETE CASCADE,
    policy_id          UUID         NOT NULL REFERENCES approval_policy (id),
    policy_version     INTEGER      NOT NULL,
    policy_name        VARCHAR(200) NOT NULL,
    status             VARCHAR(30)  NOT NULL,
    current_sequence   INTEGER      NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL,
    created_by         VARCHAR(100) NOT NULL,
    updated_at         TIMESTAMPTZ  NOT NULL,
    updated_by         VARCHAR(100) NOT NULL,
    version            BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_purchase_order_approval PRIMARY KEY (id),
    CONSTRAINT ck_purchase_order_approval_status CHECK (status IN (
        'PENDING', 'APPROVED', 'REJECTED', 'CHANGES_REQUESTED', 'CANCELED'))
);
CREATE UNIQUE INDEX uq_purchase_order_approval_po
    ON purchase_order_approval (purchase_order_id);
CREATE INDEX ix_purchase_order_approval_org
    ON purchase_order_approval (organization_id, status);

CREATE TABLE purchase_order_approval_step (
    id                  UUID         NOT NULL DEFAULT gen_random_uuid(),
    organization_id     UUID         NOT NULL REFERENCES organization (id),
    approval_id         UUID         NOT NULL REFERENCES purchase_order_approval (id) ON DELETE CASCADE,
    sequence            INTEGER      NOT NULL,
    name                VARCHAR(200) NOT NULL,
    approver_type       VARCHAR(30)  NOT NULL,
    approver_role       VARCHAR(50),
    assignee_user_id    UUID,
    status              VARCHAR(30)  NOT NULL,
    comment             VARCHAR(1000),
    acted_by            VARCHAR(200),
    acted_at            TIMESTAMPTZ,
    sla_due_at          TIMESTAMPTZ  NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL,
    created_by          VARCHAR(100) NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    updated_by          VARCHAR(100) NOT NULL,
    version             BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_purchase_order_approval_step PRIMARY KEY (id),
    CONSTRAINT ck_purchase_order_approval_step_status CHECK (status IN (
        'PENDING', 'APPROVED', 'REJECTED', 'SKIPPED')),
    CONSTRAINT ck_purchase_order_approval_step_type CHECK (approver_type IN (
        'ROLE', 'USER', 'REQUESTER_MANAGER'))
);
CREATE UNIQUE INDEX uq_purchase_order_approval_step_seq
    ON purchase_order_approval_step (approval_id, sequence);
CREATE INDEX ix_purchase_order_approval_step_org
    ON purchase_order_approval_step (organization_id, status);

ALTER TABLE purchase_order_approval ENABLE ROW LEVEL SECURITY;
ALTER TABLE purchase_order_approval FORCE ROW LEVEL SECURITY;
CREATE POLICY purchase_order_approval_tenant_isolation ON purchase_order_approval
    USING (organization_id = NULLIF(current_setting('app.current_org', true), '')::uuid)
    WITH CHECK (organization_id = NULLIF(current_setting('app.current_org', true), '')::uuid);

ALTER TABLE purchase_order_approval_step ENABLE ROW LEVEL SECURITY;
ALTER TABLE purchase_order_approval_step FORCE ROW LEVEL SECURITY;
CREATE POLICY purchase_order_approval_step_tenant_isolation ON purchase_order_approval_step
    USING (organization_id = NULLIF(current_setting('app.current_org', true), '')::uuid)
    WITH CHECK (organization_id = NULLIF(current_setting('app.current_org', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON purchase_order_approval TO pams_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON purchase_order_approval_step TO pams_app;
