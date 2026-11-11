import { api, unwrap } from '@/api/client';
import type { ApprovalPolicy, Page } from '@/api/types';

export async function listPolicies(): Promise<Page<ApprovalPolicy>> {
  return unwrap(
    api.GET('/api/v1/approval-policies', {
      params: { query: { page: 0, size: 100, sort: 'createdAt,desc' } },
    }),
  ) as Promise<Page<ApprovalPolicy>>;
}

export async function createPolicy(body: {
  name: string;
  entityType?: string;
  amountMin: number;
  amountMax?: number;
  steps: {
    name: string;
    approverType: 'ROLE' | 'USER' | 'REQUESTER_MANAGER';
    approverRole?: string;
    slaHours: number;
  }[];
}): Promise<ApprovalPolicy> {
  return unwrap(api.POST('/api/v1/approval-policies', { body }));
}

export async function deletePolicy(id: string): Promise<void> {
  await unwrap(
    api.DELETE('/api/v1/approval-policies/{id}', {
      params: { path: { id } },
    }),
  );
}

export async function updatePolicy(
  id: string,
  body: {
    name: string;
    entityType?: string;
    amountMin: number;
    amountMax?: number;
    steps: {
      name: string;
      approverType: 'ROLE' | 'USER' | 'REQUESTER_MANAGER';
      approverRole?: string;
      slaHours: number;
    }[];
  },
): Promise<ApprovalPolicy> {
  return unwrap(api.PUT('/api/v1/approval-policies/{id}', { params: { path: { id } }, body }));
}
