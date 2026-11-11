-- Platform administration: tenant lifecycle, platform permissions and audit.

ALTER TABLE organization
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE organization
    ADD CONSTRAINT ck_organization_status CHECK (status IN ('ACTIVE', 'SUSPENDED'));
ALTER TABLE organization ADD COLUMN suspended_at TIMESTAMPTZ;
ALTER TABLE organization ADD COLUMN suspended_by VARCHAR(200);

CREATE INDEX ix_organization_status ON organization (status);

INSERT INTO permission (code, description) VALUES
    ('platform:tenant_read', 'View all tenants'),
    ('platform:tenant_manage', 'Create, edit, suspend and activate tenants'),
    ('platform:analytics_read', 'View platform-wide analytics');

INSERT INTO role_model (role_name, display_name, description) VALUES
    ('PLATFORM_ADMIN', 'Platform administrator',
     'Manages tenants and views cross-tenant platform analytics.');

INSERT INTO role_permission (role_name, permission_code) VALUES
    ('PLATFORM_ADMIN', 'platform:tenant_read'),
    ('PLATFORM_ADMIN', 'platform:tenant_manage'),
    ('PLATFORM_ADMIN', 'platform:analytics_read'),
    ('ADMIN', 'platform:tenant_read'),
    ('ADMIN', 'platform:tenant_manage'),
    ('ADMIN', 'platform:analytics_read');

CREATE TABLE platform_audit_event (
    id              UUID         NOT NULL DEFAULT gen_random_uuid(),
    actor_subject   VARCHAR(200) NOT NULL,
    actor_name      VARCHAR(200) NOT NULL,
    action          VARCHAR(100) NOT NULL,
    tenant_id       UUID,
    payload         JSONB        NOT NULL,
    occurred_at     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_platform_audit_event PRIMARY KEY (id)
);
CREATE INDEX ix_platform_audit_tenant ON platform_audit_event (tenant_id, occurred_at DESC);
CREATE INDEX ix_platform_audit_occurred ON platform_audit_event (occurred_at DESC);
GRANT SELECT, INSERT ON platform_audit_event TO pams_app;
