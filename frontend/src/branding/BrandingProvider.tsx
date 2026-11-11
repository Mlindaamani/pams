import { createContext, useContext, useEffect, useMemo } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useAuth } from 'react-oidc-context';
import { setApiToken } from '@/api/client';
import { rolesFromAccessToken } from '@/auth/ability';
import { getBranding, getBrandingFavicon, getBrandingLogo } from '@/features/admin/brandingApi';

export interface Branding {
  displayName: string;
  primaryColor: string;
  secondaryColor: string;
  emailFromName?: string;
  supportEmail?: string;
  hasLogo: boolean;
  hasFavicon: boolean;
  cornerRadius: number;
  fontFamily: string;
  uiDensity: string;
  requestLabel: string;
  orderLabel: string;
  vendorLabel: string;
  budgetLabel: string;
  catalogLabel: string;
  version: number;
}

const PAMS_BRANDING: Branding = {
  displayName: 'PAMS',
  primaryColor: '#0B5CAD',
  secondaryColor: '#00695C',
  hasLogo: false,
  hasFavicon: false,
  cornerRadius: 10,
  fontFamily: 'INTER',
  uiDensity: 'COMFORTABLE',
  requestLabel: 'Requests',
  orderLabel: 'Purchase orders',
  vendorLabel: 'Vendors',
  budgetLabel: 'Budgets',
  catalogLabel: 'Catalog items',
  version: 0,
};

interface BrandingContextValue {
  branding: Branding;
  logoUrl: string | null;
  refresh: () => void;
}

const BrandingContext = createContext<BrandingContextValue>({
  branding: PAMS_BRANDING,
  logoUrl: null,
  refresh: () => undefined,
});

let appliedToken: string | null = null;

export function BrandingProvider({ children }: { children: React.ReactNode }) {
  const auth = useAuth();
  const token = auth.user?.access_token ?? null;
  const subject = auth.user?.profile.sub;
  const platformAdmin = rolesFromAccessToken(token ?? undefined).includes('PLATFORM_ADMIN');
  const tenantBrandingEnabled = Boolean(token) && !platformAdmin;
  const queryClient = useQueryClient();

  if (token !== appliedToken) {
    appliedToken = token;
    setApiToken(token);
  }

  const brandingQuery = useQuery({
    queryKey: ['branding', subject],
    queryFn: getBranding,
    enabled: tenantBrandingEnabled,
    staleTime: 60_000,
  });
  const branding: Branding = brandingQuery.data
    ? {
        displayName: brandingQuery.data.displayName ?? 'PAMS',
        primaryColor: brandingQuery.data.primaryColor ?? PAMS_BRANDING.primaryColor,
        secondaryColor: brandingQuery.data.secondaryColor ?? PAMS_BRANDING.secondaryColor,
        emailFromName: brandingQuery.data.emailFromName,
        supportEmail: brandingQuery.data.supportEmail,
        hasLogo: brandingQuery.data.hasLogo ?? false,
        hasFavicon: brandingQuery.data.hasFavicon ?? false,
        cornerRadius: brandingQuery.data.cornerRadius ?? 10,
        fontFamily: brandingQuery.data.fontFamily ?? 'INTER',
        uiDensity: brandingQuery.data.uiDensity ?? 'COMFORTABLE',
        requestLabel: brandingQuery.data.requestLabel ?? 'Requests',
        orderLabel: brandingQuery.data.orderLabel ?? 'Purchase orders',
        vendorLabel: brandingQuery.data.vendorLabel ?? 'Vendors',
        budgetLabel: brandingQuery.data.budgetLabel ?? 'Budgets',
        catalogLabel: brandingQuery.data.catalogLabel ?? 'Catalog items',
        version: brandingQuery.data.version ?? 0,
      }
    : PAMS_BRANDING;

  const logoQuery = useQuery({
    queryKey: ['branding', subject, 'logo', branding.version],
    queryFn: getBrandingLogo,
    enabled: tenantBrandingEnabled && branding.hasLogo,
    staleTime: 60_000,
  });
  const logoUrl = useMemo(
    () => (logoQuery.data ? URL.createObjectURL(logoQuery.data) : null),
    [logoQuery.data],
  );

  const faviconQuery = useQuery({
    queryKey: ['branding', subject, 'favicon', branding.version],
    queryFn: getBrandingFavicon,
    enabled: tenantBrandingEnabled && branding.hasFavicon,
    staleTime: 60_000,
  });
  const faviconUrl = useMemo(
    () => (faviconQuery.data ? URL.createObjectURL(faviconQuery.data) : null),
    [faviconQuery.data],
  );

  useEffect(() => {
    if (!logoUrl) return;
    return () => URL.revokeObjectURL(logoUrl);
  }, [logoUrl]);

  useEffect(() => {
    const link = document.querySelector<HTMLLinkElement>('link[rel~="icon"]');
    if (!link) return;
    link.href = faviconUrl ?? '/logo/pams-icon.svg';
    return () => {
      if (faviconUrl) URL.revokeObjectURL(faviconUrl);
    };
  }, [faviconUrl]);

  useEffect(() => {
    document.title = branding.displayName === 'PAMS' ? 'PAMS' : `${branding.displayName} · PAMS`;
    document.documentElement.style.setProperty('--pams-primary', branding.primaryColor);
    document.documentElement.style.setProperty('--pams-secondary', branding.secondaryColor);
  }, [branding.displayName, branding.primaryColor, branding.secondaryColor]);

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: ['branding', subject] });
  };

  return (
    <BrandingContext.Provider value={{ branding, logoUrl, refresh }}>
      {children}
    </BrandingContext.Provider>
  );
}

export function useBranding() {
  return useContext(BrandingContext);
}
