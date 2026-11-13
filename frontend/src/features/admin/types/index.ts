import type { components } from '@/api/schema';
import type { ApprovalPolicy } from '@/api/types';

/** Effective organization row used by administration screens. */
export type Organization = components['schemas']['OrganizationResponse'];
/** Tenant branding values returned by the API. */
export type BrandingView = components['schemas']['BrandingView'];
/** Editable branding payload. */
export type BrandingInput = components['schemas']['UpdateBrandingRequest'];
/** A permission that can be assigned to a role. */
export interface PermissionModel {
  code: string;
  description: string;
}
/** A role available in the tenant. */
export interface RoleModel {
  roleName: string;
  displayName: string;
  description: string;
}
/** Permission codes assigned to one role. */
export interface RolePermissions {
  roleName: string;
  permissions: string[];
}
/** One permission row on the role-permission page. */
export interface PermissionAssignment {
  code: string;
  description: string;
  module: string;
  assigned: boolean;
}
/** Paginated role-permission response. */
export interface RolePermissionPage {
  roleName: string;
  modules: string[];
  permissions: PermissionAssignment[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
/** Full access-configuration payload. */
export interface AccessConfig {
  permissions: PermissionModel[];
  roles: RoleModel[];
  rolePermissions: RolePermissions[];
}
/** One configurable workflow action rule. */
export interface WorkflowRule {
  entityType: string;
  fromStatus: string;
  actionCode: string;
  requiredPermission: string;
}
/** Selectable workflow status for an entity type. */
export interface WorkflowStatus {
  statusCode: string;
  displayName: string;
  sortOrder: number;
}
/** Role-permission page filter. */
export type AssignmentFilter = 'all' | 'assigned' | 'available';
/** Workflow step condition. */
export type ConditionType = 'ALWAYS' | 'BUDGET_EXCEPTION' | 'DEPARTMENT';
/** Workflow approver source. */
export type ApproverType = 'ROLE' | 'REQUESTER_MANAGER' | 'USER';
/** Editable workflow-policy step. */
export interface StepForm {
  id: string;
  name: string;
  approverType: ApproverType;
  approverRole: string;
  approverUserId: string;
  slaHours: number;
  stage: number;
  minApprovals: string;
  conditionType: ConditionType;
  conditionDepartmentId: string;
}
/** Workflow-policy step returned by the API. */
export type PolicyStep = NonNullable<ApprovalPolicy['steps']>[number];
/** Webhook create/update payload. */
export interface WebhookInput {
  name: string;
  url: string;
  description?: string;
  eventTypes: string[];
  signingMethod: string;
  timeoutSeconds: number;
  maxAttempts: number;
  active: boolean;
}
/** One selectable webhook event. */
export interface WebhookEventOption {
  value: string;
  label: string;
}
/** Grouped webhook event catalogue card. */
export interface WebhookEventGroup {
  id: string;
  label: string;
  helperText: string;
  events: WebhookEventOption[];
}
/** Editable webhook form state. */
export interface WebhookFormState {
  name: string;
  url: string;
  description: string;
  eventTypes: string[];
  signingMethod: string;
  timeoutSeconds: number;
  maxAttempts: number;
  active: boolean;
}
/** Catalog item create/update form state. */
export interface CatalogFormState {
  sku: string;
  name: string;
  unit: string;
  amount: string;
  currency: string;
  category: string;
  defaultVendorId: string;
  departmentScope: 'ALL' | 'SELECTED';
  departmentIds: string[];
  active: boolean;
}
/** User invite/update form state. */
export interface UserFormState {
  email: string;
  displayName: string;
  departmentId: string;
  managerId: string;
  limitAmount: string;
  limitCurrency: string;
  active: boolean;
  roles: string[];
}

/** Workflow-policy step create/update payload. */
export type WorkflowPolicyStepInput = components['schemas']['PolicyStepRequest'];

/** Metadata for a configurable workflow action. */
export interface WorkflowActionMeta {
  label: string;
  description: string;
  permission: string;
}
