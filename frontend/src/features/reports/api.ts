import { api, unwrap } from '@/api/client';
import type { CommitmentsReport, CycleTimeReport, SpendReport } from '@/api/types';

export async function spendReport(
  groupBy: string,
  from?: string,
  to?: string,
): Promise<SpendReport> {
  return unwrap(api.GET('/api/v1/reports/spend', { params: { query: { groupBy, from, to } } }));
}

export async function spendCsv(groupBy: string, from?: string, to?: string): Promise<Blob> {
  return unwrap(
    api.GET('/api/v1/reports/spend.csv', {
      params: { query: { groupBy, from, to } },
      parseAs: 'blob',
    }),
  );
}

export async function cycleTimeReport(
  groupBy: string,
  from?: string,
  to?: string,
): Promise<CycleTimeReport> {
  return unwrap(
    api.GET('/api/v1/reports/approval-cycle-time', {
      params: { query: { groupBy, from, to } },
    }),
  );
}

export async function commitmentsReport(): Promise<CommitmentsReport> {
  return unwrap(api.GET('/api/v1/reports/commitments'));
}
