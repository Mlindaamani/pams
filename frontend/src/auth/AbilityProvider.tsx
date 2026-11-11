import { createContext, useCallback, useContext, useMemo, type PropsWithChildren } from 'react';
import { createContextualCan } from '@casl/react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useAuth } from 'react-oidc-context';
import { setApiToken } from '@/api/client';
import { defineAbilityFor, type AppAbility } from './ability';
import { getMyPermissions } from './permissionsApi';
import { getMyFeatures } from './featuresApi';

export const AbilityContext = createContext<AppAbility>(defineAbilityFor([]));
export const Can = createContextualCan(AbilityContext.Consumer);

let appliedToken: string | null = null;

export function AbilityProvider({ children }: PropsWithChildren) {
  const auth = useAuth();
  const token = auth.user?.access_token ?? null;
  const subject = auth.user?.profile.sub;

  // Set the token before queries run so the permissions request is authenticated.
  if (token !== appliedToken) {
    appliedToken = token;
    setApiToken(token);
  }

  const permissionsQuery = useQuery({
    queryKey: ['me', 'permissions', subject],
    queryFn: getMyPermissions,
    enabled: Boolean(token),
    staleTime: 5 * 60 * 1000,
  });
  const featuresQuery = useQuery({
    queryKey: ['me', 'features', subject],
    queryFn: getMyFeatures,
    enabled: Boolean(token),
    staleTime: 30 * 1000,
    refetchOnWindowFocus: true,
  });
  const permissions = permissionsQuery.data?.permissions;
  const features = featuresQuery.data?.features;
  const ability = useMemo(
    () => defineAbilityFor(permissions ?? [], features ?? []),
    [permissions, features],
  );

  return <AbilityContext.Provider value={ability}>{children}</AbilityContext.Provider>;
}

export function useAbility(): AppAbility {
  return useContext(AbilityContext);
}

export function useRefreshEntitlements() {
  const auth = useAuth();
  const queryClient = useQueryClient();
  const subject = auth.user?.profile.sub;
  return useCallback(() => {
    void queryClient.invalidateQueries({ queryKey: ['me', 'permissions', subject] });
    void queryClient.invalidateQueries({ queryKey: ['me', 'features', subject] });
  }, [queryClient, subject]);
}
