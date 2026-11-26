import { describe, expect, it } from 'vitest';
import { APP_ROLES, isAppRole, roleLabel } from '@/features/admin/userRoles';

describe('user role labels', () => {
  it('keeps the assignable roles stable', () => {
    expect(APP_ROLES).toEqual([
      'ADMIN',
      'REQUESTER',
      'APPROVER',
      'PROCUREMENT_OFFICER',
      'FINANCE',
      'AUDITOR',
    ]);
  });

  it('formats user-facing labels and excludes technical roles', () => {
    expect(roleLabel('PROCUREMENT_OFFICER')).toBe('Procurement officer');
    expect(isAppRole('FINANCE')).toBe(true);
    expect(isAppRole('offline_access')).toBe(false);
  });
});
