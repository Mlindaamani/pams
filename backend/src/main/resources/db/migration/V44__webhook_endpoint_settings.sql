-- Webhook receiver identity and per-endpoint delivery settings.

ALTER TABLE webhook_subscription
    ADD COLUMN name VARCHAR(200),
    ADD COLUMN signing_method VARCHAR(30) NOT NULL DEFAULT 'HMAC_SHA256',
    ADD COLUMN timeout_seconds INTEGER NOT NULL DEFAULT 10,
    ADD COLUMN max_attempts INTEGER NOT NULL DEFAULT 5;

UPDATE webhook_subscription
   SET name = COALESCE(
       NULLIF(description, ''),
       NULLIF(split_part(split_part(url, '//', 2), '/', 1), ''),
       'Webhook endpoint'
   )
 WHERE name IS NULL;

ALTER TABLE webhook_subscription ALTER COLUMN name SET NOT NULL;

ALTER TABLE webhook_subscription
    ADD CONSTRAINT ck_webhook_signing_method
        CHECK (signing_method IN ('HMAC_SHA256')),
    ADD CONSTRAINT ck_webhook_timeout
        CHECK (timeout_seconds BETWEEN 1 AND 60),
    ADD CONSTRAINT ck_webhook_max_attempts
        CHECK (max_attempts BETWEEN 1 AND 10);
