import { describe, expect, it } from 'vitest';
import {
  WEBHOOK_DELIVERY_EVENTS,
  WEBHOOK_EVENTS,
  WEBHOOK_EVENT_GROUPS,
  webhookEventLabel,
} from '@/features/admin/webhookEvents';

describe('webhook event catalogue', () => {
  it('exposes 20 lifecycle events in six groups', () => {
    expect(WEBHOOK_EVENTS).toHaveLength(20);
    expect(WEBHOOK_EVENT_GROUPS).toHaveLength(6);
    expect(WEBHOOK_DELIVERY_EVENTS).toHaveLength(21);
    expect(new Set(WEBHOOK_EVENTS.map((event) => event.value)).size).toBe(20);
  });

  it('labels lifecycle and test events', () => {
    expect(webhookEventLabel('RequestConvertedToPurchaseOrder')).toBe(
      'Request converted to purchase order',
    );
    expect(webhookEventLabel('WebhookTest')).toBe('Test event');
    expect(webhookEventLabel('UnknownEvent')).toBe('UnknownEvent');
  });
});
