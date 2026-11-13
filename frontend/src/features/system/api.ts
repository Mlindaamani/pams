import { api, unwrap } from '@/api/client';
import type { SystemStatus } from './types';

export type { SystemStatus } from './types';

export async function getSystemStatus(): Promise<SystemStatus> {
  return unwrap(api.GET('/api/v1/admin/system/status'));
}
