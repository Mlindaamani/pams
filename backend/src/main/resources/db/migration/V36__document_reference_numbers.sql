-- Human-readable, tenant/year-scoped reference numbers for core documents.

CREATE TABLE document_sequence (
    organization_id UUID        NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
    document_type   VARCHAR(30) NOT NULL,
    document_year   INTEGER     NOT NULL,
    next_value      BIGINT      NOT NULL DEFAULT 1,
    CONSTRAINT pk_document_sequence PRIMARY KEY (organization_id, document_type, document_year)
);

GRANT SELECT, INSERT, UPDATE, DELETE ON document_sequence TO pams_app;

ALTER TABLE document_sequence ENABLE ROW LEVEL SECURITY;
ALTER TABLE document_sequence FORCE ROW LEVEL SECURITY;
CREATE POLICY document_sequence_tenant_isolation ON document_sequence
    USING (organization_id = NULLIF(current_setting('app.current_org', true), '')::uuid)
    WITH CHECK (organization_id = NULLIF(current_setting('app.current_org', true), '')::uuid);

ALTER TABLE purchase_request ADD COLUMN request_number VARCHAR(40);
WITH numbered AS (
    SELECT id,
           organization_id,
           EXTRACT(YEAR FROM created_at)::int AS document_year,
           ROW_NUMBER() OVER (
               PARTITION BY organization_id, EXTRACT(YEAR FROM created_at)
               ORDER BY created_at, id
           ) AS row_number
      FROM purchase_request
)
UPDATE purchase_request request
   SET request_number = 'PR-' || numbered.document_year || '-' ||
                        LPAD(numbered.row_number::text, 6, '0')
  FROM numbered
 WHERE request.id = numbered.id;
ALTER TABLE purchase_request ALTER COLUMN request_number SET NOT NULL;
CREATE UNIQUE INDEX uq_purchase_request_number
    ON purchase_request (organization_id, request_number);
CREATE INDEX ix_purchase_request_number_search
    ON purchase_request (organization_id, lower(request_number));

ALTER TABLE budget ADD COLUMN budget_number VARCHAR(40);
WITH numbered AS (
    SELECT id,
           organization_id,
           fiscal_year AS document_year,
           ROW_NUMBER() OVER (
               PARTITION BY organization_id, fiscal_year
               ORDER BY created_at, id
           ) AS row_number
      FROM budget
)
UPDATE budget budget_row
   SET budget_number = 'BUD-' || numbered.document_year || '-' ||
                       LPAD(numbered.row_number::text, 6, '0')
  FROM numbered
 WHERE budget_row.id = numbered.id;
ALTER TABLE budget ALTER COLUMN budget_number SET NOT NULL;
CREATE UNIQUE INDEX uq_budget_number ON budget (organization_id, budget_number);

ALTER TABLE goods_receipt ADD COLUMN receipt_number VARCHAR(40);
WITH numbered AS (
    SELECT id,
           organization_id,
           EXTRACT(YEAR FROM received_date)::int AS document_year,
           ROW_NUMBER() OVER (
               PARTITION BY organization_id, EXTRACT(YEAR FROM received_date)
               ORDER BY created_at, id
           ) AS row_number
      FROM goods_receipt
)
UPDATE goods_receipt receipt
   SET receipt_number = 'GRN-' || numbered.document_year || '-' ||
                        LPAD(numbered.row_number::text, 6, '0')
  FROM numbered
 WHERE receipt.id = numbered.id;
ALTER TABLE goods_receipt ALTER COLUMN receipt_number SET NOT NULL;
CREATE UNIQUE INDEX uq_goods_receipt_number
    ON goods_receipt (organization_id, receipt_number);

ALTER TABLE vendor_payment ADD COLUMN payment_number VARCHAR(40);
WITH numbered AS (
    SELECT id,
           organization_id,
           EXTRACT(YEAR FROM payment_date)::int AS document_year,
           ROW_NUMBER() OVER (
               PARTITION BY organization_id, EXTRACT(YEAR FROM payment_date)
               ORDER BY created_at, id
           ) AS row_number
      FROM vendor_payment
)
UPDATE vendor_payment payment
   SET payment_number = 'PAY-' || numbered.document_year || '-' ||
                        LPAD(numbered.row_number::text, 6, '0')
  FROM numbered
 WHERE payment.id = numbered.id;
ALTER TABLE vendor_payment ALTER COLUMN payment_number SET NOT NULL;
CREATE UNIQUE INDEX uq_vendor_payment_number
    ON vendor_payment (organization_id, payment_number);

INSERT INTO document_sequence (organization_id, document_type, document_year, next_value)
SELECT organization_id, 'PURCHASE_REQUEST', SUBSTRING(request_number FROM 4 FOR 4)::int,
       MAX(SUBSTRING(request_number FROM 9)::bigint) + 1
  FROM purchase_request
 GROUP BY organization_id, SUBSTRING(request_number FROM 4 FOR 4);

INSERT INTO document_sequence (organization_id, document_type, document_year, next_value)
SELECT organization_id, 'BUDGET', SUBSTRING(budget_number FROM 5 FOR 4)::int,
       MAX(SUBSTRING(budget_number FROM 10)::bigint) + 1
  FROM budget
 GROUP BY organization_id, SUBSTRING(budget_number FROM 5 FOR 4);

INSERT INTO document_sequence (organization_id, document_type, document_year, next_value)
SELECT organization_id, 'GOODS_RECEIPT', SUBSTRING(receipt_number FROM 5 FOR 4)::int,
       MAX(SUBSTRING(receipt_number FROM 10)::bigint) + 1
  FROM goods_receipt
 GROUP BY organization_id, SUBSTRING(receipt_number FROM 5 FOR 4);

INSERT INTO document_sequence (organization_id, document_type, document_year, next_value)
SELECT organization_id, 'VENDOR_PAYMENT', SUBSTRING(payment_number FROM 5 FOR 4)::int,
       MAX(SUBSTRING(payment_number FROM 10)::bigint) + 1
  FROM vendor_payment
 GROUP BY organization_id, SUBSTRING(payment_number FROM 5 FOR 4);
