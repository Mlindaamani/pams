import { api, unwrap } from '@/api/client';
import type { components } from '@/api/schema';

export type OnboardingChecklist = components['schemas']['Checklist'];

export async function getOnboardingChecklist(): Promise<OnboardingChecklist> {
  return unwrap(api.GET('/api/v1/onboarding')) as Promise<OnboardingChecklist>;
}
