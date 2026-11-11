-- Internal invoice references and numbering sequence.

CREATE TABLE invoice_sequence (
    organization_id UUID    NOT NULL REFERENCES organization (id),
    fiscal_year     INTEGER NOT NULL,
    next_value      BIGINT  NOT NULL,
    CONSTRAINT pk_invoice_sequence PRIMARY KEY (organization_id, fiscal_year)
);

ALTER TABLE vendor_invoice ADD COLUMN internal_reference VARCHAR(40);

WITH numbered AS (
    SELECT id,
           organization_id,
           EXTRACT(YEAR FROM invoice_date)::INTEGER AS invoice_year,
           ROW_NUMBER() OVER (
               PARTITION BY organization_id, EXTRACT(YEAR FROM invoice_date)
               ORDER BY created_at, id
           ) AS row_number
    FROM vendor_invoice
)
UPDATE vendor_invoice invoice
SET internal_reference =
    'INV-' || numbered.invoice_year || '-' || LPAD(numbered.row_number::TEXT, 6, '0')
FROM numbered
WHERE invoice.id = numbered.id;

INSERT INTO invoice_sequence (organization_id, fiscal_year, next_value)
SELECT organization_id,
       EXTRACT(YEAR FROM invoice_date)::INTEGER,
       MAX(SUBSTRING(internal_reference FROM 10)::BIGINT) + 1
FROM vendor_invoice
GROUP BY organization_id, EXTRACT(YEAR FROM invoice_date);

CREATE UNIQUE INDEX uq_vendor_invoice_internal_reference
    ON vendor_invoice (organization_id, internal_reference);

GRANT SELECT, INSERT, UPDATE, DELETE ON invoice_sequence TO pams_app;

ALTER TABLE invoice_sequence ENABLE ROW LEVEL SECURITY;
ALTER TABLE invoice_sequence FORCE ROW LEVEL SECURITY;
CREATE POLICY invoice_sequence_tenant_isolation ON invoice_sequence
    USING (organization_id = NULLIF(current_setting('app.current_org', true), '')::uuid)
    WITH CHECK (organization_id = NULLIF(current_setting('app.current_org', true), '')::uuid);
