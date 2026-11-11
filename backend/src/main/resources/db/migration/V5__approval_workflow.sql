-- Phase 4: approval policies, instances, steps, delegations, audit trail and ShedLock.

CREATE TABLE approval_policy (
    id              UUID           NOT NULL DEFAULT gen_random_uuid(),
    organization_id UUID           NOT NULL REFERENCES organization (id),
    name            VARCHAR(200)   NOT NULL,
    amount_min      NUMERIC(19, 4) NOT NULL,
    amount_max      NUMERIC(19, 4),
    policy_version  INTEGER        NOT NULL,
    active          BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ    NOT NULL,
    created_by      VARCHAR(100)   NOT NULL,
    updated_at      TIMESTAMPTZ    NOT NULL,
    updated_by      VARCHAR(100)   NOT NULL,
    version         BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT pk_approval_policy PRIMARY KEY (id),
    CONSTRAINT ck_approval_policy_amounts CHECK (
        amount_min >= 0 AND (amount_max IS NULL OR amount_max > amount_min))
);
CREATE UNIQUE INDEX uq_approval_policy_name_version
    ON approval_policy (organization_id, name, policy_version);
CREATE INDEX ix_approval_policy_org_active ON approval_policy (organization_id, active);

CREATE TABLE approval_policy_step (
    id                UUID         NOT NULL DEFAULT gen_random_uuid(),
    organization_id   UUID         NOT NULL REFERENCES organization (id),
    policy_id         UUID         NOT NULL REFERENCES approval_policy (id) ON DELETE CASCADE,
    sequence          INTEGER      NOT NULL,
    name              VARCHAR(200) NOT NULL,
    approver_type     VARCHAR(30)  NOT NULL,
    approver_role     VARCHAR(50),
    approver_user_id  UUID,
    sla_hours         INTEGER      NOT NULL DEFAULT 48,
    created_at        TIMESTAMPTZ  NOT NULL,
    created_by        VARCHAR(100) NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,
    updated_by        VARCHAR(100) NOT NULL,
    version           BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_approval_policy_step PRIMARY KEY (id),
    CONSTRAINT ck_approval_policy_step_type
        CHECK (approver_type IN ('ROLE', 'USER', 'REQUESTER_MANAGER')),
    CONSTRAINT ck_approval_policy_step_sla CHECK (sla_hours > 0)
);
CREATE UNIQUE INDEX uq_approval_policy_step_sequence ON approval_policy_step (policy_id, sequence);
CREATE INDEX ix_approval_policy_step_policy ON approval_policy_step (policy_id);

CREATE TABLE approval_instance (
    id                  UUID           NOT NULL DEFAULT gen_random_uuid(),
    organization_id     UUID           NOT NULL REFERENCES organization (id),
    purchase_request_id UUID           NOT NULL REFERENCES purchase_request (id),
    requester_subject   VARCHAR(200)   NOT NULL,
    requester_user_id   UUID,
    policy_id           UUID           NOT NULL REFERENCES approval_policy (id),
    policy_version      INTEGER        NOT NULL,
    currency            VARCHAR(3)     NOT NULL,
    total_amount        NUMERIC(19, 4) NOT NULL,
    status              VARCHAR(30)    NOT NULL,
    current_sequence    INTEGER,
    last_comment        VARCHAR(2000),
    created_at          TIMESTAMPTZ    NOT NULL,
    created_by          VARCHAR(100)   NOT NULL,
    updated_at          TIMESTAMPTZ    NOT NULL,
    updated_by          VARCHAR(100)   NOT NULL,
    version             BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT pk_approval_instance PRIMARY KEY (id),
    CONSTRAINT ck_approval_instance_status CHECK (status IN (
        'IN_PROGRESS', 'APPROVED', 'REJECTED', 'CHANGES_REQUESTED', 'CANCELLED'))
);
CREATE UNIQUE INDEX uq_approval_instance_request ON approval_instance (purchase_request_id);
CREATE INDEX ix_approval_instance_org ON approval_instance (organization_id);

CREATE TABLE approval_step (
    id                UUID         NOT NULL DEFAULT gen_random_uuid(),
    organization_id   UUID         NOT NULL REFERENCES organization (id),
    instance_id       UUID         NOT NULL REFERENCES approval_instance (id) ON DELETE CASCADE,
    sequence          INTEGER      NOT NULL,
    name              VARCHAR(200) NOT NULL,
    approver_type     VARCHAR(30)  NOT NULL,
    assignee_user_id  UUID,
    assignee_role     VARCHAR(50),
    status            VARCHAR(20)  NOT NULL,
    comment           VARCHAR(2000),
    acted_by_subject  VARCHAR(200),
    acted_by_name     VARCHAR(200),
    acted_at          TIMESTAMPTZ,
    sla_due_at        TIMESTAMPTZ,
    reminder_sent_at  TIMESTAMPTZ,
    escalated_at      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL,
    created_by        VARCHAR(100) NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,
    updated_by        VARCHAR(100) NOT NULL,
    version           BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_approval_step PRIMARY KEY (id),
    CONSTRAINT ck_approval_step_status CHECK (status IN (
        'PENDING', 'APPROVED', 'REJECTED', 'SKIPPED', 'DELEGATED')),
    CONSTRAINT ck_approval_step_type
        CHECK (approver_type IN ('ROLE', 'USER', 'REQUESTER_MANAGER'))
);
CREATE UNIQUE INDEX uq_approval_step_instance_sequence ON approval_step (instance_id, sequence);
CREATE INDEX ix_approval_step_instance ON approval_step (instance_id);
CREATE INDEX ix_approval_step_org_status_due
    ON approval_step (organization_id, status, sla_due_at);

CREATE TABLE delegation (
    id                 UUID        NOT NULL DEFAULT gen_random_uuid(),
    organization_id    UUID        NOT NULL REFERENCES organization (id),
    delegator_user_id  UUID        NOT NULL REFERENCES user_profile (id),
    delegate_user_id   UUID        NOT NULL REFERENCES user_profile (id),
    starts_at          TIMESTAMPTZ NOT NULL,
    ends_at            TIMESTAMPTZ NOT NULL,
    active             BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMPTZ NOT NULL,
    created_by         VARCHAR(100) NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL,
    updated_by         VARCHAR(100) NOT NULL,
    version            BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT pk_delegation PRIMARY KEY (id),
    CONSTRAINT ck_delegation_range CHECK (ends_at > starts_at),
    CONSTRAINT ck_delegation_users CHECK (delegator_user_id <> delegate_user_id)
);
CREATE INDEX ix_delegation_org_delegator ON delegation (organization_id, delegator_user_id);
CREATE INDEX ix_delegation_org_delegate ON delegation (organization_id, delegate_user_id);

CREATE TABLE audit_event (
    id              UUID         NOT NULL DEFAULT gen_random_uuid(),
    organization_id UUID         NOT NULL REFERENCES organization (id),
    aggregate_type  VARCHAR(50)  NOT NULL,
    aggregate_id    UUID         NOT NULL,
    action          VARCHAR(80)  NOT NULL,
    actor_subject   VARCHAR(200) NOT NULL,
    actor_name      VARCHAR(200) NOT NULL,
    payload         JSONB        NOT NULL DEFAULT '{}'::jsonb,
    occurred_at     TIMESTAMPTZ  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    created_by      VARCHAR(100) NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    updated_by      VARCHAR(100) NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_audit_event PRIMARY KEY (id)
);
CREATE INDEX ix_audit_event_org_occurred ON audit_event (organization_id, occurred_at DESC);
CREATE INDEX ix_audit_event_aggregate ON audit_event (organization_id, aggregate_type, aggregate_id);

CREATE TABLE shedlock (
    name       VARCHAR(64) NOT NULL,
    lock_until TIMESTAMPTZ NOT NULL,
    locked_at  TIMESTAMPTZ NOT NULL,
    locked_by  VARCHAR(255) NOT NULL,
    CONSTRAINT pk_shedlock PRIMARY KEY (name)
);

ALTER TABLE purchase_request
    ADD COLUMN approval_policy_id UUID REFERENCES approval_policy (id),
    ADD COLUMN approval_policy_version INTEGER,
    ADD COLUMN reserved_amount NUMERIC(19, 4) NOT NULL DEFAULT 0;

GRANT SELECT, INSERT, UPDATE, DELETE ON approval_policy TO pams_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON approval_policy_step TO pams_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON approval_instance TO pams_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON approval_step TO pams_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON delegation TO pams_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON audit_event TO pams_app;

ALTER TABLE approval_policy ENABLE ROW LEVEL SECURITY;
ALTER TABLE approval_policy FORCE ROW LEVEL SECURITY;
CREATE POLICY approval_policy_tenant_isolation ON approval_policy
    USING (organization_id = NULLIF(current_setting('app.current_org', true), '')::uuid)
    WITH CHECK (organization_id = NULLIF(current_setting('app.current_org', true), '')::uuid);

ALTER TABLE approval_policy_step ENABLE ROW LEVEL SECURITY;
ALTER TABLE approval_policy_step FORCE ROW LEVEL SECURITY;
CREATE POLICY approval_policy_step_tenant_isolation ON approval_policy_step
    USING (organization_id = NULLIF(current_setting('app.current_org', true), '')::uuid)
    WITH CHECK (organization_id = NULLIF(current_setting('app.current_org', true), '')::uuid);

ALTER TABLE approval_instance ENABLE ROW LEVEL SECURITY;
ALTER TABLE approval_instance FORCE ROW LEVEL SECURITY;
CREATE POLICY approval_instance_tenant_isolation ON approval_instance
    USING (organization_id = NULLIF(current_setting('app.current_org', true), '')::uuid)
    WITH CHECK (organization_id = NULLIF(current_setting('app.current_org', true), '')::uuid);

ALTER TABLE approval_step ENABLE ROW LEVEL SECURITY;
ALTER TABLE approval_step FORCE ROW LEVEL SECURITY;
CREATE POLICY approval_step_tenant_isolation ON approval_step
    USING (organization_id = NULLIF(current_setting('app.current_org', true), '')::uuid)
    WITH CHECK (organization_id = NULLIF(current_setting('app.current_org', true), '')::uuid);

ALTER TABLE delegation ENABLE ROW LEVEL SECURITY;
ALTER TABLE delegation FORCE ROW LEVEL SECURITY;
CREATE POLICY delegation_tenant_isolation ON delegation
    USING (organization_id = NULLIF(current_setting('app.current_org', true), '')::uuid)
    WITH CHECK (organization_id = NULLIF(current_setting('app.current_org', true), '')::uuid);

ALTER TABLE audit_event ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_event FORCE ROW LEVEL SECURITY;
CREATE POLICY audit_event_tenant_isolation ON audit_event
    USING (organization_id = NULLIF(current_setting('app.current_org', true), '')::uuid)
    WITH CHECK (organization_id = NULLIF(current_setting('app.current_org', true), '')::uuid);
