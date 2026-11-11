import { MutationCache, QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { RouterProvider } from 'react-router-dom';
import { AbilityProvider } from '@/auth/AbilityProvider';
import { BrandingProvider } from '@/branding/BrandingProvider';
import { ApiError, isConflict, problemMessage } from '@/api/client';
import { ToastProvider } from '@/components/Toast';
import { ThemeModeProvider } from '@/components/ThemeModeProvider';
import { router } from '@/routes';

const queryClient = new QueryClient({
  mutationCache: new MutationCache({
    onError: (error) => {
      const prefix = isConflict(error)
        ? 'The data changed on the server. Reload before trying again. '
        : '';
      // Lazy import avoids a circular dependency between the toast bus and the query client.
      void import('@/components/Toast').then(({ toast }) =>
        toast.error(`${prefix}${problemMessage(error)}`),
      );
    },
  }),
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: false,
      retry: (failureCount, error) =>
        failureCount < 2 && !(error instanceof ApiError && error.status < 500),
    },
    mutations: { retry: false },
  },
});

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrandingProvider>
        <ThemeModeProvider>
          <ToastProvider>
            <AbilityProvider>
              <RouterProvider router={router} />
            </AbilityProvider>
          </ToastProvider>
        </ThemeModeProvider>
      </BrandingProvider>
    </QueryClientProvider>
  );
}
