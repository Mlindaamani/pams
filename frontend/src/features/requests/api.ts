import { api, etag, idempotencyKey, unwrap } from '@/api/client';
import type { paths } from '@/api/schema';
import type {
  ApprovalInstance,
  Attachment,
  Page,
  PurchaseRequest,
  PurchaseRequestRequest,
} from '@/api/types';

type RequestQuery = NonNullable<paths['/api/v1/purchase-requests']['get']['parameters']['query']>;
export type RequestFilters = RequestQuery;

export async function listRequests(filters: RequestFilters): Promise<Page<PurchaseRequest>> {
  return unwrap(
    api.GET('/api/v1/purchase-requests', {
      params: { query: { ...filters, sort: filters.sort ?? 'createdAt,desc' } },
    }),
  ) as Promise<Page<PurchaseRequest>>;
}

export async function getRequest(id: string): Promise<PurchaseRequest> {
  return unwrap(api.GET('/api/v1/purchase-requests/{id}', { params: { path: { id } } }));
}

export async function createRequest(body: PurchaseRequestRequest): Promise<PurchaseRequest> {
  return unwrap(
    api.POST('/api/v1/purchase-requests', {
      body,
      headers: { 'Idempotency-Key': idempotencyKey() },
    }),
  );
}

export async function updateRequest(
  id: string,
  body: PurchaseRequestRequest,
  version: number,
): Promise<PurchaseRequest> {
  return unwrap(
    api.PUT('/api/v1/purchase-requests/{id}', {
      params: { path: { id } },
      body,
      headers: { 'If-Match': etag(version) },
    }),
  );
}

export async function cloneRequest(id: string): Promise<PurchaseRequest> {
  return unwrap(
    api.POST('/api/v1/purchase-requests/{id}/clone', {
      params: { path: { id } },
      headers: { 'Idempotency-Key': idempotencyKey() },
    }),
  );
}

export async function deleteRequest(id: string, version: number): Promise<void> {
  await unwrap(
    api.DELETE('/api/v1/purchase-requests/{id}', {
      params: { path: { id } },
      headers: { 'If-Match': etag(version) },
    }),
  );
}

export async function submitRequest(id: string, version: number): Promise<PurchaseRequest> {
  return unwrap(
    api.POST('/api/v1/purchase-requests/{id}/submit', {
      params: { path: { id } },
      headers: { 'If-Match': etag(version), 'Idempotency-Key': idempotencyKey() },
    }),
  );
}

export async function cancelRequest(id: string, version: number): Promise<PurchaseRequest> {
  return unwrap(
    api.POST('/api/v1/purchase-requests/{id}/cancel', {
      params: { path: { id } },
      headers: { 'If-Match': etag(version), 'Idempotency-Key': idempotencyKey() },
    }),
  );
}

export async function getTimeline(id: string): Promise<ApprovalInstance> {
  return unwrap(
    api.GET('/api/v1/purchase-requests/{requestId}/approval', {
      params: { path: { requestId: id } },
    }),
  );
}

export interface RequestActions {
  actions: string[];
  approvalStepId?: string;
}

export async function getRequestActions(id: string): Promise<RequestActions> {
  return unwrap(
    api.GET('/api/v1/purchase-requests/{id}/actions', { params: { path: { id } } }),
  ) as Promise<RequestActions>;
}

export async function listAttachments(id: string): Promise<Attachment[]> {
  return unwrap(
    api.GET('/api/v1/purchase-requests/{requestId}/attachments', {
      params: { path: { requestId: id } },
    }),
  );
}

export async function uploadAttachment(id: string, file: File): Promise<Attachment> {
  const form = new FormData();
  form.append('file', file);
  return unwrap(
    api.POST('/api/v1/purchase-requests/{requestId}/attachments', {
      params: { path: { requestId: id } },
      body: form as unknown as { file: string },
    }),
  );
}

export async function downloadAttachment(id: string, attachmentId: string): Promise<Blob> {
  return unwrap(
    api.GET('/api/v1/purchase-requests/{requestId}/attachments/{attachmentId}', {
      params: { path: { requestId: id, attachmentId } },
      parseAs: 'blob',
    }),
  );
}

export async function deleteAttachment(id: string, attachmentId: string): Promise<void> {
  await unwrap(
    api.DELETE('/api/v1/purchase-requests/{requestId}/attachments/{attachmentId}', {
      params: { path: { requestId: id, attachmentId } },
    }),
  );
}
