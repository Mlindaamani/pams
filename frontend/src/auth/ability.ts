import { AbilityBuilder, createMongoAbility, type MongoAbility } from '@casl/ability';

export type Action =
  | 'manage'
  | 'create'
  | 'read'
  | 'update'
  | 'delete'
  | 'submit'
  | 'cancel'
  | 'approve'
  | 'reject'
  | 'request-changes'
  | 'issue'
  | 'receive'
  | 'convert'
  | 'export';

export type Subject =
  | 'PurchaseRequest'
  | 'Approval'
  | 'Vendor'
  | 'Budget'
  | 'PurchaseOrder'
  | 'Invoice'
  | 'Webhook'
  | 'Catalog'
  | 'Report'
  | 'Audit'
  | 'Policy'
  | 'Department'
  | 'User'
  | 'Notification'
  | 'Delegation'
  | 'Organization'
  | 'System'
  | 'Config'
  | 'Workflow'
  | 'Tenant'
  | 'Platform'
  | 'Subscription'
  | 'Payment'
  | 'Branding'
  | 'all';

export type AppAbility = MongoAbility<[Action, Subject]>;

/** Builds UI abilities from database-backed permission codes, never role names. */
export function defineAbilityFor(permissions: string[], features: string[] = []): AppAbility {
  const { can, build } = new AbilityBuilder<AppAbility>(createMongoAbility);
  const has = (permission: string) => permissions.includes(permission);
  const feature = (code: string) => features.includes(code);

  if (has('notification:read')) can('read', 'Notification');
  if (feature('requests') && has('purchase_request:create')) can('create', 'PurchaseRequest');
  if (feature('requests') && has('purchase_request:read')) can('read', 'PurchaseRequest');
  if (feature('requests') && has('purchase_request:update')) can('update', 'PurchaseRequest');
  if (feature('requests') && has('purchase_request:delete')) can('delete', 'PurchaseRequest');
  if (feature('requests') && has('purchase_request:submit')) can('submit', 'PurchaseRequest');
  if (feature('requests') && has('purchase_request:cancel')) can('cancel', 'PurchaseRequest');
  if (feature('requests') && has('purchase_request:convert')) can('convert', 'PurchaseRequest');

  if (feature('approvals') && has('approval:read')) can('read', 'Approval');
  if (feature('approvals') && has('approval:act')) {
    can(['approve', 'reject', 'request-changes'], 'Approval');
  }

  if (feature('vendors') && has('vendor:read')) can('read', 'Vendor');
  if (feature('vendors') && has('vendor:manage')) can('manage', 'Vendor');
  if (has('catalog:read')) can('read', 'Catalog');
  if (has('catalog:manage')) can('manage', 'Catalog');
  if (has('budget:read')) can('read', 'Budget');
  if (has('budget:manage')) can('manage', 'Budget');

  if (feature('purchase_orders') && has('purchase_order:read')) can('read', 'PurchaseOrder');
  if (feature('purchase_orders') && has('purchase_order:create')) can('create', 'PurchaseOrder');
  if (feature('purchase_orders') && has('purchase_order:update')) can('update', 'PurchaseOrder');
  if (feature('purchase_orders') && has('purchase_order:issue')) can('issue', 'PurchaseOrder');
  if (feature('purchase_orders') && has('purchase_order:cancel')) can('cancel', 'PurchaseOrder');
  if (feature('purchase_orders') && feature('receipts') && has('purchase_order:receive')) {
    can('receive', 'PurchaseOrder');
  }

  if (feature('invoices') && has('invoice:read')) can('read', 'Invoice');
  if (feature('invoices') && has('invoice:create')) can('create', 'Invoice');
  if (feature('invoices') && has('invoice:approve')) can('approve', 'Invoice');

  if (feature('core_reports') && has('report:read')) can('read', 'Report');
  if (feature('core_reports') && feature('advanced_reports') && has('report:export')) {
    can('export', 'Report');
  }
  if (has('audit:read')) can('read', 'Audit');
  if (feature('audit_export') && has('audit:read')) can('export', 'Audit');
  if (feature('approvals') && has('approval_policy:manage')) can(['read', 'manage'], 'Policy');
  if (has('department:read')) can('read', 'Department');
  if (has('department:manage')) can('manage', 'Department');
  if (has('user:read')) can('read', 'User');
  if (has('user:manage')) can('manage', 'User');
  if (has('organization:read')) can('read', 'Organization');
  if (has('organization:update')) can('update', 'Organization');
  if (has('system:read')) can('read', 'System');
  if (feature('webhooks') && has('webhook:manage')) can(['read', 'manage'], 'Webhook');
  if (feature('approvals') && has('delegation:read')) can('read', 'Delegation');
  if (feature('approvals') && has('delegation:manage')) can(['create', 'manage'], 'Delegation');
  if (has('access:manage')) can('read', 'Config');
  if (has('workflow:manage')) can('read', 'Workflow');
  if (has('platform:tenant_read')) can('read', 'Tenant');
  if (has('platform:tenant_manage')) can('manage', 'Tenant');
  if (has('platform:analytics_read')) can('read', 'Platform');
  if (has('subscription:read')) can('read', 'Subscription');
  if (has('subscription:manage')) can('manage', 'Subscription');
  if (has('payment:read')) can('read', 'Payment');
  if (has('payment:manage')) can('manage', 'Payment');
  if (feature('custom_branding') && has('organization:read')) can('read', 'Branding');
  if (feature('custom_branding') && has('organization:update')) can('manage', 'Branding');

  return build();
}

export function rolesFromProfile(profile: unknown): string[] {
  if (profile && typeof profile === 'object' && 'realm_access' in profile) {
    const realmAccess = (profile as { realm_access?: { roles?: string[] } }).realm_access;
    return realmAccess?.roles ?? [];
  }
  return [];
}

/**
 * Keycloak puts realm roles in the access token. Used only for display; authorization is
 * permission-based and comes from the backend.
 */
export function rolesFromAccessToken(accessToken?: string): string[] {
  if (!accessToken) return [];
  try {
    const segment = accessToken.split('.')[1];
    if (!segment) return [];
    const normalized = segment.replace(/-/g, '+').replace(/_/g, '/');
    const padded = normalized.padEnd(normalized.length + ((4 - (normalized.length % 4)) % 4), '=');
    const decoded = globalThis.atob(padded);
    return rolesFromProfile(JSON.parse(decoded));
  } catch {
    return [];
  }
}
