import { api, unwrap } from '@/api/client';

export interface PermissionModel {
  code: string;
  description: string;
}

export interface RoleModel {
  roleName: string;
  displayName: string;
  description: string;
}

export interface RolePermissions {
  roleName: string;
  permissions: string[];
}

export interface PermissionAssignment {
  code: string;
  description: string;
  module: string;
  assigned: boolean;
}

export interface RolePermissionPage {
  roleName: string;
  modules: string[];
  permissions: PermissionAssignment[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface AccessConfig {
  permissions: PermissionModel[];
  roles: RoleModel[];
  rolePermissions: RolePermissions[];
}

export interface WorkflowRule {
  entityType: string;
  fromStatus: string;
  actionCode: string;
  requiredPermission: string;
}

export interface WorkflowStatus {
  statusCode: string;
  displayName: string;
  sortOrder: number;
}

export async function getAccessConfig(): Promise<AccessConfig> {
  return unwrap(api.GET('/api/v1/admin/access')) as Promise<AccessConfig>;
}

export async function getRolePermissions(
  roleName: string,
  filters: {
    q?: string;
    assignment?: 'all' | 'assigned' | 'available';
    module?: string;
    page?: number;
    size?: number;
  },
): Promise<RolePermissionPage> {
  return unwrap(
    api.GET('/api/v1/admin/access/roles/{roleName}/permissions', {
      params: {
        path: { roleName },
        query: {
          q: filters.q,
          assignment: filters.assignment ?? 'all',
          module: filters.module,
          page: filters.page ?? 0,
          size: filters.size ?? 200,
        },
      },
    }),
  ) as Promise<RolePermissionPage>;
}

export async function setRolePermission(
  roleName: string,
  permissionCode: string,
  assigned: boolean,
): Promise<void> {
  await unwrap(
    api.PUT('/api/v1/admin/access/roles/{roleName}/permissions/{permissionCode}', {
      params: { path: { roleName, permissionCode } },
      body: { assigned },
    }),
  );
}

export async function updateRolePermissions(
  roleName: string,
  permissions: string[],
): Promise<RolePermissions> {
  return unwrap(
    api.PUT('/api/v1/admin/access/roles/{roleName}', {
      params: { path: { roleName } },
      body: { permissions },
    }),
  ) as Promise<RolePermissions>;
}

export async function getWorkflowRules(entityType: string): Promise<WorkflowRule[]> {
  return unwrap(
    api.GET('/api/v1/admin/workflow-actions/{entityType}', {
      params: { path: { entityType } },
    }),
  ) as Promise<WorkflowRule[]>;
}

export async function getWorkflowStatuses(entityType: string): Promise<WorkflowStatus[]> {
  return unwrap(
    api.GET('/api/v1/admin/workflow-actions/{entityType}/statuses', {
      params: { path: { entityType } },
    }),
  ) as Promise<WorkflowStatus[]>;
}

export async function saveWorkflowRules(
  entityType: string,
  rules: WorkflowRule[],
): Promise<WorkflowRule[]> {
  return unwrap(
    api.PUT('/api/v1/admin/workflow-actions/{entityType}', {
      params: { path: { entityType } },
      body: {
        rules: rules.map(({ fromStatus, actionCode, requiredPermission }) => ({
          fromStatus,
          actionCode,
          requiredPermission,
        })),
      },
    }),
  ) as Promise<WorkflowRule[]>;
}
