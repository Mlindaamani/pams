-- Preserve approval history when a policy version is deleted.

ALTER TABLE approval_policy ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE approval_policy ADD COLUMN deleted_by VARCHAR(100);

CREATE INDEX ix_approval_policy_org_deleted
    ON approval_policy (organization_id, deleted_at);
