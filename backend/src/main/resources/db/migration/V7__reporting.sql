-- Phase 6: reporting support.

ALTER TABLE approval_instance ADD COLUMN completed_at TIMESTAMPTZ;

CREATE INDEX ix_purchase_order_org_issued_at
    ON purchase_order (organization_id, issued_at)
    WHERE status IN ('ISSUED', 'PARTIALLY_RECEIVED', 'RECEIVED', 'CLOSED');

CREATE INDEX ix_approval_instance_org_completed
    ON approval_instance (organization_id, completed_at)
    WHERE status = 'APPROVED';
