export interface RuntimeConfig {
  apiBaseUrl: string;
  authority: string;
  clientId: string;
  redirectUri: string;
  postLogoutRedirectUri: string;
  scope: string;
}

declare global {
  interface Window {
    __APP_CONFIG__?: Partial<RuntimeConfig>;
  }
}

const origin = window.location.origin;

export const appConfig: RuntimeConfig = {
  apiBaseUrl:
    window.__APP_CONFIG__?.apiBaseUrl ?? (import.meta.env.VITE_API_BASE_URL as string) ?? '',
  authority:
    window.__APP_CONFIG__?.authority ??
    (import.meta.env.VITE_OIDC_AUTHORITY as string) ??
    'http://localhost:8081/realms/pams',
  clientId:
    window.__APP_CONFIG__?.clientId ??
    (import.meta.env.VITE_OIDC_CLIENT_ID as string) ??
    'pams-frontend',
  redirectUri: window.__APP_CONFIG__?.redirectUri ?? `${origin}/`,
  postLogoutRedirectUri: window.__APP_CONFIG__?.postLogoutRedirectUri ?? `${origin}/welcome`,
  scope: window.__APP_CONFIG__?.scope ?? 'openid profile email',
};
