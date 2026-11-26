import { useEffect } from 'react';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  CircularProgress,
  Stack,
  Typography,
} from '@mui/material';
import { useAuth } from 'react-oidc-context';

export function LoginPage() {
  const auth = useAuth();

  useEffect(() => {
    if (!auth.isLoading && !auth.isAuthenticated && !auth.activeNavigator) {
      void auth.signinRedirect();
    }
  }, [auth]);

  return (
    <Box
      sx={{
        minHeight: '100vh',
        display: 'grid',
        placeItems: 'center',
        p: 2,
        bgcolor: 'background.default',
      }}
    >
      <Card sx={{ width: '100%', maxWidth: 440 }}>
        <CardContent sx={{ p: { xs: 3, sm: 4 } }}>
          <Stack spacing={3}>
            <Stack spacing={0.75}>
              <Typography variant="overline" color="text.secondary" sx={{ letterSpacing: 1.5 }}>
                PAMS
              </Typography>
              <Typography variant="h5" component="h1">
                Sign in
              </Typography>
              <Typography variant="body2" color="text.secondary">
                {auth.error
                  ? 'The secure sign-in page could not be completed.'
                  : 'You are being redirected to the secure sign-in page.'}
              </Typography>
            </Stack>

            {auth.error ? (
              <Alert severity="error">
                {auth.error.message || 'Sign-in failed. Please try again.'}
              </Alert>
            ) : (
              <Stack direction="row" spacing={1.5} alignItems="center" role="status">
                <CircularProgress size={22} />
                <Typography variant="body2" color="text.secondary">
                  Connecting to Keycloak…
                </Typography>
              </Stack>
            )}

            <Button
              variant="contained"
              fullWidth
              onClick={() => void auth.signinRedirect()}
              disabled={auth.isLoading}
            >
              {auth.error ? 'Try again' : 'Open sign-in page'}
            </Button>
            <Typography variant="caption" color="text.secondary" textAlign="center">
              Use your organization account. Contact your administrator if you need access.
            </Typography>
          </Stack>
        </CardContent>
      </Card>
    </Box>
  );
}
