import { api, etag, idempotencyKey, unwrap } from '@/api/client';
import type { Page } from '@/api/types';
import type { components } from '@/api/schema';

export type Tenant = components['schemas']['OrganizationResponse'];
export type TenantDetail = components['schemas']['TenantDetailResponse'];
export type DemoRequest = components['schemas']['DemoRequestResponse'];
export type DemoRequestStatus = 'NEW' | 'CONTACTED' | 'QUALIFIED' | 'CLOSED';

export interface CurrencySpend {
  currency: string;
  total: number;
  orderCount: number;
}

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

export async function listTenants(params: {
  q?: string;
  status?: 'ACTIVE' | 'SUSPENDED';
  size?: number;
}): Promise<Page<Tenant>> {
  return unwrap(
    api.GET('/api/v1/platform/tenants', {
      params: { query: { ...params, page: 0, size: params.size ?? 100, sort: 'createdAt,desc' } },
    }),
  ) as Promise<Page<Tenant>>;
}

export async function createTenant(body: {
  name: string;
  slug: string;
  invoiceTolerancePercent: number;
  planCode?: string;
  adminEmail?: string;
  adminName?: string;
}): Promise<Tenant> {
  return unwrap(
    api.POST('/api/v1/platform/tenants', {
      headers: { 'Idempotency-Key': idempotencyKey() },
      body,
    }),
  );
}

export async function updateTenant(
  id: string,
  version: number,
  body: { name: string; slug: string; invoiceTolerancePercent: number },
): Promise<Tenant> {
  return unwrap(
    api.PUT('/api/v1/platform/tenants/{id}', {
      params: { path: { id } },
      headers: { 'If-Match': etag(version) },
      body,
    }),
  );
}

export async function suspendTenant(id: string, version: number): Promise<Tenant> {
  return unwrap(
    api.POST('/api/v1/platform/tenants/{id}/suspend', {
      params: { path: { id } },
      headers: { 'If-Match': etag(version), 'Idempotency-Key': idempotencyKey() },
    }),
  );
}

export async function activateTenant(id: string, version: number): Promise<Tenant> {
  return unwrap(
    api.POST('/api/v1/platform/tenants/{id}/activate', {
      params: { path: { id } },
      headers: { 'If-Match': etag(version), 'Idempotency-Key': idempotencyKey() },
    }),
  );
}

export async function getPlatformDashboard(from: string, to: string): Promise<PlatformDashboard> {
  return unwrap(
    api.GET('/api/v1/platform/analytics/dashboard', {
      params: { query: { from, to } },
    }),
  ) as unknown as Promise<PlatformDashboard>;
}

export async function retryTenantProvisioning(id: string): Promise<void> {
  await unwrap(
    api.POST('/api/v1/platform/tenants/{id}/provisioning/retry', {
      params: { path: { id } },
      headers: { 'Idempotency-Key': idempotencyKey() },
    }),
  );
}

export async function inviteTenantAdministrator(
  id: string,
  body: { email: string; name: string },
): Promise<void> {
  await unwrap(
    api.POST('/api/v1/platform/tenants/{id}/administrator-invite', {
      params: { path: { id } },
      headers: { 'Idempotency-Key': idempotencyKey() },
      body,
    }),
  );
}

export async function getTenantDetail(id: string): Promise<TenantDetail> {
  return unwrap(
    api.GET('/api/v1/platform/tenants/{id}/detail', {
      params: { path: { id } },
    }),
  );
}

export async function listDemoRequests(params: {
  q?: string;
  status?: DemoRequestStatus;
  page?: number;
  size?: number;
}): Promise<Page<DemoRequest>> {
  return unwrap(
    api.GET('/api/v1/platform/demo-requests', {
      params: {
        query: {
          ...params,
          page: params.page ?? 0,
          size: params.size ?? 20,
          sort: 'createdAt,desc',
        },
      },
    }),
  ) as Promise<Page<DemoRequest>>;
}
