import { api, unwrap } from '@/api/client';
import type { components } from '@/api/schema';

export type SubscriptionPlan = components['schemas']['PlanView'];
export type TenantSubscription = components['schemas']['SubscriptionView'];
export type SubscriptionUsage = components['schemas']['Usage'];
export type SubscriptionSummary = components['schemas']['SubscriptionSummary'];
export type BillingMetrics = components['schemas']['BillingMetrics'];
export type ChangePreview = components['schemas']['ChangePreview'];
export type ReconciliationReport = components['schemas']['ReconciliationReport'];

export async function listSubscriptionPlans(): Promise<SubscriptionPlan[]> {
  return unwrap(api.GET('/api/v1/subscription/plans'));
}

export async function getSubscription(): Promise<TenantSubscription> {
  return unwrap(api.GET('/api/v1/subscription'));
}

export async function startCheckout(planCode: string, interval: 'MONTHLY' | 'ANNUAL') {
  return unwrap(api.POST('/api/v1/subscription/checkout', { body: { planCode, interval } }));
}

export async function openBillingPortal() {
  return unwrap(api.POST('/api/v1/subscription/portal'));
}

export async function listSubscriptionSummaries(): Promise<SubscriptionSummary[]> {
  return unwrap(api.GET('/api/v1/platform/subscriptions'));
}

export async function startPlatformCheckout(
  tenantId: string,
  planCode: string,
  interval: 'MONTHLY' | 'ANNUAL',
) {
  return unwrap(
    api.POST('/api/v1/platform/tenants/{id}/subscription/checkout', {
      params: { path: { id: tenantId } },
      body: { planCode, interval },
    }),
  );
}

export async function listPlatformSubscriptionPlans(): Promise<SubscriptionPlan[]> {
  return unwrap(api.GET('/api/v1/platform/subscriptions/plans'));
}

export async function getBillingMetrics(): Promise<BillingMetrics> {
  return unwrap(api.GET('/api/v1/platform/subscriptions/metrics'));
}

export async function changeTenantPlan(
  tenantId: string,
  planCode: string,
  billingInterval: 'MONTHLY' | 'ANNUAL',
) {
  return unwrap(
    api.POST('/api/v1/platform/tenants/{id}/subscription/change-plan', {
      params: { path: { id: tenantId } },
      body: { planCode, billingInterval },
    }),
  );
}

export async function extendTenantTrial(tenantId: string, days: number) {
  return unwrap(
    api.POST('/api/v1/platform/tenants/{id}/subscription/extend-trial', {
      params: { path: { id: tenantId } },
      body: { days },
    }),
  );
}

export async function activateTenantSubscription(tenantId: string) {
  return unwrap(
    api.POST('/api/v1/platform/tenants/{id}/subscription/activate', {
      params: { path: { id: tenantId } },
    }),
  );
}

export async function cancelTenantSubscription(tenantId: string, atPeriodEnd: boolean) {
  return unwrap(
    api.POST('/api/v1/platform/tenants/{id}/subscription/cancel', {
      params: { path: { id: tenantId } },
      body: { atPeriodEnd },
    }),
  );
}

export async function reactivateTenantSubscription(tenantId: string) {
  return unwrap(
    api.POST('/api/v1/platform/tenants/{id}/subscription/reactivate', {
      params: { path: { id: tenantId } },
    }),
  );
}

export type PlatformPlan = components['schemas']['PlanView'];
export type PlanInput = components['schemas']['SubscriptionPlanRequest'];
export type SubscriptionFeature = components['schemas']['FeatureView'];
export type FeatureInput = components['schemas']['SubscriptionFeatureRequest'];

export async function listAllPlatformPlans(): Promise<PlatformPlan[]> {
  return unwrap(api.GET('/api/v1/platform/subscription-plans'));
}

export async function createPlatformPlan(body: PlanInput): Promise<PlatformPlan> {
  return unwrap(api.POST('/api/v1/platform/subscription-plans', { body }));
}

export async function updatePlatformPlan(code: string, body: PlanInput): Promise<PlatformPlan> {
  return unwrap(
    api.PUT('/api/v1/platform/subscription-plans/{code}', {
      params: { path: { code } },
      body,
    }),
  );
}

export async function deactivatePlatformPlan(code: string): Promise<void> {
  await unwrap(
    api.DELETE('/api/v1/platform/subscription-plans/{code}', {
      params: { path: { code } },
    }),
  );
}

export async function listSubscriptionFeatures(): Promise<SubscriptionFeature[]> {
  return unwrap(api.GET('/api/v1/platform/subscription-features'));
}

export async function createSubscriptionFeature(body: FeatureInput): Promise<SubscriptionFeature> {
  return unwrap(api.POST('/api/v1/platform/subscription-features', { body }));
}

export async function updateSubscriptionFeature(
  code: string,
  body: FeatureInput,
): Promise<SubscriptionFeature> {
  return unwrap(
    api.PUT('/api/v1/platform/subscription-features/{code}', {
      params: { path: { code } },
      body,
    }),
  );
}

export async function deactivateSubscriptionFeature(code: string): Promise<void> {
  await unwrap(
    api.DELETE('/api/v1/platform/subscription-features/{code}', {
      params: { path: { code } },
    }),
  );
}

export async function reconcileTenantSubscription(tenantId: string) {
  return unwrap(
    api.POST('/api/v1/platform/tenants/{id}/subscription/reconcile', {
      params: { path: { id: tenantId } },
    }),
  );
}

export async function previewPlanChange(planCode: string, interval: 'MONTHLY' | 'ANNUAL') {
  return unwrap(
    api.POST('/api/v1/subscription/change-preview', {
      body: { planCode, interval },
    }),
  );
}

export async function auditSubscriptionReconciliation(): Promise<ReconciliationReport> {
  return unwrap(api.GET('/api/v1/platform/subscriptions/reconciliation'));
}
