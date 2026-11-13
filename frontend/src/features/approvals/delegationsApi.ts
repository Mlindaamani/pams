import { api, unwrap } from '@/api/client';
import type { Delegation } from './types';

export type { Delegation } from './types';

export async function listMyDelegations(): Promise<Delegation[]> {
  return unwrap(api.GET('/api/v1/delegations/mine'));
}

export async function createDelegation(body: {
  delegateUserId: string;
  startsAt: string;
  endsAt: string;
}): Promise<Delegation> {
  return unwrap(api.POST('/api/v1/delegations', { body }));
}

export async function revokeDelegation(id: string): Promise<void> {
  await unwrap(api.DELETE('/api/v1/delegations/{id}', { params: { path: { id } } }));
}
