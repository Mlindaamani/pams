import { api, idempotencyKey, unwrap } from '@/api/client';
import type { ApprovalInstance, ApprovalStep } from '@/api/types';

export async function approvalInbox(): Promise<ApprovalStep[]> {
  return unwrap(api.GET('/api/v1/approvals/inbox'));
}

export async function approve(stepId: string, comment?: string): Promise<ApprovalInstance> {
  return unwrap(
    api.POST('/api/v1/approvals/{stepId}/approve', {
      params: { path: { stepId } },
      headers: { 'Idempotency-Key': idempotencyKey() },
      body: { comment },
    }),
  );
}

export async function reject(stepId: string, comment: string): Promise<ApprovalInstance> {
  return unwrap(
    api.POST('/api/v1/approvals/{stepId}/reject', {
      params: { path: { stepId } },
      headers: { 'Idempotency-Key': idempotencyKey() },
      body: { comment },
    }),
  );
}

export async function requestChanges(stepId: string, comment: string): Promise<ApprovalInstance> {
  return unwrap(
    api.POST('/api/v1/approvals/{stepId}/request-changes', {
      params: { path: { stepId } },
      headers: { 'Idempotency-Key': idempotencyKey() },
      body: { comment },
    }),
  );
}

export async function bulkApprove(ids: string[], comment?: string): Promise<number> {
  const result = await unwrap(
    api.POST('/api/v1/approvals/bulk-approve', {
      headers: { 'Idempotency-Key': idempotencyKey() },
      body: { ids, comment },
    }),
  );
  return result.processed ?? 0;
}
