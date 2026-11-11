import { useEffect } from 'react';
import { Box, Button, Skeleton, Stack, Typography } from '@mui/material';
import { useAuth } from 'react-oidc-context';
import { useThemeMode } from '@/components/ThemeModeProvider';

export function LoginPage() {
  const auth = useAuth();
  const { mode } = useThemeMode();

  useEffect(() => {
    if (!auth.isLoading && !auth.isAuthenticated && !auth.activeNavigator) {
      void auth.signinRedirect();
    }
  }, [auth]);

  if (auth.error) {
    return (
      <Box sx={{ p: 4 }}>
        <Typography variant="h6" color="error">
          Sign-in failed
        </Typography>
        <Typography>{auth.error.message}</Typography>
        <Button sx={{ mt: 2 }} variant="contained" onClick={() => void auth.signinRedirect()}>
          Try again
        </Button>
      </Box>
    );
  }

  return (
    <Stack alignItems="center" justifyContent="center" sx={{ minHeight: '100vh' }} spacing={3}>
      <Box
        component="img"
        src={mode === 'dark' ? '/logo/pams-logo-reverse.svg' : '/logo/pams-logo.svg'}
        alt="PAMS"
        sx={{ width: 320, maxWidth: '85vw' }}
      />
      <Stack
        spacing={1.5}
        sx={{ width: 320, maxWidth: '85vw' }}
        aria-busy="true"
        aria-label="Signing in"
      >
        <Skeleton variant="rounded" height={42} />
        <Skeleton variant="rounded" height={42} />
        <Skeleton variant="text" width="70%" />
      </Stack>
      <Typography color="text.secondary">Redirecting to Keycloak…</Typography>
    </Stack>
  );
}
