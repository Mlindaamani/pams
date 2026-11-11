-- Non-amount conditions for approval-policy steps.
ALTER TABLE approval_policy_step
    ADD COLUMN condition_type VARCHAR(30) NOT NULL DEFAULT 'ALWAYS',
    ADD COLUMN condition_department_id UUID REFERENCES department (id);

ALTER TABLE approval_policy_step
    ADD CONSTRAINT ck_approval_policy_step_condition
        CHECK (condition_type IN ('ALWAYS', 'BUDGET_EXCEPTION', 'DEPARTMENT'));

ALTER TABLE approval_policy_step
    ADD CONSTRAINT ck_approval_policy_step_department_condition
        CHECK (condition_type <> 'DEPARTMENT' OR condition_department_id IS NOT NULL);

CREATE INDEX ix_approval_policy_step_condition_department
    ON approval_policy_step (organization_id, condition_department_id)
    WHERE condition_department_id IS NOT NULL;
