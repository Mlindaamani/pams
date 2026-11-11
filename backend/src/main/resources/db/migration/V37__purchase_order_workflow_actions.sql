-- Purchase-order workflow statuses and configurable action rules.

INSERT INTO workflow_status (entity_type, status_code, display_name, sort_order, terminal) VALUES
    ('purchase_order', 'DRAFT', 'Draft', 10, FALSE),
    ('purchase_order', 'PENDING_APPROVAL', 'Pending approval', 20, FALSE),
    ('purchase_order', 'APPROVED', 'Approved', 30, FALSE),
    ('purchase_order', 'REJECTED', 'Rejected', 40, TRUE),
    ('purchase_order', 'ISSUED', 'Issued', 50, FALSE),
    ('purchase_order', 'PARTIALLY_RECEIVED', 'Partially received', 60, FALSE),
    ('purchase_order', 'RECEIVED', 'Received', 70, FALSE),
    ('purchase_order', 'CLOSED', 'Closed', 80, TRUE),
    ('purchase_order', 'CANCELLED', 'Cancelled', 90, TRUE)
ON CONFLICT DO NOTHING;

INSERT INTO workflow_action_rule (entity_type, from_status, action_code, required_permission) VALUES
    ('purchase_order', 'DRAFT', 'EDIT', 'purchase_order:update'),
    ('purchase_order', 'DRAFT', 'SUBMIT_APPROVAL', 'purchase_order:update'),
    ('purchase_order', 'DRAFT', 'ISSUE', 'purchase_order:issue'),
    ('purchase_order', 'DRAFT', 'CANCEL', 'purchase_order:cancel'),
    ('purchase_order', 'PENDING_APPROVAL', 'APPROVE', 'approval:act'),
    ('purchase_order', 'PENDING_APPROVAL', 'REJECT', 'approval:act'),
    ('purchase_order', 'PENDING_APPROVAL', 'REQUEST_CHANGES', 'approval:act'),
    ('purchase_order', 'PENDING_APPROVAL', 'CANCEL', 'purchase_order:cancel'),
    ('purchase_order', 'APPROVED', 'ISSUE', 'purchase_order:issue'),
    ('purchase_order', 'APPROVED', 'CANCEL', 'purchase_order:cancel'),
    ('purchase_order', 'ISSUED', 'RECEIVE', 'purchase_order:receive'),
    ('purchase_order', 'ISSUED', 'CREATE_INVOICE', 'invoice:create'),
    ('purchase_order', 'ISSUED', 'CANCEL', 'purchase_order:cancel'),
    ('purchase_order', 'PARTIALLY_RECEIVED', 'RECEIVE', 'purchase_order:receive'),
    ('purchase_order', 'PARTIALLY_RECEIVED', 'CREATE_INVOICE', 'invoice:create'),
    ('purchase_order', 'RECEIVED', 'CREATE_INVOICE', 'invoice:create')
ON CONFLICT DO NOTHING;
