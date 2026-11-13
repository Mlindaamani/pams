/** Public plan shown on the pricing page. */
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

/** Public demo-request form payload. */
export interface DemoRequestInput {
  name: string;
  email: string;
  company: string;
  phone?: string;
  message?: string;
}

/** Billing interval selected on the pricing page. */
export type BillingCycle = 'MONTHLY' | 'ANNUAL';
