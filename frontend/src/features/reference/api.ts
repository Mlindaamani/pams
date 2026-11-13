import { api, unwrap } from '@/api/client';
import type { CatalogItem, Department, Page } from '@/api/types';

export async function listDepartments(): Promise<Page<Department>> {
  return unwrap(
    api.GET('/api/v1/departments', {
      params: { query: { page: 0, size: 100, sort: 'name,asc' } },
    }),
  ) as Promise<Page<Department>>;
}

export async function listCatalogItems(departmentId?: string): Promise<Page<CatalogItem>> {
  return unwrap(
    api.GET('/api/v1/catalog-items', {
      params: {
        query: { page: 0, size: 100, sort: 'name,asc', departmentId },
      },
    }),
  ) as Promise<Page<CatalogItem>>;
}

export async function listFavoriteCatalogItems(): Promise<CatalogItem[]> {
  return unwrap(api.GET('/api/v1/catalog-items/favorites')) as Promise<CatalogItem[]>;
}

export async function listPopularCatalogItems(
  departmentId?: string,
  limit = 10,
): Promise<CatalogItem[]> {
  return unwrap(
    api.GET('/api/v1/catalog-items/popular', { params: { query: { departmentId, limit } } }),
  ) as Promise<CatalogItem[]>;
}

export async function favoriteCatalogItem(id: string): Promise<void> {
  await unwrap(api.PUT('/api/v1/catalog-items/{id}/favorite', { params: { path: { id } } }));
}

export async function unfavoriteCatalogItem(id: string): Promise<void> {
  await unwrap(api.DELETE('/api/v1/catalog-items/{id}/favorite', { params: { path: { id } } }));
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
  defaultVendorId?: string;
  departmentScope?: 'ALL' | 'SELECTED';
  departmentIds?: string[];
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
    defaultVendorId?: string;
    departmentScope?: 'ALL' | 'SELECTED';
    departmentIds?: string[];
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

import type { NonCatalogReviewLine } from './types';

export type { NonCatalogReviewLine } from './types';

export async function listNonCatalogReview(): Promise<NonCatalogReviewLine[]> {
  return unwrap(api.GET('/api/v1/catalog-items/non-catalog-review')) as Promise<
    NonCatalogReviewLine[]
  >;
}

export async function resolveNonCatalogReview(
  id: string,
  status: 'ADDED' | 'DISMISSED',
): Promise<void> {
  await unwrap(
    api.PUT('/api/v1/catalog-items/non-catalog-review/{lineId}', {
      params: { path: { lineId: id } },
      body: { status },
    }),
  );
}
