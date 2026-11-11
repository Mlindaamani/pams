-- Starter catalog items for common department purchasing.

WITH items(sku, name, unit, price, category) AS (VALUES
    ('SYS-ICT-MON-24', '24-inch Computer Monitor', 'EA', 350000, 'ICT Equipment'),
    ('SYS-ICT-MON-27', '27-inch Computer Monitor', 'EA', 520000, 'ICT Equipment'),
    ('SYS-ICT-MOU-WRL', 'Wireless Mouse', 'EA', 25000, 'ICT Accessories'),
    ('SYS-ICT-MOU-USB', 'USB Mouse', 'EA', 12000, 'ICT Accessories'),
    ('SYS-ICT-KEY-WRL', 'Wireless Keyboard', 'EA', 45000, 'ICT Accessories'),
    ('SYS-ICT-KEY-USB', 'USB Keyboard', 'EA', 25000, 'ICT Accessories'),
    ('SYS-ICT-KEYMOU', 'Keyboard and Mouse Combo', 'Set', 60000, 'ICT Accessories'),
    ('SYS-ICT-LAP-STD', 'Standard Business Laptop', 'EA', 2500000, 'ICT Equipment'),
    ('SYS-ICT-DOCK-USBC', 'USB-C Docking Station', 'EA', 450000, 'ICT Equipment'),
    ('SYS-ICT-HDD-1TB', '1TB External Hard Drive', 'EA', 180000, 'ICT Storage'),
    ('SYS-ICT-SSD-512', '512GB Portable SSD', 'EA', 220000, 'ICT Storage'),
    ('SYS-ICT-HDMI-2M', 'HDMI Cable 2m', 'EA', 15000, 'ICT Accessories'),
    ('SYS-ICT-USBC-2M', 'USB-C Cable 2m', 'EA', 20000, 'ICT Accessories'),
    ('SYS-ICT-HEADSET', 'USB Headset with Microphone', 'EA', 90000, 'ICT Accessories'),
    ('SYS-ICT-WEBCAM', 'HD Webcam', 'EA', 120000, 'ICT Equipment'),
    ('SYS-ICT-PRINTER', 'Network Laser Printer', 'EA', 950000, 'ICT Equipment'),
    ('SYS-ICT-TONER', 'Printer Toner Cartridge', 'EA', 180000, 'ICT Consumables'),
    ('SYS-ICT-UPS', 'UPS 650VA', 'EA', 250000, 'ICT Equipment'),
    ('SYS-ICT-POWER', 'Surge Protector Power Strip', 'EA', 35000, 'ICT Accessories'),
    ('SYS-ICT-SWITCH', '8-Port Network Switch', 'EA', 180000, 'ICT Network'),
    ('SYS-ICT-WIFI', 'Wi-Fi Router', 'EA', 160000, 'ICT Network'),
    ('SYS-ICT-PROJ', 'Projector', 'EA', 1200000, 'ICT Equipment'),
    ('SYS-ICT-PHONE', 'IP Desk Phone', 'EA', 220000, 'ICT Equipment'),
    ('SYS-ICT-TAB', 'Tablet', 'EA', 800000, 'ICT Equipment'),
    ('SYS-ICT-LIC-365', 'Microsoft 365 License (Annual)', 'License', 350000, 'Software'),
    ('SYS-ICT-ANTIVIRUS', 'Antivirus License (Annual)', 'License', 120000, 'Software'),
    ('SYS-ICT-CLOUD', 'Cloud Backup Subscription (Annual)', 'Subscription', 200000, 'Software'),
    ('SYS-ICT-TEAM', 'Team Collaboration License (Annual)', 'License', 180000, 'Software')
)
INSERT INTO catalog_item
    (organization_id, sku, name, unit, default_price, currency, category, active,
     department_scope, created_at, created_by, updated_at, updated_by)
SELECT organization.id, items.sku, items.name, items.unit, items.price, 'TZS', items.category,
       TRUE, 'SELECTED', now(), 'catalog-seed', now(), 'catalog-seed'
  FROM organization
 CROSS JOIN items
 WHERE EXISTS (
     SELECT 1 FROM department d
      WHERE d.organization_id = organization.id
        AND d.deleted_at IS NULL
        AND lower(d.name) = 'ict'
 )
ON CONFLICT (organization_id, sku) WHERE deleted_at IS NULL DO NOTHING;

WITH items(sku, name, unit, price, category) AS (VALUES
    ('SYS-FIN-CALC', 'Financial Calculator', 'EA', 60000, 'Finance Equipment'),
    ('SYS-FIN-SCANNER', 'Document Scanner', 'EA', 450000, 'Finance Equipment'),
    ('SYS-FIN-SHREDDER', 'Paper Shredder', 'EA', 350000, 'Finance Equipment'),
    ('SYS-FIN-SAFE', 'Document Safe', 'EA', 700000, 'Finance Equipment'),
    ('SYS-FIN-STAMP', 'Company Stamp', 'EA', 30000, 'Finance Supplies'),
    ('SYS-FIN-ARCHIVE', 'Archive Box (Pack of 10)', 'Pack of 10', 45000, 'Finance Supplies'),
    ('SYS-FIN-PAPER', 'A4 Copy Paper (Pack of 5 Reams)', 'Pack of 5', 35000, 'Office Supplies'),
    ('SYS-FIN-ENVELOPE', 'Envelopes (Pack of 100)', 'Pack of 100', 25000, 'Office Supplies'),
    ('SYS-FIN-CABINET', 'Filing Cabinet', 'EA', 450000, 'Finance Furniture'),
    ('SYS-FIN-SOFT', 'Accounting Software License (Annual)', 'License', 900000, 'Software'),
    ('SYS-FIN-BINDER', 'Ring Binders (Pack of 10)', 'Pack of 10', 30000, 'Finance Supplies'),
    ('SYS-FIN-TONER', 'Finance Printer Toner', 'EA', 180000, 'Finance Consumables')
)
INSERT INTO catalog_item
    (organization_id, sku, name, unit, default_price, currency, category, active,
     department_scope, created_at, created_by, updated_at, updated_by)
SELECT organization.id, items.sku, items.name, items.unit, items.price, 'TZS', items.category,
       TRUE, 'SELECTED', now(), 'catalog-seed', now(), 'catalog-seed'
  FROM organization
 CROSS JOIN items
 WHERE EXISTS (
     SELECT 1 FROM department d
      WHERE d.organization_id = organization.id
        AND d.deleted_at IS NULL
        AND lower(d.name) = 'finance'
 )
ON CONFLICT (organization_id, sku) WHERE deleted_at IS NULL DO NOTHING;

WITH items(sku, name, unit, price, category) AS (VALUES
    ('SYS-GEN-CHAIR', 'Office Chair', 'EA', 250000, 'Furniture'),
    ('SYS-GEN-DESK', 'Office Desk', 'EA', 600000, 'Furniture'),
    ('SYS-GEN-PAPER', 'A4 Copy Paper (Pack of 5 Reams)', 'Pack of 5', 35000, 'Office Supplies'),
    ('SYS-GEN-PEN', 'Ballpoint Pens (Pack of 50)', 'Pack of 50', 15000, 'Office Supplies'),
    ('SYS-GEN-NOTEBOOK', 'Notebooks (Pack of 10)', 'Pack of 10', 20000, 'Office Supplies'),
    ('SYS-GEN-WATER', 'Drinking Water (20L Bottle)', 'Bottle', 8000, 'Pantry'),
    ('SYS-GEN-TISSUE', 'Tissue Boxes (Pack of 12)', 'Pack of 12', 30000, 'Cleaning'),
    ('SYS-GEN-CLEANER', 'Multi-surface Cleaner 5L', 'EA', 25000, 'Cleaning'),
    ('SYS-GEN-SOAP', 'Hand Soap 5L', 'EA', 20000, 'Cleaning'),
    ('SYS-GEN-TRASH', 'Trash Bin', 'EA', 25000, 'Cleaning'),
    ('SYS-GEN-FIRSTAID', 'First Aid Kit', 'EA', 80000, 'Safety'),
    ('SYS-GEN-LIGHT', 'LED Office Light', 'EA', 65000, 'Office Equipment'),
    ('SYS-GEN-EXT', 'Extension Cable 5m', 'EA', 20000, 'Office Equipment'),
    ('SYS-GEN-COFFEE', 'Coffee Supplies Pack', 'Pack', 45000, 'Pantry'),
    ('SYS-GEN-CUPS', 'Disposable Cups (Pack of 100)', 'Pack of 100', 12000, 'Pantry'),
    ('SYS-GEN-MARKER', 'Whiteboard Markers (Pack of 12)', 'Pack of 12', 18000, 'Office Supplies'),
    ('SYS-GEN-STAPLER', 'Heavy-duty Stapler', 'EA', 22000, 'Office Supplies'),
    ('SYS-GEN-FOLDERS', 'Document Folders (Pack of 20)', 'Pack of 20', 25000, 'Office Supplies'),
    ('SYS-GEN-WHITEBOARD', 'Whiteboard', 'EA', 180000, 'Office Equipment'),
    ('SYS-GEN-CLOCK', 'Wall Clock', 'EA', 45000, 'Office Equipment')
)
INSERT INTO catalog_item
    (organization_id, sku, name, unit, default_price, currency, category, active,
     department_scope, created_at, created_by, updated_at, updated_by)
SELECT organization.id, items.sku, items.name, items.unit, items.price, 'TZS', items.category,
       TRUE, 'ALL', now(), 'catalog-seed', now(), 'catalog-seed'
  FROM organization
 CROSS JOIN items
ON CONFLICT (organization_id, sku) WHERE deleted_at IS NULL DO NOTHING;

INSERT INTO catalog_item_department
    (organization_id, catalog_item_id, department_id, created_at, created_by)
SELECT ci.organization_id, ci.id, d.id, now(), 'catalog-seed'
  FROM catalog_item ci
  JOIN department d
    ON d.organization_id = ci.organization_id
   AND d.deleted_at IS NULL
 WHERE ci.sku LIKE 'SYS-ICT-%'
   AND lower(d.name) = 'ict'
ON CONFLICT DO NOTHING;

INSERT INTO catalog_item_department
    (organization_id, catalog_item_id, department_id, created_at, created_by)
SELECT ci.organization_id, ci.id, d.id, now(), 'catalog-seed'
  FROM catalog_item ci
  JOIN department d
    ON d.organization_id = ci.organization_id
   AND d.deleted_at IS NULL
 WHERE ci.sku LIKE 'SYS-FIN-%'
   AND lower(d.name) = 'finance'
ON CONFLICT DO NOTHING;
