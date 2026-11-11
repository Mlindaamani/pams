CREATE TABLE demo_request (
    id              UUID         NOT NULL DEFAULT gen_random_uuid(),
    name            VARCHAR(200) NOT NULL,
    email           VARCHAR(320) NOT NULL,
    company         VARCHAR(200) NOT NULL,
    phone           VARCHAR(50),
    message         VARCHAR(2000),
    status          VARCHAR(30)  NOT NULL DEFAULT 'NEW',
    created_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_demo_request PRIMARY KEY (id),
    CONSTRAINT ck_demo_request_status CHECK (status IN ('NEW', 'CONTACTED', 'QUALIFIED', 'CLOSED'))
);
CREATE INDEX ix_demo_request_created ON demo_request (created_at DESC);
GRANT SELECT, INSERT, UPDATE, DELETE ON demo_request TO pams_app;
