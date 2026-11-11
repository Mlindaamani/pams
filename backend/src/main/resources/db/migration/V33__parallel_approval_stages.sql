-- Allow multiple approvers in the same stage. The sequence column is the stage number.
DROP INDEX uq_approval_policy_step_sequence;
CREATE INDEX ix_approval_policy_step_policy_sequence
    ON approval_policy_step (policy_id, sequence);

DROP INDEX uq_approval_step_instance_sequence;
CREATE INDEX ix_approval_step_instance_sequence
    ON approval_step (instance_id, sequence);

DROP INDEX uq_purchase_order_approval_step_seq;
CREATE INDEX ix_purchase_order_approval_step_seq
    ON purchase_order_approval_step (approval_id, sequence);
