import type { components } from '@/api/schema';

/** Server-computed actions available for a purchase order. */
export interface PurchaseOrderActions {
  actions: string[];
  invoiceableQuantity?: number;
  receivedQuantity?: number;
  invoicedQuantity?: number;
}

/** Purchase-order approval timeline. */
export type PurchaseOrderApproval = components['schemas']['ApprovalView'];
