import { api, unwrap } from '@/api/client';
import type { components } from '@/api/schema';

export type SystemStatus = components['schemas']['SystemStatusResponse'];

export async function getSystemStatus(): Promise<SystemStatus> {
  return unwrap(api.GET('/api/v1/admin/system/status'));
}
