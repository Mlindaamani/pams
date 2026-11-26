export const APP_ROLES = [
  'ADMIN',
  'REQUESTER',
  'APPROVER',
  'PROCUREMENT_OFFICER',
  'FINANCE',
  'AUDITOR',
] as const;

export type AppRole = (typeof APP_ROLES)[number];

const ROLE_LABELS: Record<string, string> = {
  ADMIN: 'Administrator',
  REQUESTER: 'Requester',
  APPROVER: 'Approver',
  PROCUREMENT_OFFICER: 'Procurement officer',
  FINANCE: 'Finance',
  AUDITOR: 'Auditor',
};

export function isAppRole(role: string): role is AppRole {
  return (APP_ROLES as readonly string[]).includes(role);
}

export function roleLabel(role: string): string {
  return ROLE_LABELS[role] ?? role.replaceAll('_', ' ').toLowerCase();
}
