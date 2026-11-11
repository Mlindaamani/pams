-- Phase 2 defense in depth: PostgreSQL row level security for tenant-owned tables.
-- The application sets app.current_org per connection (see TenantAwareDataSource);
-- an empty/unset value denies access because the cast is guarded by NULLIF.

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'pams_app') THEN
        CREATE ROLE pams_app NOLOGIN;
    END IF;
END $$;

GRANT USAGE ON SCHEMA public TO pams_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO pams_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO pams_app;

ALTER TABLE department ENABLE ROW LEVEL SECURITY;
ALTER TABLE department FORCE ROW LEVEL SECURITY;
CREATE POLICY department_tenant_isolation ON department
    USING (organization_id = NULLIF(current_setting('app.current_org', true), '')::uuid)
    WITH CHECK (organization_id = NULLIF(current_setting('app.current_org', true), '')::uuid);

ALTER TABLE user_profile ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_profile FORCE ROW LEVEL SECURITY;
CREATE POLICY user_profile_tenant_isolation ON user_profile
    USING (organization_id = NULLIF(current_setting('app.current_org', true), '')::uuid)
    WITH CHECK (organization_id = NULLIF(current_setting('app.current_org', true), '')::uuid);

ALTER TABLE vendor ENABLE ROW LEVEL SECURITY;
ALTER TABLE vendor FORCE ROW LEVEL SECURITY;
CREATE POLICY vendor_tenant_isolation ON vendor
    USING (organization_id = NULLIF(current_setting('app.current_org', true), '')::uuid)
    WITH CHECK (organization_id = NULLIF(current_setting('app.current_org', true), '')::uuid);

ALTER TABLE catalog_item ENABLE ROW LEVEL SECURITY;
ALTER TABLE catalog_item FORCE ROW LEVEL SECURITY;
CREATE POLICY catalog_item_tenant_isolation ON catalog_item
    USING (organization_id = NULLIF(current_setting('app.current_org', true), '')::uuid)
    WITH CHECK (organization_id = NULLIF(current_setting('app.current_org', true), '')::uuid);
