import { Link as RouterLink, useLocation } from 'react-router-dom';
import { Box, Button, Container, Stack, Typography } from '@mui/material';
import ArrowForwardIcon from '@mui/icons-material/ArrowForward';
import { useBranding } from '@/branding/BrandingProvider';

export function PublicLayout({ children }: { children: React.ReactNode }) {
  const { branding } = useBranding();
  const { pathname } = useLocation();
  return (
    <Box sx={{ minHeight: '100vh', bgcolor: 'background.default' }}>
      <Box
        component="header"
        sx={{
          position: 'sticky',
          top: 0,
          zIndex: (theme) => theme.zIndex.appBar,
          bgcolor: 'primary.main',
          color: '#FFFFFF',
          py: 1.25,
          boxShadow: '0 1px 0 rgba(255,255,255,0.12), 0 10px 30px rgba(7,64,121,0.12)',
        }}
      >
        <Container maxWidth="lg">
          <Stack direction="row" alignItems="center" spacing={1.5}>
            <Box
              component={RouterLink}
              to="/welcome"
              sx={{ display: 'flex', alignItems: 'center', textDecoration: 'none' }}
            >
              <Box
                component="img"
                src="/logo/pams-logo-reverse.svg"
                alt="PAMS"
                sx={{ height: 34 }}
              />
              {branding.displayName !== 'PAMS' && (
                <Typography sx={{ color: '#FFFFFF', ml: 1.5, fontWeight: 700 }}>
                  {branding.displayName}
                </Typography>
              )}
            </Box>
            <Box sx={{ flexGrow: 1 }} />
            <Button
              component={RouterLink}
              to="/pricing"
              color="inherit"
              aria-current={pathname === '/pricing' ? 'page' : undefined}
              sx={{
                display: { xs: 'none', sm: 'inline-flex' },
                bgcolor: pathname === '/pricing' ? 'rgba(255,255,255,0.12)' : 'transparent',
              }}
            >
              Pricing
            </Button>
            <Button
              component={RouterLink}
              to="/demo"
              color="inherit"
              aria-current={pathname === '/demo' ? 'page' : undefined}
              sx={{
                display: { xs: 'none', md: 'inline-flex' },
                bgcolor: pathname === '/demo' ? 'rgba(255,255,255,0.12)' : 'transparent',
              }}
            >
              Book a demo
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
        </Container>
      </Box>
      <Box component="main">{children}</Box>
      <Box
        component="footer"
        sx={{
          borderTop: 1,
          borderColor: 'divider',
          py: { xs: 4, md: 5 },
          bgcolor: 'background.paper',
        }}
      >
        <Container maxWidth="lg">
          <Stack
            direction={{ xs: 'column', md: 'row' }}
            justifyContent="space-between"
            alignItems={{ xs: 'flex-start', md: 'center' }}
            spacing={3}
          >
            <Stack spacing={1}>
              <Box
                component={RouterLink}
                to="/welcome"
                sx={{ display: 'inline-flex', alignItems: 'center' }}
              >
                <Box component="img" src="/logo/pams-logo.svg" alt="PAMS" sx={{ height: 30 }} />
              </Box>
              <Typography variant="body2" color="text.secondary">
                Procurement &amp; Approval Management System
              </Typography>
            </Stack>
            <Stack direction="row" spacing={{ xs: 2, sm: 3 }} flexWrap="wrap" useFlexGap>
              <Typography component={RouterLink} to="/pricing" variant="body2">
                Pricing
              </Typography>
              <Typography component={RouterLink} to="/demo" variant="body2">
                Request demo
              </Typography>
              <Typography component={RouterLink} to="/login" variant="body2">
                Sign in
              </Typography>
            </Stack>
          </Stack>
        </Container>
      </Box>
    </Box>
  );
}
