import type { components } from '@/api/schema';

/** Tenant/organization row used by platform administration. */
export type Tenant = components['schemas']['OrganizationResponse'];
/** Tenant detail including subscription and provisioning state. */
export type TenantDetail = components['schemas']['TenantDetailResponse'];
/** Inbound public demo lead. */
export type DemoRequest = components['schemas']['DemoRequestResponse'];
/** Lifecycle status of a demo lead. */
export type DemoRequestStatus = 'NEW' | 'CONTACTED' | 'QUALIFIED' | 'CLOSED';

/** Spend total for one currency on the platform dashboard. */
export interface CurrencySpend {
  currency: string;
  total: number;
  orderCount: number;
}

/** Per-tenant usage and health metric. */
export interface TenantMetric {
  id: string;
  name: string;
  slug: string;
  status: 'ACTIVE' | 'SUSPENDED';
  users: number;
  requests: number;
  pendingApprovals: number;
  mismatchedInvoices: number;
  spendByCurrency: Record<string, number>;
  lastActivity?: string;
}

/** Cross-tenant platform analytics payload. */
export interface PlatformDashboard {
  from: string;
  to: string;
  generatedAt: string;
  summary: {
    totalTenants: number;
    activeTenants: number;
    suspendedTenants: number;
    newTenants: number;
    totalUsers: number;
    totalRequests: number;
    pendingApprovals: number;
    mismatchedInvoices: number;
    outboxDeadLetters: number;
  };
  spendByCurrency: CurrencySpend[];
  requestStatus: { status: string; count: number }[];
  monthlySpend: { month: string; currency: string; total: number; orderCount: number }[];
  monthlyRequests: {
    month: string;
    submitted: number;
    approved: number;
    rejected: number;
  }[];
  tenants: TenantMetric[];
}

/** Editable feature-entitlement form state. */
export interface FeatureDraft {
  code: string;
  name: string;
  description: string;
  category: string;
  active: boolean;
  sortOrder: string;
}

/** Editable subscription-plan form state. */
export interface PlanDraft {
  code: string;
  name: string;
  description: string;
  monthlyPrice: string;
  annualPrice: string;
  currency: string;
  trialDays: string;
  maxUsers: string;
  maxDepartments: string;
  maxRequestsMonth: string;
  maxStorageMb: string;
  featureCodes: string[];
  active: boolean;
  sortOrder: string;
  stripePriceMonthly: string;
  stripePriceAnnual: string;
}
