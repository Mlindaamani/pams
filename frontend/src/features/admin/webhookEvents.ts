import type { WebhookEventGroup } from './types';

export type { WebhookEventGroup, WebhookEventOption } from './types';

export const WEBHOOK_EVENT_GROUPS: WebhookEventGroup[] = [
  {
    id: 'requests',
    label: 'Requests',
    helperText:
      'Follow each request from submission through approval, changes, cancellation and conversion to a purchase order.',
    events: [
      { value: 'RequestSubmitted', label: 'Submitted' },
      { value: 'RequestApproved', label: 'Approved' },
      { value: 'RequestRejected', label: 'Rejected' },
      { value: 'RequestChangesRequested', label: 'Changes requested' },
      { value: 'RequestCancelled', label: 'Cancelled' },
      { value: 'RequestConvertedToPurchaseOrder', label: 'Converted to purchase order' },
    ],
  },
  {
    id: 'purchase_orders',
    label: 'Purchase orders',
    helperText:
      'Keep ERP, vendor and internal systems aligned when orders are approved, issued or cancelled.',
    events: [
      { value: 'PurchaseOrderIssued', label: 'Issued' },
      { value: 'PurchaseOrderApproved', label: 'Approved' },
      { value: 'PurchaseOrderCancelled', label: 'Cancelled' },
    ],
  },
  {
    id: 'goods_receipts',
    label: 'Goods receipts',
    helperText: 'Trigger receiving, inventory and project-delivery updates when goods arrive.',
    events: [{ value: 'GoodsReceiptRecorded', label: 'Recorded' }],
  },
  {
    id: 'vendor_invoices',
    label: 'Vendor invoices',
    helperText:
      'Send recorded, approved, disputed, rejected and paid invoice updates to finance and accounting systems.',
    events: [
      { value: 'VendorInvoiceRecorded', label: 'Recorded' },
      { value: 'VendorInvoiceApproved', label: 'Approved' },
      { value: 'VendorInvoiceDisputed', label: 'Disputed' },
      { value: 'VendorInvoiceRejected', label: 'Rejected' },
      { value: 'VendorInvoicePaid', label: 'Paid' },
    ],
  },
  {
    id: 'vendor_payments',
    label: 'Vendor payments',
    helperText:
      'Keep treasury and cash-flow systems informed as payments are scheduled, approved, paid or cancelled.',
    events: [
      { value: 'VendorPaymentScheduled', label: 'Scheduled' },
      { value: 'VendorPaymentApproved', label: 'Approved' },
      { value: 'VendorPaymentPaid', label: 'Paid' },
      { value: 'VendorPaymentCancelled', label: 'Cancelled' },
    ],
  },
  {
    id: 'vendors',
    label: 'Vendors',
    helperText: 'Notify consuming systems when a vendor becomes active, suspended or blacklisted.',
    events: [{ value: 'VendorStatusChanged', label: 'Status changed' }],
  },
];

export const WEBHOOK_EVENTS = WEBHOOK_EVENT_GROUPS.flatMap((group) => group.events);

const GROUP_SUBJECTS: Record<string, string> = {
  requests: 'Request',
  purchase_orders: 'Purchase order',
  goods_receipts: 'Goods receipt',
  vendor_invoices: 'Vendor invoice',
  vendor_payments: 'Vendor payment',
  vendors: 'Vendor',
};

export const WEBHOOK_DELIVERY_EVENTS = [
  ...WEBHOOK_EVENT_GROUPS.flatMap((group) =>
    group.events.map((event) => ({
      value: event.value,
      label: `${GROUP_SUBJECTS[group.id]} ${event.label.toLowerCase()}`,
    })),
  ),
  { value: 'WebhookTest', label: 'Test event' },
];

export function webhookEventLabel(eventType?: string): string {
  return (
    WEBHOOK_DELIVERY_EVENTS.find((event) => event.value === eventType)?.label ?? eventType ?? '—'
  );
}
