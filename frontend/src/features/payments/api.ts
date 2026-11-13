import { api, idempotencyKey, unwrap } from '@/api/client';
import type { CreatePaymentRequest, Payment } from '@/api/types';

export async function listPayments(): Promise<Payment[]> {
  return unwrap(api.GET('/api/v1/vendor-payments'));
}

export async function createPayment(body: CreatePaymentRequest): Promise<Payment> {
  return unwrap(
    api.POST('/api/v1/vendor-payments', {
      headers: { 'Idempotency-Key': idempotencyKey() },
      body,
    }),
  );
}

export async function approvePayment(id: string): Promise<Payment> {
  return unwrap(
    api.POST('/api/v1/vendor-payments/{id}/approve', {
      params: { path: { id } },
      headers: { 'Idempotency-Key': idempotencyKey() },
    }),
  );
}

export async function payPayment(id: string): Promise<Payment> {
  return unwrap(
    api.POST('/api/v1/vendor-payments/{id}/pay', {
      params: { path: { id } },
      headers: { 'Idempotency-Key': idempotencyKey() },
    }),
  );
}

export async function cancelPayment(id: string): Promise<Payment> {
  return unwrap(
    api.POST('/api/v1/vendor-payments/{id}/cancel', {
      params: { path: { id } },
      headers: { 'Idempotency-Key': idempotencyKey() },
    }),
  );
}

import type { AgingReport } from './types';

export type { AgingReport } from './types';

export async function getPaymentAging(): Promise<AgingReport> {
  return unwrap(api.GET('/api/v1/vendor-payments/aging'));
}
