import { appConfig } from '@/config';

export function accountSecurityUrl(): string {
  return `${appConfig.authority.replace(/\/$/, '')}/account`;
}
