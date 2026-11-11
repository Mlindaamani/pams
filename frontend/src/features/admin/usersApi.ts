import { api, unwrap } from '@/api/client';
import type { Page, UserOption, UserProfile } from '@/api/types';

export async function listUsers(params: {
  departmentId?: string;
  active?: boolean;
  q?: string;
  page?: number;
  size?: number;
}): Promise<Page<UserProfile>> {
  return unwrap(
    api.GET('/api/v1/users', {
      params: { query: { ...params, sort: 'displayName,asc' } },
    }),
  ) as Promise<Page<UserProfile>>;
}

export async function getMe(): Promise<UserProfile> {
  return unwrap(api.GET('/api/v1/users/me'));
}

export async function delegationCandidates(): Promise<UserOption[]> {
  return unwrap(api.GET('/api/v1/users/delegation-candidates'));
}

export async function createUser(body: {
  keycloakSubject: string;
  email: string;
  displayName: string;
  departmentId: string;
  managerId?: string;
  approvalLimit?: { amount: string; currency: string };
}): Promise<UserProfile> {
  return unwrap(api.POST('/api/v1/users', { body }));
}

export async function updateUser(
  id: string,
  body: {
    displayName: string;
    departmentId: string;
    managerId?: string;
    approvalLimit?: { amount: string; currency: string };
    active: boolean;
  },
): Promise<UserProfile> {
  return unwrap(api.PUT('/api/v1/users/{id}', { params: { path: { id } }, body }));
}

export async function deleteUser(id: string): Promise<void> {
  await unwrap(api.DELETE('/api/v1/users/{id}', { params: { path: { id } } }));
}

export async function saveMyProfile(body: {
  displayName: string;
  departmentId: string;
  managerId?: string;
}): Promise<UserProfile> {
  return unwrap(api.PUT('/api/v1/users/me', { body }));
}

export async function uploadMyAvatar(file: File): Promise<UserProfile> {
  const form = new FormData();
  form.append('file', file);
  return unwrap(
    api.POST('/api/v1/users/me/avatar', {
      body: form as unknown as { file: string },
    }),
  );
}

export async function removeMyAvatar(): Promise<void> {
  await unwrap(api.DELETE('/api/v1/users/me/avatar'));
}

export async function myAvatar(): Promise<Blob> {
  return unwrap(
    api.GET('/api/v1/users/me/avatar', {
      parseAs: 'blob',
    }),
  );
}

export async function inviteUser(body: {
  email: string;
  displayName: string;
  departmentId: string;
  managerId?: string;
  roles: string[];
  approvalLimit?: { amount: string; currency: string };
}): Promise<UserProfile> {
  return unwrap(api.POST('/api/v1/users/invitations', { body }));
}
