-- Milestone 6: signed webhook subscriptions and deliveries.

CREATE TABLE webhook_subscription (
    id               UUID          NOT NULL DEFAULT gen_random_uuid(),
    organization_id  UUID          NOT NULL REFERENCES organization (id),
    url              VARCHAR(1000) NOT NULL,
    description      VARCHAR(300),
    secret           VARCHAR(100)  NOT NULL,
    event_types      VARCHAR(500)  NOT NULL,
    active           BOOLEAN       NOT NULL DEFAULT TRUE,
    last_success_at  TIMESTAMPTZ,
    last_failure_at  TIMESTAMPTZ,
    created_at       TIMESTAMPTZ   NOT NULL,
    created_by       VARCHAR(100)  NOT NULL,
    updated_at       TIMESTAMPTZ   NOT NULL,
    updated_by       VARCHAR(100)  NOT NULL,
    version          BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT pk_webhook_subscription PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uq_webhook_subscription_url
    ON webhook_subscription (organization_id, lower(url));
CREATE INDEX ix_webhook_subscription_org_active
    ON webhook_subscription (organization_id, active);

CREATE TABLE webhook_delivery (
    id                 UUID          NOT NULL DEFAULT gen_random_uuid(),
    organization_id    UUID          NOT NULL REFERENCES organization (id),
    subscription_id    UUID          NOT NULL REFERENCES webhook_subscription (id) ON DELETE CASCADE,
    event_id           UUID          NOT NULL,
    event_type         VARCHAR(100)  NOT NULL,
    payload            JSONB         NOT NULL,
    status             VARCHAR(30)   NOT NULL,
    attempts           INTEGER       NOT NULL DEFAULT 0,
    next_attempt_at    TIMESTAMPTZ   NOT NULL,
    last_attempt_at    TIMESTAMPTZ,
    response_status    INTEGER,
    response_body      VARCHAR(2000),
    error_message      VARCHAR(1000),
    duration_ms        INTEGER,
    created_at         TIMESTAMPTZ   NOT NULL,
    created_by         VARCHAR(100)  NOT NULL,
    updated_at         TIMESTAMPTZ   NOT NULL,
    updated_by         VARCHAR(100)  NOT NULL,
    version            BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT pk_webhook_delivery PRIMARY KEY (id),
    CONSTRAINT ck_webhook_delivery_status
        CHECK (status IN ('PENDING', 'SUCCEEDED', 'DEAD_LETTER'))
);
CREATE UNIQUE INDEX uq_webhook_delivery_event
    ON webhook_delivery (subscription_id, event_id);
CREATE INDEX ix_webhook_delivery_due
    ON webhook_delivery (next_attempt_at)
    WHERE status = 'PENDING';
CREATE INDEX ix_webhook_delivery_subscription
    ON webhook_delivery (subscription_id, created_at DESC);

GRANT SELECT, INSERT, UPDATE, DELETE ON webhook_subscription TO pams_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON webhook_delivery TO pams_app;

-- The delivery poller processes all tenants, like outbox_event. API queries always filter by
-- organization_id and tenant isolation is covered by integration tests. Subscription secrets are
-- never returned after creation/rotation.
