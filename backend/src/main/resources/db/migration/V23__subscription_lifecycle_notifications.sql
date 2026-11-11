-- Lifecycle notifications and expired trial state.

ALTER TABLE tenant_subscription
    ADD COLUMN trial_reminder_7d_sent_at TIMESTAMPTZ,
    ADD COLUMN trial_reminder_1d_sent_at TIMESTAMPTZ,
    ADD COLUMN trial_expired_notified_at TIMESTAMPTZ,
    ADD COLUMN payment_failed_notified_at TIMESTAMPTZ,
    ADD COLUMN canceled_notified_at TIMESTAMPTZ;

ALTER TABLE tenant_subscription
    DROP CONSTRAINT ck_tenant_subscription_status;

ALTER TABLE tenant_subscription
    ADD CONSTRAINT ck_tenant_subscription_status CHECK (status IN (
        'TRIALING', 'ACTIVE', 'PAST_DUE', 'UNPAID', 'CANCELED', 'INCOMPLETE', 'EXPIRED'));
