-- Phase 1 reference data: departments, user profiles, vendors, catalog items.

CREATE TABLE department (
    id              UUID         NOT NULL DEFAULT gen_random_uuid(),
    organization_id UUID         NOT NULL REFERENCES organization (id),
    name            VARCHAR(200) NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    created_by      VARCHAR(100) NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    updated_by      VARCHAR(100) NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    deleted_at      TIMESTAMPTZ,
    deleted_by      VARCHAR(100),
    CONSTRAINT pk_department PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uq_department_org_name
    ON department (organization_id, lower(name))
    WHERE deleted_at IS NULL;
CREATE INDEX ix_department_org ON department (organization_id);
CREATE INDEX ix_department_org_created_at ON department (organization_id, created_at DESC);

CREATE TABLE user_profile (
    id                     UUID         NOT NULL DEFAULT gen_random_uuid(),
    organization_id        UUID         NOT NULL REFERENCES organization (id),
    keycloak_subject       VARCHAR(200) NOT NULL,
    email                  VARCHAR(320) NOT NULL,
    display_name           VARCHAR(200) NOT NULL,
    department_id          UUID         NOT NULL REFERENCES department (id),
    manager_id             UUID         REFERENCES user_profile (id),
    approval_limit_amount  NUMERIC(19, 4),
    approval_limit_currency VARCHAR(3),
    active                 BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at             TIMESTAMPTZ  NOT NULL,
    created_by             VARCHAR(100) NOT NULL,
    updated_at             TIMESTAMPTZ  NOT NULL,
    updated_by             VARCHAR(100) NOT NULL,
    version                BIGINT       NOT NULL DEFAULT 0,
    deleted_at             TIMESTAMPTZ,
    deleted_by             VARCHAR(100),
    CONSTRAINT pk_user_profile PRIMARY KEY (id),
    CONSTRAINT ck_user_profile_approval_limit
        CHECK (approval_limit_amount IS NULL OR approval_limit_amount >= 0),
    CONSTRAINT ck_user_profile_approval_limit_currency
        CHECK ((approval_limit_amount IS NULL) = (approval_limit_currency IS NULL))
);
CREATE UNIQUE INDEX uq_user_profile_org_subject
    ON user_profile (organization_id, keycloak_subject)
    WHERE deleted_at IS NULL;
CREATE INDEX ix_user_profile_org ON user_profile (organization_id);
CREATE INDEX ix_user_profile_department ON user_profile (department_id);
CREATE INDEX ix_user_profile_manager ON user_profile (manager_id);
CREATE INDEX ix_user_profile_org_created_at ON user_profile (organization_id, created_at DESC);

CREATE TABLE vendor (
    id                  UUID         NOT NULL DEFAULT gen_random_uuid(),
    organization_id     UUID         NOT NULL REFERENCES organization (id),
    name                VARCHAR(200) NOT NULL,
    tax_id              VARCHAR(50)  NOT NULL,
    contact_name        VARCHAR(200),
    contact_email       VARCHAR(320),
    contact_phone       VARCHAR(50),
    address_line        VARCHAR(500),
    bank_name           VARCHAR(200),
    bank_account_name   VARCHAR(200),
    bank_account_number VARCHAR(100),
    status              VARCHAR(20)  NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL,
    created_by          VARCHAR(100) NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    updated_by          VARCHAR(100) NOT NULL,
    version             BIGINT       NOT NULL DEFAULT 0,
    deleted_at          TIMESTAMPTZ,
    deleted_by          VARCHAR(100),
    CONSTRAINT pk_vendor PRIMARY KEY (id),
    CONSTRAINT ck_vendor_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'BLACKLISTED'))
);
CREATE UNIQUE INDEX uq_vendor_org_tax_id
    ON vendor (organization_id, tax_id)
    WHERE deleted_at IS NULL;
CREATE INDEX ix_vendor_org ON vendor (organization_id);
CREATE INDEX ix_vendor_org_status ON vendor (organization_id, status);
CREATE INDEX ix_vendor_org_created_at ON vendor (organization_id, created_at DESC);
CREATE INDEX ix_vendor_org_name ON vendor (organization_id, lower(name));

CREATE TABLE catalog_item (
    id              UUID           NOT NULL DEFAULT gen_random_uuid(),
    organization_id UUID           NOT NULL REFERENCES organization (id),
    sku             VARCHAR(100)   NOT NULL,
    name            VARCHAR(200)   NOT NULL,
    unit            VARCHAR(20)    NOT NULL,
    default_price   NUMERIC(19, 4) NOT NULL,
    currency        VARCHAR(3)     NOT NULL,
    category        VARCHAR(100),
    active          BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ    NOT NULL,
    created_by      VARCHAR(100)   NOT NULL,
    updated_at      TIMESTAMPTZ    NOT NULL,
    updated_by      VARCHAR(100)   NOT NULL,
    version         BIGINT         NOT NULL DEFAULT 0,
    deleted_at      TIMESTAMPTZ,
    deleted_by      VARCHAR(100),
    CONSTRAINT pk_catalog_item PRIMARY KEY (id),
    CONSTRAINT ck_catalog_item_price CHECK (default_price >= 0)
);
CREATE UNIQUE INDEX uq_catalog_item_org_sku
    ON catalog_item (organization_id, sku)
    WHERE deleted_at IS NULL;
CREATE INDEX ix_catalog_item_org ON catalog_item (organization_id);
CREATE INDEX ix_catalog_item_org_category ON catalog_item (organization_id, category);
CREATE INDEX ix_catalog_item_org_active ON catalog_item (organization_id, active);
CREATE INDEX ix_catalog_item_org_created_at ON catalog_item (organization_id, created_at DESC);
