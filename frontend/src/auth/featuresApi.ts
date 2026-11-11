import { api, unwrap } from '@/api/client';
import type { components } from '@/api/schema';

export type FeatureEntitlementsBody = components['schemas']['FeatureEntitlements'];

export async function getMyFeatures(): Promise<FeatureEntitlementsBody> {
  return unwrap(api.GET('/api/v1/me/features')) as Promise<FeatureEntitlementsBody>;
}
