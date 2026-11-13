import type { components } from '@/api/schema';

/** Time-boxed approval delegation. */
export type Delegation = components['schemas']['DelegationResponse'];
/** Approval action selected in the inbox. */
export type ApprovalAction = 'approve' | 'reject' | 'request-changes';
