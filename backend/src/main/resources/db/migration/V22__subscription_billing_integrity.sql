-- Make subscription billing source and commercial terms explicit.

ALTER TABLE tenant_subscription
    ADD COLUMN billing_source VARCHAR(20) NOT NULL DEFAULT 'TRIAL',
    ADD COLUMN monthly_price_snapshot NUMERIC(19, 4),
    ADD COLUMN annual_price_snapshot NUMERIC(19, 4),
    ADD COLUMN currency_snapshot VARCHAR(3),
    ADD COLUMN stripe_price_id VARCHAR(100),
    ADD COLUMN last_stripe_sync_at TIMESTAMPTZ;

ALTER TABLE tenant_subscription
    ADD CONSTRAINT ck_tenant_subscription_billing_source
        CHECK (billing_source IN ('TRIAL', 'STRIPE', 'MANUAL'));

UPDATE tenant_subscription ts
   SET billing_source = CASE
           WHEN ts.stripe_subscription_id IS NOT NULL THEN 'STRIPE'
           WHEN ts.status = 'TRIALING' THEN 'TRIAL'
           ELSE 'MANUAL'
       END,
       monthly_price_snapshot = p.monthly_price,
       annual_price_snapshot = p.annual_price,
       currency_snapshot = p.currency,
       stripe_price_id = CASE
           WHEN ts.billing_interval = 'ANNUAL' THEN p.stripe_price_annual
           ELSE p.stripe_price_monthly
       END,
       last_stripe_sync_at = CASE
           WHEN ts.stripe_subscription_id IS NOT NULL THEN now()
           ELSE NULL
       END
  FROM subscription_plan p
 WHERE p.code = ts.plan_code;

ALTER TABLE tenant_subscription
    ALTER COLUMN monthly_price_snapshot SET NOT NULL,
    ALTER COLUMN annual_price_snapshot SET NOT NULL,
    ALTER COLUMN currency_snapshot SET NOT NULL;

CREATE INDEX ix_tenant_subscription_billing_source
    ON tenant_subscription (billing_source, status);
