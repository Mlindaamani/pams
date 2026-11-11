-- Enterprise tenant branding.

INSERT INTO subscription_feature
    (code, name, description, category, active, sort_order, created_at, created_by, updated_at, updated_by)
VALUES
    ('custom_branding', 'Custom branding',
     'Customize the workspace name, logo, colors and email sender identity.',
     'CORE', TRUE, 130, now(), 'system', now(), 'system')
ON CONFLICT (code) DO NOTHING;

INSERT INTO subscription_plan_feature (plan_code, feature_code)
SELECT 'ENTERPRISE', 'custom_branding'
WHERE EXISTS (SELECT 1 FROM subscription_plan WHERE code = 'ENTERPRISE')
ON CONFLICT DO NOTHING;

CREATE TABLE tenant_branding (
    organization_id       UUID         NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
    display_name          VARCHAR(200),
    logo_object_key       VARCHAR(300),
    logo_content_type     VARCHAR(100),
    primary_color         VARCHAR(7)   NOT NULL DEFAULT '#0B5CAD',
    secondary_color      VARCHAR(7)   NOT NULL DEFAULT '#00695C',
    email_from_name       VARCHAR(200),
    support_email         VARCHAR(320),
    created_at            TIMESTAMPTZ  NOT NULL,
    created_by            VARCHAR(100) NOT NULL,
    updated_at            TIMESTAMPTZ  NOT NULL,
    updated_by            VARCHAR(100) NOT NULL,
    version               BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_tenant_branding PRIMARY KEY (organization_id),
    CONSTRAINT ck_tenant_branding_primary_color CHECK (primary_color ~ '^#[0-9A-Fa-f]{6}$'),
    CONSTRAINT ck_tenant_branding_secondary_color CHECK (secondary_color ~ '^#[0-9A-Fa-f]{6}$')
);

ALTER TABLE tenant_branding ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_branding FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_branding_tenant_isolation ON tenant_branding
    USING (organization_id = NULLIF(current_setting('app.current_org', true), '')::uuid)
    WITH CHECK (organization_id = NULLIF(current_setting('app.current_org', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON tenant_branding TO pams_app;
