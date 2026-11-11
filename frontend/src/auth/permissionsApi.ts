import { api, unwrap } from '@/api/client';

export interface PermissionsResponse {
  permissions: string[];
}

export async function getMyPermissions(): Promise<PermissionsResponse> {
  return unwrap(api.GET('/api/v1/me/permissions')) as Promise<PermissionsResponse>;
}
