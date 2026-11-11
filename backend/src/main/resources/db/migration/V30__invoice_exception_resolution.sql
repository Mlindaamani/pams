ALTER TABLE vendor_invoice
    ADD COLUMN resolution_note VARCHAR(1000),
    ADD COLUMN resolved_at TIMESTAMPTZ,
    ADD COLUMN resolved_by VARCHAR(200);

ALTER TABLE vendor_invoice
    DROP CONSTRAINT ck_vendor_invoice_status;

ALTER TABLE vendor_invoice
    ADD CONSTRAINT ck_vendor_invoice_status CHECK (status IN (
        'MATCHED', 'MISMATCHED', 'APPROVED', 'DISPUTED', 'REJECTED', 'VOID'));

DROP INDEX uq_vendor_invoice_number;
CREATE UNIQUE INDEX uq_vendor_invoice_vendor_number
    ON vendor_invoice (organization_id, vendor_id, lower(invoice_number))
    WHERE status NOT IN ('VOID', 'REJECTED');
