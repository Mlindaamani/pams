import { Link as RouterLink } from 'react-router-dom';
import { useAuth } from 'react-oidc-context';
import { Alert, Box, Button, Card, CardContent, Divider, Stack, Typography } from '@mui/material';
import KeyIcon from '@mui/icons-material/Key';
import SecurityIcon from '@mui/icons-material/Security';
import OpenInNewIcon from '@mui/icons-material/OpenInNew';
import { PageHeader } from '@/components/Page';
import { accountSecurityUrl } from '@/auth/accountSecurity';

type SecurityAction = 'UPDATE_PASSWORD' | 'CONFIGURE_TOTP';

export function SecurityPage() {
  const auth = useAuth();

  const openAction = (action: SecurityAction) => {
    void auth.signinRedirect({
      redirect_uri: `${window.location.origin}/profile/security`,
      extraQueryParams: { kc_action: action },
    });
  };

  return (
    <>
      <PageHeader title="Account security" backTo="/profile" backLabel="Back to profile" />
      <Stack spacing={2} sx={{ maxWidth: 840 }}>
        <Alert severity="info">
          Password and authenticator changes are handled by the secure PAMS sign-in service.
        </Alert>

        <Card>
          <CardContent>
            <Stack direction="row" spacing={1.5} alignItems="flex-start">
              <KeyIcon color="primary" />
              <Box sx={{ flex: 1 }}>
                <Typography variant="h6">Password</Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                  Change the password used to sign in to your PAMS account.
                </Typography>
                <Button
                  variant="contained"
                  disabled={auth.isLoading}
                  onClick={() => openAction('UPDATE_PASSWORD')}
                >
                  Change password
                </Button>
              </Box>
            </Stack>
          </CardContent>
        </Card>

        <Card>
          <CardContent>
            <Stack direction="row" spacing={1.5} alignItems="flex-start">
              <SecurityIcon color="primary" />
              <Box sx={{ flex: 1 }}>
                <Typography variant="h6">Multi-factor authentication</Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                  Add a time-based one-time password (TOTP) authenticator for stronger sign-in
                  protection.
                </Typography>
                <Button
                  variant="outlined"
                  disabled={auth.isLoading}
                  onClick={() => openAction('CONFIGURE_TOTP')}
                >
                  Set up authenticator
                </Button>
              </Box>
            </Stack>
          </CardContent>
        </Card>

        <Card>
          <CardContent>
            <Typography variant="h6">Sessions and connected applications</Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
              Review active sessions, connected applications and other account settings in the
              Keycloak account console.
            </Typography>
            <Divider sx={{ mb: 2 }} />
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
              <Button
                component="a"
                href={accountSecurityUrl()}
                target="_blank"
                rel="noopener noreferrer"
                variant="outlined"
                startIcon={<SecurityIcon />}
                endIcon={<OpenInNewIcon fontSize="small" />}
              >
                Open Keycloak account
              </Button>
              <Button component={RouterLink} to="/profile">
                Back to profile
              </Button>
            </Stack>
          </CardContent>
        </Card>
      </Stack>
    </>
  );
}
