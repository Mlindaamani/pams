import { api, etag, idempotencyKey, unwrap } from '@/api/client';
import type { Page } from '@/api/types';

import type {
  DemoRequest,
  DemoRequestStatus,
  PlatformDashboard,
  Tenant,
  TenantDetail,
  TenantRecord,
} from './types';

export type {
  DemoRequest,
  DemoRequestStatus,
  PlatformDashboard,
  Tenant,
  TenantDetail,
  TenantRecord,
} from './types';

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
}): Promise<TenantRecord> {
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
): Promise<TenantRecord> {
  return unwrap(
    api.PUT('/api/v1/platform/tenants/{id}', {
      params: { path: { id } },
      headers: { 'If-Match': etag(version) },
      body,
    }),
  );
}

export async function exportTenant(id: string): Promise<Blob> {
  return unwrap(
    api.GET('/api/v1/platform/tenants/{id}/export', {
      params: { path: { id } },
      parseAs: 'blob',
    }),
  ) as Promise<Blob>;
}

export async function deleteTenant(
  id: string,
  version: number,
  confirmSlug: string,
): Promise<void> {
  await unwrap(
    api.DELETE('/api/v1/platform/tenants/{id}', {
      params: { path: { id }, query: { confirmSlug } },
      headers: { 'If-Match': etag(version) },
    }),
  );
}

export async function suspendTenant(id: string, version: number): Promise<TenantRecord> {
  return unwrap(
    api.POST('/api/v1/platform/tenants/{id}/suspend', {
      params: { path: { id } },
      headers: { 'If-Match': etag(version), 'Idempotency-Key': idempotencyKey() },
    }),
  );
}

export async function activateTenant(id: string, version: number): Promise<TenantRecord> {
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
