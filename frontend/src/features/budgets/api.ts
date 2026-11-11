import { api, unwrap } from '@/api/client';
import type { Budget, Page } from '@/api/types';

export async function listBudgets(params: {
  departmentId?: string;
  fiscalYear?: number;
  page?: number;
  size?: number;
}): Promise<Page<Budget>> {
  return unwrap(
    api.GET('/api/v1/budgets', {
      params: { query: { ...params, sort: 'createdAt,desc' } },
    }),
  ) as Promise<Page<Budget>>;
}

export async function createBudget(body: {
  departmentId: string;
  fiscalYear: number;
  allocated: { amount: string; currency: string };
}): Promise<Budget> {
  return unwrap(api.POST('/api/v1/budgets', { body }));
}

export async function updateBudget(
  id: string,
  allocated: { amount: string; currency: string },
): Promise<Budget> {
  return unwrap(api.PUT('/api/v1/budgets/{id}', { params: { path: { id } }, body: { allocated } }));
}

export async function deleteBudget(id: string): Promise<void> {
  await unwrap(api.DELETE('/api/v1/budgets/{id}', { params: { path: { id } } }));
}
