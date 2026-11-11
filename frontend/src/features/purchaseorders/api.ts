import { api, etag, idempotencyKey, unwrap } from '@/api/client';
import type { components, paths } from '@/api/schema';
import type { GoodsReceipt, Page, PurchaseOrder, RecordReceiptRequest } from '@/api/types';

export async function listPurchaseOrders(params: {
  status?: NonNullable<paths['/api/v1/purchase-orders']['get']['parameters']['query']>['status'];
  vendorId?: string;
  q?: string;
  page?: number;
  size?: number;
}): Promise<Page<PurchaseOrder>> {
  return unwrap(
    api.GET('/api/v1/purchase-orders', {
      params: { query: { ...params, sort: 'createdAt,desc' } },
    }),
  ) as Promise<Page<PurchaseOrder>>;
}

export async function createPurchaseOrder(
  requestId: string,
  body: { vendorId: string; paymentTerms?: string; deliveryDate?: string },
): Promise<PurchaseOrder> {
  return unwrap(
    api.POST('/api/v1/purchase-requests/{requestId}/purchase-orders', {
      params: { path: { requestId } },
      headers: { 'Idempotency-Key': idempotencyKey() },
      body,
    }),
  );
}

export async function issuePurchaseOrder(id: string, version: number): Promise<PurchaseOrder> {
  return unwrap(
    api.POST('/api/v1/purchase-orders/{id}/issue', {
      params: { path: { id } },
      headers: { 'If-Match': etag(version), 'Idempotency-Key': idempotencyKey() },
    }),
  );
}

export async function cancelPurchaseOrder(id: string, version: number): Promise<PurchaseOrder> {
  return unwrap(
    api.POST('/api/v1/purchase-orders/{id}/cancel', {
      params: { path: { id } },
      headers: { 'If-Match': etag(version), 'Idempotency-Key': idempotencyKey() },
    }),
  );
}

export async function purchaseOrderPdf(id: string): Promise<Blob> {
  return unwrap(
    api.GET('/api/v1/purchase-orders/{id}/pdf', {
      params: { path: { id } },
      parseAs: 'blob',
    }),
  );
}

export async function getPurchaseOrder(id: string): Promise<PurchaseOrder> {
  return unwrap(api.GET('/api/v1/purchase-orders/{id}', { params: { path: { id } } }));
}

export interface PurchaseOrderActions {
  actions: string[];
}

export async function getPurchaseOrderActions(id: string): Promise<PurchaseOrderActions> {
  return unwrap(
    api.GET('/api/v1/purchase-orders/{id}/actions', { params: { path: { id } } }),
  ) as Promise<PurchaseOrderActions>;
}

export async function updatePurchaseOrder(
  id: string,
  version: number,
  body: { paymentTerms?: string; deliveryDate?: string },
): Promise<PurchaseOrder> {
  return unwrap(
    api.PUT('/api/v1/purchase-orders/{id}', {
      params: { path: { id } },
      headers: { 'If-Match': etag(version) },
      body,
    }),
  );
}

export async function listGoodsReceipts(purchaseOrderId: string): Promise<GoodsReceipt[]> {
  return unwrap(
    api.GET('/api/v1/purchase-orders/{purchaseOrderId}/receipts', {
      params: { path: { purchaseOrderId } },
    }),
  );
}

export async function recordGoodsReceipt(
  purchaseOrderId: string,
  version: number,
  body: RecordReceiptRequest,
): Promise<GoodsReceipt> {
  return unwrap(
    api.POST('/api/v1/purchase-orders/{purchaseOrderId}/receipts', {
      params: { path: { purchaseOrderId } },
      headers: { 'If-Match': etag(version), 'Idempotency-Key': idempotencyKey() },
      body,
    }),
  );
}

export type PurchaseOrderApproval = components['schemas']['ApprovalView'];

export async function getPurchaseOrderApproval(id: string): Promise<PurchaseOrderApproval> {
  return unwrap(
    api.GET('/api/v1/purchase-orders/{id}/approval', { params: { path: { id } } }),
  ) as Promise<PurchaseOrderApproval>;
}

export async function submitPurchaseOrderForApproval(id: string): Promise<PurchaseOrderApproval> {
  return unwrap(
    api.POST('/api/v1/purchase-orders/{id}/submit-approval', {
      params: { path: { id } },
      headers: { 'Idempotency-Key': idempotencyKey() },
    }),
  ) as Promise<PurchaseOrderApproval>;
}

export async function approvePurchaseOrder(
  id: string,
  comment?: string,
): Promise<PurchaseOrderApproval> {
  return unwrap(
    api.POST('/api/v1/purchase-orders/{id}/approval/approve', {
      params: { path: { id } },
      body: { comment },
      headers: { 'Idempotency-Key': idempotencyKey() },
    }),
  ) as Promise<PurchaseOrderApproval>;
}

export async function rejectPurchaseOrder(
  id: string,
  comment: string,
): Promise<PurchaseOrderApproval> {
  return unwrap(
    api.POST('/api/v1/purchase-orders/{id}/approval/reject', {
      params: { path: { id } },
      body: { comment },
      headers: { 'Idempotency-Key': idempotencyKey() },
    }),
  ) as Promise<PurchaseOrderApproval>;
}

export async function requestPurchaseOrderChanges(
  id: string,
  comment: string,
): Promise<PurchaseOrderApproval> {
  return unwrap(
    api.POST('/api/v1/purchase-orders/{id}/approval/request-changes', {
      params: { path: { id } },
      body: { comment },
      headers: { 'Idempotency-Key': idempotencyKey() },
    }),
  ) as Promise<PurchaseOrderApproval>;
}
