ALTER TABLE tenant_branding
    ADD COLUMN corner_radius INTEGER NOT NULL DEFAULT 10,
    ADD COLUMN font_family VARCHAR(30) NOT NULL DEFAULT 'INTER',
    ADD COLUMN ui_density VARCHAR(20) NOT NULL DEFAULT 'COMFORTABLE';

ALTER TABLE tenant_branding
    ADD CONSTRAINT ck_tenant_branding_corner_radius
        CHECK (corner_radius BETWEEN 0 AND 24),
    ADD CONSTRAINT ck_tenant_branding_font_family
        CHECK (font_family IN ('INTER', 'ROBOTO', 'SYSTEM', 'SERIF')),
    ADD CONSTRAINT ck_tenant_branding_ui_density
        CHECK (ui_density IN ('COMFORTABLE', 'COMPACT'));
