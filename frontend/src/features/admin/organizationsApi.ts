import { api, unwrap } from '@/api/client';
import type { Page } from '@/api/types';
import type { components } from '@/api/schema';

export type Organization = components['schemas']['OrganizationResponse'];

export async function currentOrganization(): Promise<Organization> {
  const page = (await unwrap(
    api.GET('/api/v1/organizations', { params: { query: { page: 0, size: 1 } } }),
  )) as Page<Organization>;
  if (page.content.length === 0) {
    throw new Error('Organization not found');
  }
  return page.content[0];
}

export async function updateOrganization(
  id: string,
  body: { name: string; invoiceTolerancePercent?: number },
): Promise<Organization> {
  return unwrap(
    api.PUT('/api/v1/organizations/{id}', {
      params: { path: { id } },
      body,
    }),
  );
}
