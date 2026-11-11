import { api, unwrap } from '@/api/client';

export interface PublicPlan {
  code: string;
  name: string;
  description: string;
  monthlyPrice: number;
  annualPrice: number;
  currency: string;
  trialDays: number;
  maxUsers?: number;
  maxDepartments?: number;
  maxRequestsMonth?: number;
  maxStorageMb?: number;
  features: string[];
}

export interface DemoRequestInput {
  name: string;
  email: string;
  company: string;
  phone?: string;
  message?: string;
}

export async function listPublicPlans(): Promise<PublicPlan[]> {
  return unwrap(api.GET('/api/v1/public/plans')) as unknown as Promise<PublicPlan[]>;
}

export async function submitDemoRequest(body: DemoRequestInput): Promise<void> {
  await unwrap(api.POST('/api/v1/public/demo-requests', { body }));
}
