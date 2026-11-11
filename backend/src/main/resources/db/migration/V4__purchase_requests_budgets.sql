-- Phase 3: budgets, purchase requests, lines, attachments and idempotency.

CREATE TABLE budget (
    id                UUID           NOT NULL DEFAULT gen_random_uuid(),
    organization_id   UUID           NOT NULL REFERENCES organization (id),
    department_id     UUID           NOT NULL REFERENCES department (id),
    fiscal_year       INTEGER        NOT NULL,
    category          VARCHAR(100),
    currency          VARCHAR(3)     NOT NULL,
    allocated_amount  NUMERIC(19, 4) NOT NULL,
    reserved_amount   NUMERIC(19, 4) NOT NULL DEFAULT 0,
    committed_amount  NUMERIC(19, 4) NOT NULL DEFAULT 0,
    actual_amount     NUMERIC(19, 4) NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ    NOT NULL,
    created_by        VARCHAR(100)   NOT NULL,
    updated_at        TIMESTAMPTZ    NOT NULL,
    updated_by        VARCHAR(100)   NOT NULL,
    version           BIGINT         NOT NULL DEFAULT 0,
    deleted_at        TIMESTAMPTZ,
    deleted_by        VARCHAR(100),
    CONSTRAINT pk_budget PRIMARY KEY (id),
    CONSTRAINT ck_budget_amounts CHECK (
        allocated_amount >= 0
        AND reserved_amount >= 0
        AND committed_amount >= 0
        AND actual_amount >= 0),
    CONSTRAINT ck_budget_fiscal_year CHECK (fiscal_year BETWEEN 2000 AND 2200)
);
CREATE UNIQUE INDEX uq_budget_department_year_category
    ON budget (organization_id, department_id, fiscal_year, COALESCE(category, ''))
    WHERE deleted_at IS NULL;
CREATE INDEX ix_budget_org ON budget (organization_id);
CREATE INDEX ix_budget_department ON budget (department_id);
CREATE INDEX ix_budget_org_year ON budget (organization_id, fiscal_year);

CREATE TABLE purchase_request (
    id                        UUID           NOT NULL DEFAULT gen_random_uuid(),
    organization_id           UUID           NOT NULL REFERENCES organization (id),
    requester_subject         VARCHAR(200)   NOT NULL,
    requester_name            VARCHAR(200)   NOT NULL,
    department_id             UUID           NOT NULL REFERENCES department (id),
    title                     VARCHAR(300)   NOT NULL,
    justification             VARCHAR(2000)  NOT NULL,
    needed_by                 DATE,
    currency                  VARCHAR(3)     NOT NULL,
    status                    VARCHAR(30)    NOT NULL,
    total_amount              NUMERIC(19, 4) NOT NULL DEFAULT 0,
    budget_id                 UUID           REFERENCES budget (id),
    budget_reserved           BOOLEAN        NOT NULL DEFAULT FALSE,
    budget_exception_requested BOOLEAN       NOT NULL DEFAULT FALSE,
    budget_exception          BOOLEAN        NOT NULL DEFAULT FALSE,
    submitted_at              TIMESTAMPTZ,
    created_at                TIMESTAMPTZ    NOT NULL,
    created_by                VARCHAR(100)   NOT NULL,
    updated_at                TIMESTAMPTZ    NOT NULL,
    updated_by                VARCHAR(100)   NOT NULL,
    version                   BIGINT         NOT NULL DEFAULT 0,
    deleted_at                TIMESTAMPTZ,
    deleted_by                VARCHAR(100),
    CONSTRAINT pk_purchase_request PRIMARY KEY (id),
    CONSTRAINT ck_purchase_request_status CHECK (status IN (
        'DRAFT', 'SUBMITTED', 'IN_APPROVAL', 'APPROVED', 'REJECTED',
        'CHANGES_REQUESTED', 'CANCELLED', 'CONVERTED_TO_PO')),
    CONSTRAINT ck_purchase_request_total CHECK (total_amount >= 0)
);
CREATE INDEX ix_purchase_request_org ON purchase_request (organization_id);
CREATE INDEX ix_purchase_request_org_status ON purchase_request (organization_id, status);
CREATE INDEX ix_purchase_request_org_department ON purchase_request (organization_id, department_id);
CREATE INDEX ix_purchase_request_org_requester ON purchase_request (organization_id, requester_subject);
CREATE INDEX ix_purchase_request_org_created_at ON purchase_request (organization_id, created_at DESC);
CREATE INDEX ix_purchase_request_org_total ON purchase_request (organization_id, total_amount);
CREATE INDEX ix_purchase_request_budget ON purchase_request (budget_id);

CREATE TABLE purchase_request_line (
    id                   UUID           NOT NULL DEFAULT gen_random_uuid(),
    organization_id      UUID           NOT NULL REFERENCES organization (id),
    purchase_request_id  UUID           NOT NULL REFERENCES purchase_request (id) ON DELETE CASCADE,
    catalog_item_id      UUID           REFERENCES catalog_item (id),
    description          VARCHAR(500)   NOT NULL,
    quantity             NUMERIC(19, 4) NOT NULL,
    unit                 VARCHAR(20)    NOT NULL,
    unit_price           NUMERIC(19, 4) NOT NULL,
    line_total           NUMERIC(19, 4) NOT NULL,
    position             INTEGER        NOT NULL,
    created_at           TIMESTAMPTZ    NOT NULL,
    created_by           VARCHAR(100)   NOT NULL,
    updated_at           TIMESTAMPTZ    NOT NULL,
    updated_by           VARCHAR(100)   NOT NULL,
    version              BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT pk_purchase_request_line PRIMARY KEY (id),
    CONSTRAINT ck_purchase_request_line_qty CHECK (quantity > 0),
    CONSTRAINT ck_purchase_request_line_price CHECK (unit_price >= 0),
    CONSTRAINT ck_purchase_request_line_total CHECK (line_total >= 0)
);
CREATE INDEX ix_purchase_request_line_request ON purchase_request_line (purchase_request_id);
CREATE INDEX ix_purchase_request_line_catalog ON purchase_request_line (catalog_item_id);
CREATE INDEX ix_purchase_request_line_org ON purchase_request_line (organization_id);

CREATE TABLE request_attachment (
    id                  UUID         NOT NULL DEFAULT gen_random_uuid(),
    organization_id     UUID         NOT NULL REFERENCES organization (id),
    purchase_request_id UUID         NOT NULL REFERENCES purchase_request (id) ON DELETE CASCADE,
    file_name           VARCHAR(255) NOT NULL,
    content_type        VARCHAR(100) NOT NULL,
    size_bytes          BIGINT       NOT NULL,
    checksum_sha256     VARCHAR(64)  NOT NULL,
    storage_key         VARCHAR(500) NOT NULL,
    uploaded_by         VARCHAR(200) NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL,
    created_by          VARCHAR(100) NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    updated_by          VARCHAR(100) NOT NULL,
    version             BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_request_attachment PRIMARY KEY (id),
    CONSTRAINT ck_request_attachment_size CHECK (size_bytes > 0 AND size_bytes <= 10485760)
);
CREATE INDEX ix_request_attachment_request ON request_attachment (purchase_request_id);
CREATE INDEX ix_request_attachment_org ON request_attachment (organization_id);

CREATE TABLE idempotency_record (
    id               UUID          NOT NULL DEFAULT gen_random_uuid(),
    organization_id  UUID          NOT NULL REFERENCES organization (id),
    idempotency_key  VARCHAR(200)  NOT NULL,
    request_hash     VARCHAR(64)   NOT NULL,
    response_status  INTEGER       NOT NULL,
    response_body    TEXT          NOT NULL,
    response_headers TEXT          NOT NULL,
    expires_at       TIMESTAMPTZ   NOT NULL,
    created_at       TIMESTAMPTZ   NOT NULL,
    created_by       VARCHAR(100)  NOT NULL,
    updated_at       TIMESTAMPTZ   NOT NULL,
    updated_by       VARCHAR(100)  NOT NULL,
    version          BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT pk_idempotency_record PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uq_idempotency_record_org_key
    ON idempotency_record (organization_id, idempotency_key);
CREATE INDEX ix_idempotency_record_expires ON idempotency_record (expires_at);

GRANT SELECT, INSERT, UPDATE, DELETE ON budget TO pams_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON purchase_request TO pams_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON purchase_request_line TO pams_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON request_attachment TO pams_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON idempotency_record TO pams_app;

ALTER TABLE budget ENABLE ROW LEVEL SECURITY;
ALTER TABLE budget FORCE ROW LEVEL SECURITY;
CREATE POLICY budget_tenant_isolation ON budget
    USING (organization_id = NULLIF(current_setting('app.current_org', true), '')::uuid)
    WITH CHECK (organization_id = NULLIF(current_setting('app.current_org', true), '')::uuid);

ALTER TABLE purchase_request ENABLE ROW LEVEL SECURITY;
ALTER TABLE purchase_request FORCE ROW LEVEL SECURITY;
CREATE POLICY purchase_request_tenant_isolation ON purchase_request
    USING (organization_id = NULLIF(current_setting('app.current_org', true), '')::uuid)
    WITH CHECK (organization_id = NULLIF(current_setting('app.current_org', true), '')::uuid);

ALTER TABLE purchase_request_line ENABLE ROW LEVEL SECURITY;
ALTER TABLE purchase_request_line FORCE ROW LEVEL SECURITY;
CREATE POLICY purchase_request_line_tenant_isolation ON purchase_request_line
    USING (organization_id = NULLIF(current_setting('app.current_org', true), '')::uuid)
    WITH CHECK (organization_id = NULLIF(current_setting('app.current_org', true), '')::uuid);

ALTER TABLE request_attachment ENABLE ROW LEVEL SECURITY;
ALTER TABLE request_attachment FORCE ROW LEVEL SECURITY;
CREATE POLICY request_attachment_tenant_isolation ON request_attachment
    USING (organization_id = NULLIF(current_setting('app.current_org', true), '')::uuid)
    WITH CHECK (organization_id = NULLIF(current_setting('app.current_org', true), '')::uuid);

ALTER TABLE idempotency_record ENABLE ROW LEVEL SECURITY;
ALTER TABLE idempotency_record FORCE ROW LEVEL SECURITY;
CREATE POLICY idempotency_record_tenant_isolation ON idempotency_record
    USING (organization_id = NULLIF(current_setting('app.current_org', true), '')::uuid)
    WITH CHECK (organization_id = NULLIF(current_setting('app.current_org', true), '')::uuid);
