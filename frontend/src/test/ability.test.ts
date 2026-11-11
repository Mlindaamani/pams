import { describe, expect, it } from 'vitest';
import { defineAbilityFor, rolesFromAccessToken, rolesFromProfile } from '@/auth/ability';

describe('CASL ability', () => {
  it('grants abilities only from permission codes', () => {
    const ability = defineAbilityFor(
      [
        'purchase_request:create',
        'purchase_request:read',
        'purchase_request:submit',
        'purchase_order:issue',
        'access:manage',
      ],
      ['requests', 'purchase_orders'],
    );
    expect(ability.can('create', 'PurchaseRequest')).toBe(true);
    expect(ability.can('submit', 'PurchaseRequest')).toBe(true);
    expect(ability.can('issue', 'PurchaseOrder')).toBe(true);
    expect(ability.can('read', 'Config')).toBe(true);
    expect(ability.can('approve', 'Approval')).toBe(false);
  });

  it('separates global audit from request transition logs', () => {
    const transitions = defineAbilityFor(['audit:transition_read']);
    expect(transitions.can('read-transitions', 'PurchaseRequest')).toBe(true);
    expect(transitions.can('read', 'Audit')).toBe(false);

    const globalAudit = defineAbilityFor(['audit:read']);
    expect(globalAudit.can('read', 'Audit')).toBe(true);
    expect(globalAudit.can('read-transitions', 'PurchaseRequest')).toBe(false);
  });

  it('does not infer permissions from role names', () => {
    const ability = defineAbilityFor([]);
    expect(ability.can('create', 'PurchaseRequest')).toBe(false);
    expect(ability.can('issue', 'PurchaseOrder')).toBe(false);
  });

  it('maps approval and finance permissions explicitly', () => {
    const finance = defineAbilityFor(
      [
        'approval:read',
        'approval:act',
        'invoice:read',
        'invoice:create',
        'invoice:approve',
        'budget:read',
        'budget:manage',
      ],
      ['approvals', 'invoices'],
    );
    expect(finance.can('approve', 'Approval')).toBe(true);
    expect(finance.can('request-changes', 'Approval')).toBe(true);
    expect(finance.can('create', 'Invoice')).toBe(true);
    expect(finance.can('manage', 'Budget')).toBe(true);
    expect(finance.can('manage', 'Vendor')).toBe(false);
  });

  it('does not grant feature-scoped abilities without entitlements', () => {
    const ability = defineAbilityFor(
      ['purchase_request:read', 'invoice:read', 'webhook:manage', 'report:export', 'approval:act'],
      ['requests'],
    );
    expect(ability.can('read', 'PurchaseRequest')).toBe(true);
    expect(ability.can('read', 'Invoice')).toBe(false);
    expect(ability.can('manage', 'Webhook')).toBe(false);
    expect(ability.can('export', 'Report')).toBe(false);
    expect(ability.can('approve', 'Approval')).toBe(false);
  });

  it('reads Keycloak realm roles for display only', () => {
    expect(rolesFromProfile({ realm_access: { roles: ['ADMIN', 'FINANCE'] } })).toEqual([
      'ADMIN',
      'FINANCE',
    ]);
  });

  it('reads realm roles from the access token', () => {
    const payload = btoa(JSON.stringify({ realm_access: { roles: ['ADMIN'] } }))
      .replace(/=+$/, '')
      .replace(/\+/g, '-')
      .replace(/\//g, '_');
    const token = `header.${payload}.signature`;
    expect(rolesFromAccessToken(token)).toEqual(['ADMIN']);
  });
});
