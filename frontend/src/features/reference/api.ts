import { api, unwrap } from '@/api/client';
import type { CatalogItem, Department, Page } from '@/api/types';

export async function listDepartments(): Promise<Page<Department>> {
  return unwrap(
    api.GET('/api/v1/departments', {
      params: { query: { page: 0, size: 100, sort: 'name,asc' } },
    }),
  ) as Promise<Page<Department>>;
}

export async function listCatalogItems(): Promise<Page<CatalogItem>> {
  return unwrap(
    api.GET('/api/v1/catalog-items', {
      params: { query: { page: 0, size: 100, sort: 'name,asc' } },
    }),
  ) as Promise<Page<CatalogItem>>;
}

export async function createDepartment(body: { name: string }): Promise<Department> {
  return unwrap(api.POST('/api/v1/departments', { body }));
}

export async function deleteDepartment(id: string): Promise<void> {
  await unwrap(api.DELETE('/api/v1/departments/{id}', { params: { path: { id } } }));
}

export async function createCatalogItem(body: {
  sku: string;
  name: string;
  unit: string;
  defaultPrice: { amount: string; currency: string };
  category?: string;
}): Promise<CatalogItem> {
  return unwrap(api.POST('/api/v1/catalog-items', { body }));
}

export async function updateCatalogItem(
  id: string,
  body: {
    name: string;
    unit: string;
    defaultPrice: { amount: string; currency: string };
    category?: string;
    active: boolean;
  },
): Promise<CatalogItem> {
  return unwrap(api.PUT('/api/v1/catalog-items/{id}', { params: { path: { id } }, body }));
}

export async function deleteCatalogItem(id: string): Promise<void> {
  await unwrap(api.DELETE('/api/v1/catalog-items/{id}', { params: { path: { id } } }));
}

export async function bulkDeleteCatalogItems(ids: string[]): Promise<number> {
  const result = await unwrap(api.POST('/api/v1/catalog-items/bulk-delete', { body: { ids } }));
  return result.processed ?? 0;
}

export async function bulkDeleteDepartments(ids: string[]): Promise<number> {
  const result = await unwrap(api.POST('/api/v1/departments/bulk-delete', { body: { ids } }));
  return result.processed ?? 0;
}
