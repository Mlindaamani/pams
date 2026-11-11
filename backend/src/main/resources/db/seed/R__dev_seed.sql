-- Dev-only seed data. Loaded by the dev profile (spring.flyway.locations includes db/seed).
-- Row level security is already enabled by V3; set the tenant for this migration connection so
-- the inserts pass the policy. The demo organization id must match the organization_id attribute
-- of the Keycloak users in ops/keycloak/realm-export.json.
SET app.current_org = '11111111-1111-1111-1111-111111111111';

INSERT INTO organization (id, name, slug, created_at, created_by, updated_at, updated_by, version)
VALUES (
    '11111111-1111-1111-1111-111111111111',
    'Demo Organization',
    'demo-org',
    now(), 'system', now(), 'system', 0
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO department (id, organization_id, name, created_at, created_by, updated_at, updated_by, version)
VALUES (
    '22222222-2222-2222-2222-222222222222',
    '11111111-1111-1111-1111-111111111111',
    'General',
    now(), 'system', now(), 'system', 0
)
ON CONFLICT (id) DO NOTHING;

-- Demo approval policies (version 1). Amount bands are [min, max).
INSERT INTO approval_policy (id, organization_id, name, amount_min, amount_max, policy_version, active,
                             created_at, created_by, updated_at, updated_by, version)
VALUES
    ('aaaaaaaa-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111',
     'Standard purchases', 0, 1000, 1, TRUE, now(), 'system', now(), 'system', 0),
    ('aaaaaaaa-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111',
     'Mid-value purchases', 1000, 10000, 1, TRUE, now(), 'system', now(), 'system', 0),
    ('aaaaaaaa-0000-0000-0000-000000000003', '11111111-1111-1111-1111-111111111111',
     'High-value purchases', 10000, NULL, 1, TRUE, now(), 'system', now(), 'system', 0)
ON CONFLICT (id) DO NOTHING;

INSERT INTO approval_policy_step (id, organization_id, policy_id, sequence, name, approver_type,
                                  approver_role, sla_hours, created_at, created_by, updated_at,
                                  updated_by, version)
VALUES
    ('bbbbbbbb-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111',
     'aaaaaaaa-0000-0000-0000-000000000001', 1, 'Approver', 'ROLE', 'APPROVER', 48,
     now(), 'system', now(), 'system', 0),
    ('bbbbbbbb-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111',
     'aaaaaaaa-0000-0000-0000-000000000002', 1, 'Approver', 'ROLE', 'APPROVER', 24,
     now(), 'system', now(), 'system', 0),
    ('bbbbbbbb-0000-0000-0000-000000000003', '11111111-1111-1111-1111-111111111111',
     'aaaaaaaa-0000-0000-0000-000000000002', 2, 'Finance', 'ROLE', 'FINANCE', 24,
     now(), 'system', now(), 'system', 0),
    ('bbbbbbbb-0000-0000-0000-000000000004', '11111111-1111-1111-1111-111111111111',
     'aaaaaaaa-0000-0000-0000-000000000003', 1, 'Approver', 'ROLE', 'APPROVER', 24,
     now(), 'system', now(), 'system', 0),
    ('bbbbbbbb-0000-0000-0000-000000000005', '11111111-1111-1111-1111-111111111111',
     'aaaaaaaa-0000-0000-0000-000000000003', 2, 'Finance', 'ROLE', 'FINANCE', 24,
     now(), 'system', now(), 'system', 0),
    ('bbbbbbbb-0000-0000-0000-000000000006', '11111111-1111-1111-1111-111111111111',
     'aaaaaaaa-0000-0000-0000-000000000003', 3, 'Admin', 'ROLE', 'ADMIN', 24,
     now(), 'system', now(), 'system', 0)
ON CONFLICT (id) DO NOTHING;
