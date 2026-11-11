-- Permission-based authorization and database-driven workflow actions.

CREATE TABLE permission (
    code        VARCHAR(100) NOT NULL,
    description VARCHAR(300) NOT NULL,
    CONSTRAINT pk_permission PRIMARY KEY (code)
);

INSERT INTO permission (code, description) VALUES
    ('purchase_request:create', 'Create purchase requests'),
    ('purchase_request:read', 'View purchase requests'),
    ('purchase_request:view_all', 'View all tenant purchase requests'),
    ('purchase_request:update', 'Edit draft/change-requested purchase requests'),
    ('purchase_request:delete', 'Delete draft purchase requests'),
    ('purchase_request:submit', 'Submit purchase requests'),
    ('purchase_request:cancel', 'Cancel purchase requests'),
    ('purchase_request:convert', 'Convert approved requests to purchase orders'),
    ('approval:read', 'View approval timelines'),
    ('approval:act', 'Act on approval steps'),
    ('approval:act:REQUESTER', 'Approve as requester role'),
    ('approval:act:APPROVER', 'Approve as approver role'),
    ('approval:act:PROCUREMENT_OFFICER', 'Approve as procurement officer role'),
    ('approval:act:FINANCE', 'Approve as finance role'),
    ('approval:act:AUDITOR', 'Approve as auditor role'),
    ('approval:act:ADMIN', 'Approve as admin role'),
    ('approval_policy:manage', 'Create and change approval policies'),
    ('vendor:read', 'View vendors'),
    ('vendor:manage', 'Create and change vendors'),
    ('catalog:read', 'View catalog items'),
    ('catalog:manage', 'Create and change catalog items'),
    ('budget:read', 'View budgets'),
    ('budget:manage', 'Create and change budgets'),
    ('purchase_order:read', 'View purchase orders'),
    ('purchase_order:create', 'Create purchase orders'),
    ('purchase_order:update', 'Edit draft purchase orders'),
    ('purchase_order:issue', 'Issue purchase orders'),
    ('purchase_order:cancel', 'Cancel purchase orders'),
    ('purchase_order:receive', 'Record goods receipts'),
    ('invoice:read', 'View vendor invoices'),
    ('invoice:create', 'Record vendor invoices'),
    ('invoice:approve', 'Approve mismatched vendor invoices'),
    ('report:read', 'View reports'),
    ('report:export', 'Export reports'),
    ('audit:read', 'View audit logs'),
    ('department:read', 'View departments'),
    ('department:manage', 'Create and change departments'),
    ('user:read', 'View user profiles'),
    ('user:manage', 'Create and change user profiles'),
    ('organization:read', 'View organization settings'),
    ('organization:update', 'Change organization settings'),
    ('system:read', 'View system status'),
    ('webhook:manage', 'Manage webhook endpoints'),
    ('delegation:read', 'View delegations'),
    ('delegation:manage', 'Create and revoke delegations'),
    ('notification:read', 'View notifications'),
    ('access:manage', 'Manage roles and permission mappings'),
    ('workflow:manage', 'Manage workflow action rules');

CREATE TABLE role_model (
    role_name    VARCHAR(50)  NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    description  VARCHAR(500) NOT NULL,
    CONSTRAINT pk_role_model PRIMARY KEY (role_name)
);

INSERT INTO role_model (role_name, display_name, description) VALUES
    ('ADMIN', 'Administrator', 'System configuration, access control, policies and all procurement operations.'),
    ('REQUESTER', 'Requester', 'Creates and submits purchase requests for their own department.'),
    ('APPROVER', 'Approver', 'Reviews and acts on assigned approval steps.'),
    ('PROCUREMENT_OFFICER', 'Procurement officer', 'Manages vendors, catalog, purchase orders and goods receipts.'),
    ('FINANCE', 'Finance', 'Manages budgets, invoices, financial approvals and reports.'),
    ('AUDITOR', 'Auditor', 'Read-only access to procurement records, reports and audit logs.');

CREATE TABLE role_permission (
    role_name       VARCHAR(50)  NOT NULL,
    permission_code VARCHAR(100) NOT NULL REFERENCES permission (code),
    CONSTRAINT pk_role_permission PRIMARY KEY (role_name, permission_code)
);
CREATE INDEX ix_role_permission_permission ON role_permission (permission_code);

-- ADMIN receives every permission, including future permissions inserted by later migrations.
INSERT INTO role_permission (role_name, permission_code)
SELECT 'ADMIN', code FROM permission;

INSERT INTO role_permission (role_name, permission_code) VALUES
    ('REQUESTER', 'purchase_request:create'),
    ('REQUESTER', 'purchase_request:read'),
    ('REQUESTER', 'purchase_request:update'),
    ('REQUESTER', 'purchase_request:delete'),
    ('REQUESTER', 'purchase_request:submit'),
    ('REQUESTER', 'purchase_request:cancel'),
    ('REQUESTER', 'approval:read'),
    ('REQUESTER', 'approval:act'),
    ('REQUESTER', 'approval:act:REQUESTER'),
    ('REQUESTER', 'vendor:read'),
    ('REQUESTER', 'catalog:read'),
    ('REQUESTER', 'department:read'),
    ('REQUESTER', 'delegation:read'),
    ('REQUESTER', 'notification:read'),

    ('APPROVER', 'purchase_request:read'),
    ('APPROVER', 'purchase_request:view_all'),
    ('APPROVER', 'approval:read'),
    ('APPROVER', 'approval:act'),
    ('APPROVER', 'approval:act:APPROVER'),
    ('APPROVER', 'vendor:read'),
    ('APPROVER', 'catalog:read'),
    ('APPROVER', 'department:read'),
    ('APPROVER', 'delegation:read'),
    ('APPROVER', 'delegation:manage'),
    ('APPROVER', 'notification:read'),

    ('PROCUREMENT_OFFICER', 'purchase_request:read'),
    ('PROCUREMENT_OFFICER', 'purchase_request:view_all'),
    ('PROCUREMENT_OFFICER', 'purchase_request:convert'),
    ('PROCUREMENT_OFFICER', 'approval:read'),
    ('PROCUREMENT_OFFICER', 'approval:act'),
    ('PROCUREMENT_OFFICER', 'approval:act:PROCUREMENT_OFFICER'),
    ('PROCUREMENT_OFFICER', 'vendor:read'),
    ('PROCUREMENT_OFFICER', 'vendor:manage'),
    ('PROCUREMENT_OFFICER', 'catalog:read'),
    ('PROCUREMENT_OFFICER', 'catalog:manage'),
    ('PROCUREMENT_OFFICER', 'budget:read'),
    ('PROCUREMENT_OFFICER', 'purchase_order:read'),
    ('PROCUREMENT_OFFICER', 'purchase_order:create'),
    ('PROCUREMENT_OFFICER', 'purchase_order:update'),
    ('PROCUREMENT_OFFICER', 'purchase_order:issue'),
    ('PROCUREMENT_OFFICER', 'purchase_order:cancel'),
    ('PROCUREMENT_OFFICER', 'purchase_order:receive'),
    ('PROCUREMENT_OFFICER', 'invoice:read'),
    ('PROCUREMENT_OFFICER', 'report:read'),
    ('PROCUREMENT_OFFICER', 'report:export'),
    ('PROCUREMENT_OFFICER', 'department:read'),
    ('PROCUREMENT_OFFICER', 'delegation:read'),
    ('PROCUREMENT_OFFICER', 'notification:read'),

    ('FINANCE', 'purchase_request:read'),
    ('FINANCE', 'purchase_request:view_all'),
    ('FINANCE', 'approval:read'),
    ('FINANCE', 'approval:act'),
    ('FINANCE', 'approval:act:FINANCE'),
    ('FINANCE', 'vendor:read'),
    ('FINANCE', 'catalog:read'),
    ('FINANCE', 'budget:read'),
    ('FINANCE', 'budget:manage'),
    ('FINANCE', 'purchase_order:read'),
    ('FINANCE', 'invoice:read'),
    ('FINANCE', 'invoice:create'),
    ('FINANCE', 'invoice:approve'),
    ('FINANCE', 'report:read'),
    ('FINANCE', 'report:export'),
    ('FINANCE', 'department:read'),
    ('FINANCE', 'delegation:read'),
    ('FINANCE', 'notification:read'),

    ('AUDITOR', 'purchase_request:read'),
    ('AUDITOR', 'purchase_request:view_all'),
    ('AUDITOR', 'approval:read'),
    ('AUDITOR', 'approval:act'),
    ('AUDITOR', 'approval:act:AUDITOR'),
    ('AUDITOR', 'vendor:read'),
    ('AUDITOR', 'catalog:read'),
    ('AUDITOR', 'budget:read'),
    ('AUDITOR', 'purchase_order:read'),
    ('AUDITOR', 'invoice:read'),
    ('AUDITOR', 'report:read'),
    ('AUDITOR', 'report:export'),
    ('AUDITOR', 'audit:read'),
    ('AUDITOR', 'department:read'),
    ('AUDITOR', 'user:read'),
    ('AUDITOR', 'organization:read'),
    ('AUDITOR', 'delegation:read'),
    ('AUDITOR', 'notification:read');

CREATE TABLE workflow_status (
    entity_type  VARCHAR(50) NOT NULL,
    status_code  VARCHAR(50) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    sort_order   INTEGER     NOT NULL,
    terminal     BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_workflow_status PRIMARY KEY (entity_type, status_code)
);

INSERT INTO workflow_status (entity_type, status_code, display_name, sort_order, terminal) VALUES
    ('purchase_request', 'DRAFT', 'Draft', 10, FALSE),
    ('purchase_request', 'SUBMITTED', 'Submitted', 20, FALSE),
    ('purchase_request', 'IN_APPROVAL', 'In approval', 30, FALSE),
    ('purchase_request', 'CHANGES_REQUESTED', 'Changes requested', 40, FALSE),
    ('purchase_request', 'APPROVED', 'Approved', 50, FALSE),
    ('purchase_request', 'REJECTED', 'Rejected', 60, TRUE),
    ('purchase_request', 'CANCELLED', 'Cancelled', 70, TRUE),
    ('purchase_request', 'CONVERTED_TO_PO', 'Converted to PO', 80, TRUE);

CREATE TABLE workflow_action_rule (
    entity_type         VARCHAR(50)  NOT NULL,
    from_status         VARCHAR(50)  NOT NULL,
    action_code         VARCHAR(50)  NOT NULL,
    required_permission VARCHAR(100) NOT NULL REFERENCES permission (code),
    CONSTRAINT pk_workflow_action_rule
        PRIMARY KEY (entity_type, from_status, action_code)
);

INSERT INTO workflow_action_rule (entity_type, from_status, action_code, required_permission) VALUES
    ('purchase_request', 'DRAFT', 'EDIT', 'purchase_request:update'),
    ('purchase_request', 'DRAFT', 'DELETE', 'purchase_request:delete'),
    ('purchase_request', 'DRAFT', 'SUBMIT', 'purchase_request:submit'),
    ('purchase_request', 'DRAFT', 'CANCEL', 'purchase_request:cancel'),
    ('purchase_request', 'SUBMITTED', 'CANCEL', 'purchase_request:cancel'),
    ('purchase_request', 'CHANGES_REQUESTED', 'EDIT', 'purchase_request:update'),
    ('purchase_request', 'CHANGES_REQUESTED', 'SUBMIT', 'purchase_request:submit'),
    ('purchase_request', 'CHANGES_REQUESTED', 'CANCEL', 'purchase_request:cancel'),
    ('purchase_request', 'IN_APPROVAL', 'APPROVE', 'approval:act'),
    ('purchase_request', 'IN_APPROVAL', 'REJECT', 'approval:act'),
    ('purchase_request', 'IN_APPROVAL', 'REQUEST_CHANGES', 'approval:act'),
    ('purchase_request', 'APPROVED', 'CREATE_PO', 'purchase_order:create');
