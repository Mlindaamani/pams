ALTER TABLE tenant_branding
    ADD COLUMN request_label VARCHAR(40) NOT NULL DEFAULT 'Requests',
    ADD COLUMN order_label VARCHAR(40) NOT NULL DEFAULT 'Purchase orders',
    ADD COLUMN vendor_label VARCHAR(40) NOT NULL DEFAULT 'Vendors',
    ADD COLUMN budget_label VARCHAR(40) NOT NULL DEFAULT 'Budgets',
    ADD COLUMN catalog_label VARCHAR(40) NOT NULL DEFAULT 'Catalog items';
