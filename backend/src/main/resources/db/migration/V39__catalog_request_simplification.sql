-- Catalog default vendor, request line snapshots and non-catalog review.

ALTER TABLE catalog_item
    ADD COLUMN default_vendor_id UUID REFERENCES vendor (id);

CREATE INDEX ix_catalog_item_default_vendor
    ON catalog_item (organization_id, default_vendor_id)
    WHERE default_vendor_id IS NOT NULL;

ALTER TABLE purchase_request_line
    ADD COLUMN vendor_id UUID REFERENCES vendor (id),
    ADD COLUMN is_non_catalog BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN notes VARCHAR(500),
    ADD COLUMN catalog_review_status VARCHAR(20) NOT NULL DEFAULT 'NOT_REQUIRED';

ALTER TABLE purchase_request_line
    ADD CONSTRAINT ck_purchase_request_line_catalog_review
        CHECK (catalog_review_status IN ('NOT_REQUIRED', 'PENDING', 'ADDED', 'DISMISSED'));

CREATE INDEX ix_purchase_request_line_non_catalog_review
    ON purchase_request_line (organization_id, is_non_catalog, catalog_review_status);

CREATE INDEX ix_purchase_request_line_vendor
    ON purchase_request_line (organization_id, vendor_id)
    WHERE vendor_id IS NOT NULL;

INSERT INTO permission (code, description) VALUES
    ('purchase_request:create_for_department', 'Create purchase requests for another department')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permission (role_name, permission_code) VALUES
    ('ADMIN', 'purchase_request:create_for_department'),
    ('PROCUREMENT_OFFICER', 'purchase_request:create_for_department')
ON CONFLICT DO NOTHING;
