import type { paths } from '@/api/schema';

type RequestQuery = NonNullable<paths['/api/v1/purchase-requests']['get']['parameters']['query']>;
/** Purchase-request list filters. */
export type RequestFilters = RequestQuery;
/** Server-computed actions available for a purchase request. */
export interface RequestActions {
  actions: string[];
  approvalStepId?: string;
}
