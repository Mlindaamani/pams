import { api, unwrap } from '@/api/client';
import type { AuditPage } from '@/api/types';

export async function auditPage(params: {
  cursor?: string;
  limit?: number;
  aggregateType?: string;
  aggregateId?: string;
  action?: string;
  from?: string;
  to?: string;
}): Promise<AuditPage> {
  return unwrap(api.GET('/api/v1/audit-events', { params: { query: params } }));
}
