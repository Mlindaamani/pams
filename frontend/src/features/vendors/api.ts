import { api, unwrap } from '@/api/client';
import type { paths } from '@/api/schema';
import type { Page, Vendor } from '@/api/types';

export async function listVendors(params: {
  q?: string;
  status?: NonNullable<paths['/api/v1/vendors']['get']['parameters']['query']>['status'];
  page?: number;
  size?: number;
}): Promise<Page<Vendor>> {
  return unwrap(
    api.GET('/api/v1/vendors', {
      params: { query: { ...params, sort: 'name,asc' } },
    }),
  ) as Promise<Page<Vendor>>;
}

export async function createVendor(body: {
  name: string;
  taxId: string;
  contactEmail?: string;
  contactName?: string;
}): Promise<Vendor> {
  return unwrap(api.POST('/api/v1/vendors', { body }));
}

export async function setVendorStatus(
  id: string,
  action: 'activate' | 'suspend' | 'blacklist',
): Promise<Vendor> {
  const path = `/api/v1/vendors/{id}/${action}` as const;
  return unwrap(api.POST(path, { params: { path: { id } } }));
}

export async function updateVendor(
  id: string,
  body: {
    name: string;
    taxId: string;
    contactName?: string;
    contactEmail?: string;
    contactPhone?: string;
    addressLine?: string;
    bankName?: string;
    bankAccountName?: string;
    bankAccountNumber?: string;
  },
): Promise<Vendor> {
  return unwrap(api.PUT('/api/v1/vendors/{id}', { params: { path: { id } }, body }));
}

export async function bulkDeleteVendors(ids: string[]): Promise<number> {
  const result = await unwrap(api.POST('/api/v1/vendors/bulk-delete', { body: { ids } }));
  return result.processed ?? 0;
}
