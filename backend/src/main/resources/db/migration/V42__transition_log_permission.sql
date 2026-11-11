-- Separate request transition-log access from the global audit log permission.

INSERT INTO permission (code, description) VALUES
    ('audit:transition_read', 'View transition logs on requests they can access')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permission (role_name, permission_code) VALUES
    ('ADMIN', 'audit:transition_read'),
    ('REQUESTER', 'audit:transition_read'),
    ('APPROVER', 'audit:transition_read'),
    ('FINANCE', 'audit:transition_read'),
    ('PROCUREMENT_OFFICER', 'audit:transition_read'),
    ('AUDITOR', 'audit:transition_read')
ON CONFLICT DO NOTHING;
