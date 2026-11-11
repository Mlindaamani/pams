-- Milestone 4: goods receipts against purchase orders.

ALTER TABLE purchase_order_line
    ADD COLUMN received_quantity NUMERIC(19, 4) NOT NULL DEFAULT 0;

ALTER TABLE purchase_order_line
    ADD CONSTRAINT ck_purchase_order_line_received
    CHECK (received_quantity >= 0 AND received_quantity <= quantity);

CREATE TABLE goods_receipt (
    id                      UUID          NOT NULL DEFAULT gen_random_uuid(),
    organization_id         UUID          NOT NULL REFERENCES organization (id),
    purchase_order_id       UUID          NOT NULL REFERENCES purchase_order (id),
    received_date           DATE          NOT NULL,
    delivery_note_reference VARCHAR(100),
    notes                   VARCHAR(1000),
    created_at              TIMESTAMPTZ   NOT NULL,
    created_by              VARCHAR(100)  NOT NULL,
    updated_at              TIMESTAMPTZ   NOT NULL,
    updated_by              VARCHAR(100)  NOT NULL,
    version                 BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT pk_goods_receipt PRIMARY KEY (id)
);
CREATE INDEX ix_goods_receipt_org_order
    ON goods_receipt (organization_id, purchase_order_id, received_date DESC);
CREATE INDEX ix_goods_receipt_order ON goods_receipt (purchase_order_id);

CREATE TABLE goods_receipt_line (
    id                     UUID           NOT NULL DEFAULT gen_random_uuid(),
    organization_id        UUID           NOT NULL REFERENCES organization (id),
    goods_receipt_id       UUID           NOT NULL REFERENCES goods_receipt (id) ON DELETE CASCADE,
    purchase_order_line_id UUID           NOT NULL REFERENCES purchase_order_line (id),
    description            VARCHAR(500)   NOT NULL,
    unit                   VARCHAR(20)    NOT NULL,
    quantity               NUMERIC(19, 4) NOT NULL,
    created_at             TIMESTAMPTZ    NOT NULL,
    created_by             VARCHAR(100)   NOT NULL,
    updated_at             TIMESTAMPTZ    NOT NULL,
    updated_by             VARCHAR(100)   NOT NULL,
    version                BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT pk_goods_receipt_line PRIMARY KEY (id),
    CONSTRAINT ck_goods_receipt_line_qty CHECK (quantity > 0)
);
CREATE INDEX ix_goods_receipt_line_receipt ON goods_receipt_line (goods_receipt_id);
CREATE INDEX ix_goods_receipt_line_order_line ON goods_receipt_line (purchase_order_line_id);

GRANT SELECT, INSERT, UPDATE, DELETE ON goods_receipt TO pams_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON goods_receipt_line TO pams_app;

ALTER TABLE goods_receipt ENABLE ROW LEVEL SECURITY;
ALTER TABLE goods_receipt FORCE ROW LEVEL SECURITY;
CREATE POLICY goods_receipt_tenant_isolation ON goods_receipt
    USING (organization_id = NULLIF(current_setting('app.current_org', true), '')::uuid)
    WITH CHECK (organization_id = NULLIF(current_setting('app.current_org', true), '')::uuid);

ALTER TABLE goods_receipt_line ENABLE ROW LEVEL SECURITY;
ALTER TABLE goods_receipt_line FORCE ROW LEVEL SECURITY;
CREATE POLICY goods_receipt_line_tenant_isolation ON goods_receipt_line
    USING (organization_id = NULLIF(current_setting('app.current_org', true), '')::uuid)
    WITH CHECK (organization_id = NULLIF(current_setting('app.current_org', true), '')::uuid);
