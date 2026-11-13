import { api, etag, idempotencyKey, unwrap } from '@/api/client';
import type { Page, RecordVendorInvoiceRequest, VendorInvoice } from '@/api/types';
import type { InvoiceStatus } from './types';

export async function listVendorInvoices(params: {
  status?: InvoiceStatus;
  purchaseOrderId?: string;
  invoiceNumber?: string;
  page?: number;
  size?: number;
}): Promise<Page<VendorInvoice>> {
  return unwrap(
    api.GET('/api/v1/vendor-invoices', {
      params: { query: { ...params, sort: 'createdAt,desc' } },
    }),
  ) as Promise<Page<VendorInvoice>>;
}

export async function listInvoicesForPurchaseOrder(
  purchaseOrderId: string,
): Promise<VendorInvoice[]> {
  return unwrap(
    api.GET('/api/v1/purchase-orders/{purchaseOrderId}/vendor-invoices', {
      params: { path: { purchaseOrderId } },
    }),
  );
}

export async function getVendorInvoice(id: string): Promise<VendorInvoice> {
  return unwrap(api.GET('/api/v1/vendor-invoices/{id}', { params: { path: { id } } }));
}

export async function recordVendorInvoice(
  purchaseOrderId: string,
  body: RecordVendorInvoiceRequest,
): Promise<VendorInvoice> {
  return unwrap(
    api.POST('/api/v1/purchase-orders/{purchaseOrderId}/vendor-invoices', {
      params: { path: { purchaseOrderId } },
      headers: { 'Idempotency-Key': idempotencyKey() },
      body,
    }),
  );
}

export async function approveVendorInvoice(id: string, version: number): Promise<VendorInvoice> {
  return unwrap(
    api.POST('/api/v1/vendor-invoices/{id}/approve', {
      params: { path: { id } },
      headers: { 'If-Match': etag(version), 'Idempotency-Key': idempotencyKey() },
    }),
  );
}

export async function vendorInvoicePdf(id: string): Promise<Blob> {
  return unwrap(
    api.GET('/api/v1/vendor-invoices/{id}/pdf', {
      params: { path: { id } },
      parseAs: 'blob',
    }),
  );
}

export async function rejectVendorInvoice(
  id: string,
  version: number,
  note: string,
): Promise<VendorInvoice> {
  return unwrap(
    api.POST('/api/v1/vendor-invoices/{id}/reject', {
      params: { path: { id } },
      headers: { 'If-Match': etag(version), 'Idempotency-Key': idempotencyKey() },
      body: { note },
    }),
  );
}

export async function disputeVendorInvoice(
  id: string,
  version: number,
  note: string,
): Promise<VendorInvoice> {
  return unwrap(
    api.POST('/api/v1/vendor-invoices/{id}/dispute', {
      params: { path: { id } },
      headers: { 'If-Match': etag(version), 'Idempotency-Key': idempotencyKey() },
      body: { note },
    }),
  );
}

export async function voidVendorInvoice(
  id: string,
  version: number,
  note: string,
): Promise<VendorInvoice> {
  return unwrap(
    api.POST('/api/v1/vendor-invoices/{id}/void', {
      params: { path: { id } },
      headers: { 'If-Match': etag(version), 'Idempotency-Key': idempotencyKey() },
      body: { note },
    }),
  );
}
