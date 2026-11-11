-- Minimum approvals required to complete a stage. Existing stages default to all approvers.
ALTER TABLE approval_policy_step
    ADD COLUMN min_approvals INTEGER NOT NULL DEFAULT 1;

UPDATE approval_policy_step target
   SET min_approvals = (
       SELECT count(*)
         FROM approval_policy_step peer
        WHERE peer.policy_id = target.policy_id
          AND peer.sequence = target.sequence
   );

ALTER TABLE approval_policy_step
    ADD CONSTRAINT ck_approval_policy_step_min_approvals CHECK (min_approvals >= 1);

ALTER TABLE approval_step
    ADD COLUMN min_approvals INTEGER NOT NULL DEFAULT 1;

UPDATE approval_step target
   SET min_approvals = (
       SELECT count(*)
         FROM approval_step peer
        WHERE peer.instance_id = target.instance_id
          AND peer.sequence = target.sequence
   );

ALTER TABLE approval_step
    ADD CONSTRAINT ck_approval_step_min_approvals CHECK (min_approvals >= 1);

ALTER TABLE purchase_order_approval_step
    ADD COLUMN min_approvals INTEGER NOT NULL DEFAULT 1;

UPDATE purchase_order_approval_step target
   SET min_approvals = (
       SELECT count(*)
         FROM purchase_order_approval_step peer
        WHERE peer.approval_id = target.approval_id
          AND peer.sequence = target.sequence
   );

ALTER TABLE purchase_order_approval_step
    ADD CONSTRAINT ck_po_approval_step_min_approvals CHECK (min_approvals >= 1);
