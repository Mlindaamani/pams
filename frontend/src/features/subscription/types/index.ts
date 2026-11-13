import type { components } from '@/api/schema';

/** Subscription plan shown to tenants and platform administrators. */
export type SubscriptionPlan = components['schemas']['PlanView'];
/** Current tenant subscription state and commercial terms. */
export type TenantSubscription = components['schemas']['SubscriptionView'];
/** Feature usage counters for the current billing period. */
export type SubscriptionUsage = components['schemas']['Usage'];
/** Platform subscription summary for one tenant. */
export type SubscriptionSummary = components['schemas']['SubscriptionSummary'];
/** MRR, ARR, trial and churn metrics for the platform revenue dashboard. */
export type BillingMetrics = components['schemas']['BillingMetrics'];
/** Proration preview before changing a subscription plan. */
export type ChangePreview = components['schemas']['ChangePreview'];
/** Local-versus-Stripe subscription reconciliation report. */
export type ReconciliationReport = components['schemas']['ReconciliationReport'];
/** Platform plan catalogue entry. */
export type PlatformPlan = components['schemas']['PlanView'];
/** Request body for creating or updating a platform plan. */
export type PlanInput = components['schemas']['SubscriptionPlanRequest'];
/** Feature entitlement attached to a plan. */
export type SubscriptionFeature = components['schemas']['FeatureView'];
/** Request body for creating or updating a feature entitlement. */
export type FeatureInput = components['schemas']['SubscriptionFeatureRequest'];
