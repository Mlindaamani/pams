-- Department audiences and personal favorites for catalog items.

ALTER TABLE catalog_item
    ADD COLUMN department_scope VARCHAR(20) NOT NULL DEFAULT 'ALL';

ALTER TABLE catalog_item
    ADD CONSTRAINT ck_catalog_item_department_scope
        CHECK (department_scope IN ('ALL', 'SELECTED'));

CREATE TABLE catalog_item_department (
    organization_id UUID        NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
    catalog_item_id UUID        NOT NULL REFERENCES catalog_item (id) ON DELETE CASCADE,
    department_id   UUID        NOT NULL REFERENCES department (id) ON DELETE CASCADE,
    created_at      TIMESTAMPTZ NOT NULL,
    created_by      VARCHAR(100) NOT NULL,
    CONSTRAINT pk_catalog_item_department PRIMARY KEY (catalog_item_id, department_id)
);

CREATE INDEX ix_catalog_item_department_department
    ON catalog_item_department (organization_id, department_id);

GRANT SELECT, INSERT, UPDATE, DELETE ON catalog_item_department TO pams_app;
ALTER TABLE catalog_item_department ENABLE ROW LEVEL SECURITY;
ALTER TABLE catalog_item_department FORCE ROW LEVEL SECURITY;
CREATE POLICY catalog_item_department_tenant_isolation ON catalog_item_department
    USING (organization_id = NULLIF(current_setting('app.current_org', true), '')::uuid)
    WITH CHECK (organization_id = NULLIF(current_setting('app.current_org', true), '')::uuid);

CREATE TABLE catalog_favorite (
    organization_id UUID        NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
    user_profile_id UUID        NOT NULL REFERENCES user_profile (id) ON DELETE CASCADE,
    catalog_item_id UUID        NOT NULL REFERENCES catalog_item (id) ON DELETE CASCADE,
    created_at      TIMESTAMPTZ NOT NULL,
    created_by      VARCHAR(100) NOT NULL,
    CONSTRAINT pk_catalog_favorite PRIMARY KEY (user_profile_id, catalog_item_id)
);

CREATE INDEX ix_catalog_favorite_item
    ON catalog_favorite (organization_id, catalog_item_id);

GRANT SELECT, INSERT, UPDATE, DELETE ON catalog_favorite TO pams_app;
ALTER TABLE catalog_favorite ENABLE ROW LEVEL SECURITY;
ALTER TABLE catalog_favorite FORCE ROW LEVEL SECURITY;
CREATE POLICY catalog_favorite_tenant_isolation ON catalog_favorite
    USING (organization_id = NULLIF(current_setting('app.current_org', true), '')::uuid)
    WITH CHECK (organization_id = NULLIF(current_setting('app.current_org', true), '')::uuid);
