import { Link as RouterLink } from 'react-router-dom';
import { Box, Button, Card, CardContent, Grid, Stack, Typography } from '@mui/material';
import ArrowForwardIcon from '@mui/icons-material/ArrowForward';
import CheckCircleRoundedIcon from '@mui/icons-material/CheckCircleRounded';
import { PublicLayout } from './PublicLayout';

const productShots = [
  {
    src: '/screenshots/request-detail.webp',
    title: 'Approval workflow',
    description: 'Multi-level approvals, change requests and a complete audit trail.',
    alt: 'PAMS purchase request detail showing line items, a multi-level approval timeline and the audit trail',
    rotate: '-2.5deg',
    offset: 0,
  },
  {
    src: '/screenshots/dashboard-card.webp',
    title: 'Live spend dashboards',
    description: 'See requests, committed spend and department allocation at a glance.',
    alt: 'PAMS dashboard showing recent requests, spend by month and spend by department',
    rotate: '1.5deg',
    offset: -10,
  },
  {
    src: '/screenshots/ap-aging-card.webp',
    title: 'AP aging and payments',
    description: 'Know what is due now and what is overdue, by vendor and currency.',
    alt: 'PAMS accounts payable aging report grouped by vendor and currency',
    rotate: '3deg',
    offset: 8,
  },
];

const benefits = [
  {
    title: 'Control spend before it happens',
    description:
      'Requests, budgets and approvals work together so commitments are visible before money leaves.',
  },
  {
    title: 'Run procurement from one workflow',
    description: 'From purchase request to vendor, purchase order, receipt and invoice matching.',
  },
  {
    title: 'Be audit-ready by default',
    description:
      'Every approval, exception and vendor action is recorded with a complete audit trail.',
  },
];

const steps = [
  { number: '01', title: 'Capture the request', text: 'Standardize how teams ask to buy.' },
  {
    number: '02',
    title: 'Approve with confidence',
    text: 'Budgets, limits and policy-driven approvals.',
  },
  {
    number: '03',
    title: 'Order and receive',
    text: 'Issue POs, record receipts and track delivery.',
  },
  {
    number: '04',
    title: 'Match and pay',
    text: 'Three-way match exceptions, resolution and payment scheduling.',
  },
];

export function LandingPage() {
  return (
    <PublicLayout>
      <Box sx={{ width: '100%', px: { xs: 2, sm: 4, md: 6, lg: 8 }, overflowX: 'clip' }}>
        <Grid container spacing={6} alignItems="center" sx={{ py: { xs: 8, md: 12 } }}>
          <Grid size={{ xs: 12, md: 7 }}>
            <Typography
              variant="overline"
              color="primary"
              sx={{ fontWeight: 700, letterSpacing: 2 }}
            >
              Procurement without the spreadsheet chaos
            </Typography>
            <Typography variant="h2" sx={{ mt: 1, mb: 3, fontSize: { xs: 40, md: 58 } }}>
              Spend with clarity.
              <br />
              Approve with confidence.
            </Typography>
            <Typography
              variant="h6"
              color="text.secondary"
              fontWeight={400}
              sx={{ mb: 4, maxWidth: 680 }}
            >
              PAMS brings purchase requests, approvals, vendors, budgets, purchase orders, receipts,
              invoices and vendor payments into one auditable system.
            </Typography>
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
              <Button
                component={RouterLink}
                to="/demo"
                variant="contained"
                size="large"
                endIcon={<ArrowForwardIcon />}
              >
                Book a demo
              </Button>
              <Button component={RouterLink} to="/pricing" variant="outlined" size="large">
                View pricing
              </Button>
            </Stack>
            <Stack direction="row" spacing={2} sx={{ mt: 4 }} flexWrap="wrap" useFlexGap>
              {['Budget control', 'Audit trail', 'Vendor management', 'Invoice matching'].map(
                (item) => (
                  <Stack key={item} direction="row" spacing={0.75} alignItems="center">
                    <CheckCircleRoundedIcon color="success" fontSize="small" />
                    <Typography variant="body2">{item}</Typography>
                  </Stack>
                ),
              )}
            </Stack>
          </Grid>
          <Grid size={{ xs: 12, md: 5 }}>
            <Card sx={{ borderRadius: 4, boxShadow: '0 24px 80px rgba(11,92,173,0.18)' }}>
              <CardContent sx={{ p: { xs: 3, md: 4 } }}>
                <Typography variant="overline" color="secondary" fontWeight={700}>
                  Request-to-payment visibility
                </Typography>
                <Stack spacing={2.25} sx={{ mt: 2 }}>
                  {steps.map((step) => (
                    <Stack key={step.number} direction="row" spacing={2}>
                      <Typography
                        sx={{
                          fontWeight: 800,
                          color: 'primary.main',
                          fontSize: 13,
                          minWidth: 28,
                        }}
                      >
                        {step.number}
                      </Typography>
                      <Box>
                        <Typography variant="subtitle2">{step.title}</Typography>
                        <Typography variant="body2" color="text.secondary">
                          {step.text}
                        </Typography>
                      </Box>
                    </Stack>
                  ))}
                </Stack>
              </CardContent>
            </Card>
          </Grid>
        </Grid>

        <Box sx={{ position: 'relative', py: { xs: 6, md: 11 }, overflow: 'hidden' }}>
          <Box
            aria-hidden
            sx={{
              position: 'absolute',
              top: 140,
              right: -120,
              width: 420,
              height: 420,
              borderRadius: '50%',
              bgcolor: 'rgba(11,92,173,0.10)',
              filter: 'blur(90px)',
              pointerEvents: 'none',
            }}
          />
          <Box
            aria-hidden
            sx={{
              position: 'absolute',
              bottom: 80,
              left: -150,
              width: 360,
              height: 360,
              borderRadius: '50%',
              bgcolor: 'rgba(0,105,92,0.10)',
              filter: 'blur(90px)',
              pointerEvents: 'none',
            }}
          />

          <Stack spacing={1.5} sx={{ position: 'relative', mb: { xs: 4, md: 6 }, maxWidth: 920 }}>
            <Typography variant="overline" color="primary" fontWeight={700} letterSpacing={2}>
              Inside PAMS
            </Typography>
            <Typography
              variant="h3"
              sx={{ fontSize: { xs: 32, sm: 40, md: 48 }, lineHeight: 1.15 }}
            >
              See the workflow your team will actually use
            </Typography>
            <Typography variant="body1" color="text.secondary">
              Real screenshots from a PAMS workspace. Every approval, purchase order and invoice
              stays connected from request to payment.
            </Typography>
          </Stack>

          <Box
            sx={{
              position: 'relative',
              display: 'flex',
              flexWrap: 'wrap',
              alignItems: 'flex-start',
              gap: { xs: 4, md: 3 },
              mt: { xs: 2, md: 4 },
              px: { xs: 0, md: 2 },
            }}
          >
            {productShots.map((shot, index) => (
              <Box
                key={shot.src}
                sx={{
                  width: { xs: '100%', md: 'calc(33.333% - 16px)' },
                  transform: {
                    xs: `rotate(${shot.rotate})`,
                    md: `rotate(${shot.rotate}) translateY(${shot.offset}px)`,
                  },
                }}
              >
                <Box
                  sx={{
                    willChange: 'transform',
                    '@keyframes cardFloat': {
                      '0%, 100%': { transform: 'translateY(0px)' },
                      '50%': { transform: 'translateY(-14px)' },
                    },
                    animation: `cardFloat ${7 + index * 0.9}s ease-in-out infinite`,
                    animationDelay: `${index * 0.55}s`,
                    '@media (prefers-reduced-motion: reduce)': { animation: 'none' },
                  }}
                >
                  <Box
                    sx={{
                      bgcolor: '#FFFFFF',
                      p: 0.75,
                      borderRadius: 3,
                      boxShadow: '0 30px 80px rgba(7,64,121,0.22)',
                    }}
                  >
                    <Box
                      component="img"
                      src={shot.src}
                      alt={shot.alt}
                      loading={index === 0 ? 'eager' : 'lazy'}
                      sx={{ display: 'block', width: '100%', borderRadius: 2 }}
                    />
                  </Box>
                  <Typography variant="subtitle2" sx={{ mt: 2 }}>
                    {shot.title}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    {shot.description}
                  </Typography>
                </Box>
              </Box>
            ))}
          </Box>
        </Box>

        <Box sx={{ py: { xs: 4, md: 7 } }}>
          <Typography variant="h3" sx={{ mb: 1, fontSize: { xs: 32, sm: 40, md: 48 } }}>
            Built for teams that answer to finance
          </Typography>
          <Typography variant="body1" color="text.secondary" sx={{ mb: 4, maxWidth: 700 }}>
            Replace scattered approvals and manual AP checks with a shared operational record.
          </Typography>
          <Grid container spacing={2}>
            {benefits.map((benefit) => (
              <Grid key={benefit.title} size={{ xs: 12, md: 4 }}>
                <Card sx={{ height: '100%' }}>
                  <CardContent sx={{ p: 3 }}>
                    <CheckCircleRoundedIcon color="success" sx={{ fontSize: 32, mb: 2 }} />
                    <Typography variant="h6" sx={{ mb: 1 }}>
                      {benefit.title}
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      {benefit.description}
                    </Typography>
                  </CardContent>
                </Card>
              </Grid>
            ))}
          </Grid>
        </Box>

        <Card
          sx={{
            my: 6,
            p: { xs: 3, md: 5 },
            background: 'linear-gradient(135deg, #0B5CAD, #074079)',
            color: '#FFFFFF',
          }}
        >
          <Stack direction={{ xs: 'column', md: 'row' }} spacing={3} alignItems={{ md: 'center' }}>
            <Box sx={{ flexGrow: 1 }}>
              <Typography variant="h4" sx={{ color: '#FFFFFF' }}>
                See PAMS with your own procurement workflow
              </Typography>
              <Typography sx={{ mt: 1, color: 'rgba(255,255,255,0.8)' }}>
                Tell us how your team buys today. We will show you a focused walkthrough.
              </Typography>
            </Box>
            <Button
              component={RouterLink}
              to="/demo"
              variant="contained"
              color="secondary"
              size="large"
              endIcon={<ArrowForwardIcon />}
            >
              Request a demo
            </Button>
          </Stack>
        </Card>
      </Box>
    </PublicLayout>
  );
}
