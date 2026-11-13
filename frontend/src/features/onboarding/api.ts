import { api, unwrap } from '@/api/client';
import type { OnboardingChecklist } from './types';

export type { OnboardingChecklist } from './types';

export async function getOnboardingChecklist(): Promise<OnboardingChecklist> {
  return unwrap(api.GET('/api/v1/onboarding')) as Promise<OnboardingChecklist>;
}
