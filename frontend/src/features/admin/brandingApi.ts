import { api, unwrap } from '@/api/client';
import type { BrandingInput, BrandingView } from './types';

export type { BrandingInput, BrandingView } from './types';

export async function getBranding(): Promise<BrandingView> {
  return unwrap(api.GET('/api/v1/branding'));
}

export async function updateBranding(body: BrandingInput): Promise<BrandingView> {
  return unwrap(api.PUT('/api/v1/branding', { body }));
}

export async function uploadBrandingLogo(file: File): Promise<BrandingView> {
  const form = new FormData();
  form.append('file', file);
  return unwrap(
    api.POST('/api/v1/branding/logo', {
      body: form as unknown as { file: string },
    }),
  );
}

export async function removeBrandingLogo(): Promise<BrandingView> {
  return unwrap(api.DELETE('/api/v1/branding/logo'));
}

export async function getBrandingLogo(): Promise<Blob> {
  return unwrap(api.GET('/api/v1/branding/logo', { parseAs: 'blob' }));
}

export async function uploadBrandingFavicon(file: File): Promise<BrandingView> {
  const form = new FormData();
  form.append('file', file);
  return unwrap(
    api.POST('/api/v1/branding/favicon', {
      body: form as unknown as { file: string },
    }),
  );
}

export async function removeBrandingFavicon(): Promise<BrandingView> {
  return unwrap(api.DELETE('/api/v1/branding/favicon'));
}

export async function getBrandingFavicon(): Promise<Blob> {
  return unwrap(api.GET('/api/v1/branding/favicon', { parseAs: 'blob' }));
}
