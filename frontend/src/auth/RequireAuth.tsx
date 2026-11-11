import { type PropsWithChildren } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from 'react-oidc-context';
import { Box, Skeleton, Stack } from '@mui/material';
import { rememberReturnPath } from '@/components/ReturnToHandler';

export function RequireAuth({ children }: PropsWithChildren) {
  const auth = useAuth();
  const location = useLocation();

  if (auth.isLoading) {
    return (
      <Stack spacing={2} sx={{ p: { xs: 2, md: 4 }, minHeight: '60vh' }}>
        <Skeleton variant="text" width="35%" height={44} />
        <Box
          sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: 'repeat(3, 1fr)' }, gap: 2 }}
        >
          <Skeleton variant="rounded" height={100} />
          <Skeleton variant="rounded" height={100} />
          <Skeleton variant="rounded" height={100} />
        </Box>
        <Skeleton variant="rounded" height={260} />
      </Stack>
    );
  }
  if (!auth.isAuthenticated) {
    rememberReturnPath(`${location.pathname}${location.search}`);
    return <Navigate to="/login" replace state={{ from: location }} />;
  }
  return <>{children}</>;
}
