import { api, unwrap } from '@/api/client';

import type { DemoRequestInput, PublicPlan } from './types';

export type { DemoRequestInput, PublicPlan } from './types';

export async function listPublicPlans(): Promise<PublicPlan[]> {
  return unwrap(api.GET('/api/v1/public/plans')) as unknown as Promise<PublicPlan[]>;
}

export async function submitDemoRequest(body: DemoRequestInput): Promise<void> {
  await unwrap(api.POST('/api/v1/public/demo-requests', { body }));
}
