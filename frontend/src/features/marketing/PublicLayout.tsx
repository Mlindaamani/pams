import { Link as RouterLink } from 'react-router-dom';
import { Box, Button, Stack, Typography } from '@mui/material';
import ArrowForwardIcon from '@mui/icons-material/ArrowForward';
import { useBranding } from '@/branding/BrandingProvider';

export function PublicLayout({ children }: { children: React.ReactNode }) {
  const { branding } = useBranding();
  return (
    <Box sx={{ minHeight: '100vh', bgcolor: 'background.default' }}>
      <Box component="header" sx={{ bgcolor: '#0B5CAD', color: '#FFFFFF', py: 1.5 }}>
        <Box sx={{ width: '100%', px: { xs: 2, sm: 4, md: 6, lg: 8 } }}>
          <Stack direction="row" alignItems="center" spacing={2}>
            <Box
              component={RouterLink}
              to="/welcome"
              sx={{ display: 'flex', alignItems: 'center', textDecoration: 'none' }}
            >
              <Box
                component="img"
                src="/logo/pams-logo-reverse.svg"
                alt="PAMS"
                sx={{ height: 36 }}
              />
              {branding.displayName !== 'PAMS' && (
                <Typography sx={{ color: '#FFFFFF', ml: 1.5, fontWeight: 700 }}>
                  {branding.displayName}
                </Typography>
              )}
            </Box>
            <Box sx={{ flexGrow: 1 }} />
            <Button component={RouterLink} to="/pricing" color="inherit">
              Pricing
            </Button>
            <Button
              component={RouterLink}
              to="/demo"
              color="inherit"
              sx={{ display: { xs: 'none', sm: 'inline-flex' } }}
            >
              Request demo
            </Button>
            <Button
              component={RouterLink}
              to="/login"
              variant="contained"
              color="secondary"
              endIcon={<ArrowForwardIcon />}
              sx={{ whiteSpace: 'nowrap' }}
            >
              Sign in
            </Button>
          </Stack>
        </Box>
      </Box>
      <Box component="main">{children}</Box>
      <Box
        component="footer"
        sx={{ borderTop: 1, borderColor: 'divider', py: 4, mt: 8, bgcolor: 'background.paper' }}
      >
        <Box sx={{ width: '100%', px: { xs: 2, sm: 4, md: 6, lg: 8 } }}>
          <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" spacing={2}>
            <Typography variant="body2" color="text.secondary">
              PAMS — Procurement &amp; Approval Management System
            </Typography>
            <Stack direction="row" spacing={2}>
              <Typography component={RouterLink} to="/pricing" variant="body2">
                Pricing
              </Typography>
              <Typography component={RouterLink} to="/demo" variant="body2">
                Request demo
              </Typography>
            </Stack>
          </Stack>
        </Box>
      </Box>
    </Box>
  );
}
