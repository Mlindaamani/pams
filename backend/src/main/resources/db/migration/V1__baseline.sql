-- Phase 0 baseline: tenant root.
-- Tenant-owned tables added later carry organization_id with an index and (later) RLS.
CREATE TABLE organization (
    id           UUID         NOT NULL DEFAULT gen_random_uuid(),
    name         VARCHAR(200) NOT NULL,
    slug         VARCHAR(100) NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    created_by   VARCHAR(100) NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL,
    updated_by   VARCHAR(100) NOT NULL,
    version      BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_organization PRIMARY KEY (id),
    CONSTRAINT uq_organization_slug UNIQUE (slug)
);
