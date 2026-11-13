import type { paths } from '@/api/schema';

/** Vendor invoice lifecycle status filter. */
export type InvoiceStatus = NonNullable<
  paths['/api/v1/vendor-invoices']['get']['parameters']['query']
>['status'];
