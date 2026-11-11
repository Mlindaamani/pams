-- Milestone 5: vendor invoices and three-way matching.

ALTER TABLE organization
    ADD COLUMN invoice_tolerance_percent NUMERIC(5, 2) NOT NULL DEFAULT 2.00;

ALTER TABLE organization
    ADD CONSTRAINT ck_organization_invoice_tolerance
    CHECK (invoice_tolerance_percent >= 0 AND invoice_tolerance_percent <= 100);

CREATE TABLE vendor_invoice (
    id                      UUID           NOT NULL DEFAULT gen_random_uuid(),
    organization_id         UUID           NOT NULL REFERENCES organization (id),
    purchase_order_id       UUID           NOT NULL REFERENCES purchase_order (id),
    po_number               VARCHAR(40)    NOT NULL,
    vendor_id               UUID           NOT NULL REFERENCES vendor (id),
    invoice_number          VARCHAR(100)   NOT NULL,
    invoice_date            DATE           NOT NULL,
    due_date                DATE,
    currency                VARCHAR(3)     NOT NULL,
    subtotal                NUMERIC(19, 4) NOT NULL,
    tax_amount              NUMERIC(19, 4) NOT NULL,
    total_amount            NUMERIC(19, 4) NOT NULL,
    status                  VARCHAR(30)    NOT NULL,
    tolerance_percent       NUMERIC(5, 2)  NOT NULL,
    notes                   VARCHAR(1000),
    approved_at             TIMESTAMPTZ,
    approved_by             VARCHAR(200),
    created_at              TIMESTAMPTZ    NOT NULL,
    created_by              VARCHAR(100)   NOT NULL,
    updated_at              TIMESTAMPTZ    NOT NULL,
    updated_by              VARCHAR(100)   NOT NULL,
    version                 BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT pk_vendor_invoice PRIMARY KEY (id),
    CONSTRAINT ck_vendor_invoice_status
        CHECK (status IN ('MATCHED', 'MISMATCHED', 'APPROVED')),
    CONSTRAINT ck_vendor_invoice_amounts
        CHECK (subtotal >= 0 AND tax_amount >= 0 AND total_amount >= 0)
);
CREATE UNIQUE INDEX uq_vendor_invoice_number
    ON vendor_invoice (organization_id, lower(invoice_number));
CREATE INDEX ix_vendor_invoice_org_status
    ON vendor_invoice (organization_id, status, invoice_date DESC);
CREATE INDEX ix_vendor_invoice_org_po
    ON vendor_invoice (organization_id, purchase_order_id);
CREATE INDEX ix_vendor_invoice_vendor ON vendor_invoice (vendor_id);

CREATE TABLE vendor_invoice_line (
    id                     UUID           NOT NULL DEFAULT gen_random_uuid(),
    organization_id        UUID           NOT NULL REFERENCES organization (id),
    vendor_invoice_id      UUID           NOT NULL REFERENCES vendor_invoice (id) ON DELETE CASCADE,
    purchase_order_id      UUID           NOT NULL REFERENCES purchase_order (id),
    purchase_order_line_id UUID           NOT NULL REFERENCES purchase_order_line (id),
    description            VARCHAR(500)   NOT NULL,
    unit                   VARCHAR(20)    NOT NULL,
    quantity               NUMERIC(19, 4) NOT NULL,
    unit_price             NUMERIC(19, 4) NOT NULL,
    line_total             NUMERIC(19, 4) NOT NULL,
    matched                BOOLEAN        NOT NULL,
    price_variance_percent NUMERIC(9, 4)  NOT NULL,
    match_message          VARCHAR(500),
    created_at             TIMESTAMPTZ    NOT NULL,
    created_by             VARCHAR(100)   NOT NULL,
    updated_at             TIMESTAMPTZ    NOT NULL,
    updated_by             VARCHAR(100)   NOT NULL,
    version                BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT pk_vendor_invoice_line PRIMARY KEY (id),
    CONSTRAINT ck_vendor_invoice_line_qty CHECK (quantity > 0),
    CONSTRAINT ck_vendor_invoice_line_price CHECK (unit_price >= 0)
);
CREATE INDEX ix_vendor_invoice_line_invoice ON vendor_invoice_line (vendor_invoice_id);
CREATE INDEX ix_vendor_invoice_line_po_line ON vendor_invoice_line (purchase_order_line_id);

GRANT SELECT, INSERT, UPDATE, DELETE ON vendor_invoice TO pams_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON vendor_invoice_line TO pams_app;

ALTER TABLE vendor_invoice ENABLE ROW LEVEL SECURITY;
ALTER TABLE vendor_invoice FORCE ROW LEVEL SECURITY;
CREATE POLICY vendor_invoice_tenant_isolation ON vendor_invoice
    USING (organization_id = NULLIF(current_setting('app.current_org', true), '')::uuid)
    WITH CHECK (organization_id = NULLIF(current_setting('app.current_org', true), '')::uuid);

ALTER TABLE vendor_invoice_line ENABLE ROW LEVEL SECURITY;
ALTER TABLE vendor_invoice_line FORCE ROW LEVEL SECURITY;
CREATE POLICY vendor_invoice_line_tenant_isolation ON vendor_invoice_line
    USING (organization_id = NULLIF(current_setting('app.current_org', true), '')::uuid)
    WITH CHECK (organization_id = NULLIF(current_setting('app.current_org', true), '')::uuid);
