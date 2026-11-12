import { api, etag, idempotencyKey, unwrap } from '@/api/client';
import type { Page, WebhookCreated, WebhookDelivery, WebhookSubscription } from '@/api/types';

export interface WebhookInput {
  url: string;
  description?: string;
  eventTypes: string[];
  active: boolean;
}

export async function listWebhooks(): Promise<WebhookSubscription[]> {
  return unwrap(api.GET('/api/v1/webhooks'));
}

export async function createWebhook(body: WebhookInput): Promise<WebhookCreated> {
  return unwrap(
    api.POST('/api/v1/webhooks', {
      headers: { 'Idempotency-Key': idempotencyKey() },
      body,
    }),
  );
}

export async function updateWebhook(
  id: string,
  version: number,
  body: WebhookInput,
): Promise<WebhookSubscription> {
  return unwrap(
    api.PUT('/api/v1/webhooks/{id}', {
      params: { path: { id } },
      headers: { 'If-Match': etag(version) },
      body,
    }),
  );
}

export async function deleteWebhook(id: string, version: number): Promise<void> {
  await unwrap(
    api.DELETE('/api/v1/webhooks/{id}', {
      params: { path: { id } },
      headers: { 'If-Match': etag(version) },
    }),
  );
}

export async function rotateWebhookSecret(
  id: string,
  version: number,
): Promise<{ signingSecret?: string; version?: number }> {
  return unwrap(
    api.POST('/api/v1/webhooks/{id}/rotate-secret', {
      params: { path: { id } },
      headers: { 'If-Match': etag(version), 'Idempotency-Key': idempotencyKey() },
    }),
  );
}

export async function listWebhookDeliveries(id: string): Promise<WebhookDelivery[]> {
  return unwrap(
    api.GET('/api/v1/webhooks/{id}/deliveries', {
      params: { path: { id }, query: { limit: 50 } },
    }),
  );
}

export async function listDeliveryLog(params: {
  endpointId?: string;
  status?: string;
  eventType?: string;
  page?: number;
  size?: number;
}): Promise<Page<WebhookDelivery>> {
  return unwrap(
    api.GET('/api/v1/webhooks/deliveries', {
      params: {
        query: {
          endpointId: params.endpointId,
          status: params.status as never,
          eventType: params.eventType,
          page: params.page ?? 0,
          size: params.size ?? 100,
          sort: 'createdAt,desc',
        },
      },
    }),
  ) as Promise<Page<WebhookDelivery>>;
}

export async function sendWebhookTestEvent(id: string): Promise<WebhookDelivery> {
  return unwrap(
    api.POST('/api/v1/webhooks/{id}/test-event', {
      params: { path: { id } },
      headers: { 'Idempotency-Key': idempotencyKey() },
    }),
  );
}

export async function retryWebhookDelivery(id: string): Promise<WebhookDelivery> {
  return unwrap(
    api.POST('/api/v1/webhooks/deliveries/{id}/retry', {
      params: { path: { id } },
      headers: { 'Idempotency-Key': idempotencyKey() },
    }),
  );
}
