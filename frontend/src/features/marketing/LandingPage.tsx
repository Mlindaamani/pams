import { Link as RouterLink } from 'react-router-dom';
import { alpha } from '@mui/material/styles';
import {
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Container,
  Grid,
  Stack,
  Typography,
} from '@mui/material';
import ArrowForwardIcon from '@mui/icons-material/ArrowForward';
import CheckCircleRoundedIcon from '@mui/icons-material/CheckCircleRounded';
import AccountBalanceWalletOutlinedIcon from '@mui/icons-material/AccountBalanceWalletOutlined';
import FactCheckOutlinedIcon from '@mui/icons-material/FactCheckOutlined';
import VerifiedUserOutlinedIcon from '@mui/icons-material/VerifiedUserOutlined';
import EditNoteOutlinedIcon from '@mui/icons-material/EditNoteOutlined';
import RuleOutlinedIcon from '@mui/icons-material/RuleOutlined';
import LocalShippingOutlinedIcon from '@mui/icons-material/LocalShippingOutlined';
import PaymentsOutlinedIcon from '@mui/icons-material/PaymentsOutlined';
import { MarketingCtaPanel } from './MarketingCtaPanel';
import { MarketingSectionHeading } from './MarketingSectionHeading';
import { PublicLayout } from './PublicLayout';

const benefits = [
  {
    icon: AccountBalanceWalletOutlinedIcon,
    title: 'Budget-aware requests',
    description:
      'Check available budget before submission and keep every commitment visible to finance.',
  },
  {
    icon: FactCheckOutlinedIcon,
    title: 'Policy-driven approvals',
    description:
      'Route requests by amount, role, manager or policy, with clear change-request loops.',
  },
  {
    icon: VerifiedUserOutlinedIcon,
    title: 'Audit-ready by default',
    description:
      'Every decision, exception and vendor action is recorded in a complete audit trail.',
  },
];

const steps = [
  {
    number: '01',
    icon: EditNoteOutlinedIcon,
    title: 'Capture the request',
    text: 'Standardize how teams ask to buy, with budgets, quotes and attachments.',
  },
  {
    number: '02',
    icon: RuleOutlinedIcon,
    title: 'Approve with confidence',
    text: 'Apply limits, roles and multi-level policies without chasing email threads.',
  },
  {
    number: '03',
    icon: LocalShippingOutlinedIcon,
    title: 'Order and receive',
    text: 'Issue purchase orders, record receipts and keep delivery status current.',
  },
  {
    number: '04',
    icon: PaymentsOutlinedIcon,
    title: 'Match and pay',
    text: 'Resolve three-way match exceptions and schedule vendor payments.',
  },
];

const productShots = [
  {
    src: '/screenshots/dashboard-card.webp',
    title: 'Live spend visibility',
    description:
      'Requests, committed spend and department allocation in one operational dashboard.',
    alt: 'PAMS dashboard showing recent requests, spend by month and spend by department',
  },
  {
    src: '/screenshots/ap-aging-card.webp',
    title: 'Accounts payable control',
    description:
      'See what is due, what is overdue and where cash is committed, by vendor and currency.',
    alt: 'PAMS accounts payable aging report grouped by vendor and currency',
  },
];

const assurances = [
  'Policy-driven approvals',
  'Budget control',
  'Audit-ready records',
  'Invoice matching',
];

export function LandingPage() {
  return (
    <PublicLayout>
      <Box sx={{ position: 'relative', overflow: 'hidden' }}>
        <Box
          aria-hidden
          sx={{
            position: 'absolute',
            top: -260,
            left: '50%',
            width: 1000,
            height: 720,
            transform: 'translateX(-50%)',
            background:
              'radial-gradient(circle, rgba(11,92,173,0.16) 0%, rgba(11,92,173,0.05) 40%, transparent 70%)',
            pointerEvents: 'none',
          }}
        />
        <Container maxWidth="lg" sx={{ position: 'relative' }}>
          <Stack
            alignItems="center"
            textAlign="center"
            sx={{ pt: { xs: 8, md: 12 }, pb: { xs: 7, md: 9 } }}
          >
            <Chip
              label="Procurement, approvals and payments in one place"
              sx={{
                bgcolor: alpha('#0B5CAD', 0.07),
                borderColor: alpha('#0B5CAD', 0.18),
                color: 'primary.main',
                fontWeight: 600,
              }}
              variant="outlined"
            />
            <Typography
              component="h1"
              variant="h2"
              sx={{
                mt: 3,
                maxWidth: 940,
                fontSize: { xs: 42, sm: 54, md: 68 },
                fontWeight: 800,
                letterSpacing: '-0.045em',
                lineHeight: 1.03,
              }}
            >
              Spend with clarity.
              <br />
              Approve with confidence.
            </Typography>
            <Typography
              color="text.secondary"
              sx={{ mt: 3, maxWidth: 720, fontSize: { xs: 17, md: 20 }, lineHeight: 1.65 }}
            >
              PAMS connects purchase requests, budgets, approvals, purchase orders, receipts,
              invoices and payments in one auditable workflow.
            </Typography>
            <Stack
              direction={{ xs: 'column', sm: 'row' }}
              spacing={1.5}
              sx={{ mt: 4, width: { xs: '100%', sm: 'auto' } }}
            >
              <Button
                component={RouterLink}
                to="/demo"
                variant="contained"
                size="large"
                endIcon={<ArrowForwardIcon />}
                sx={{ px: 3 }}
              >
                Book a demo
              </Button>
              <Button
                component={RouterLink}
                to="/pricing"
                variant="outlined"
                size="large"
                sx={{ px: 3 }}
              >
                Explore pricing
              </Button>
            </Stack>
            <Stack
              direction="row"
              spacing={{ xs: 1.5, sm: 2.5 }}
              flexWrap="wrap"
              useFlexGap
              justifyContent="center"
              sx={{ mt: 4 }}
            >
              {assurances.map((item) => (
                <Stack key={item} direction="row" spacing={0.75} alignItems="center">
                  <CheckCircleRoundedIcon color="success" sx={{ fontSize: 17 }} />
                  <Typography variant="body2" color="text.secondary">
                    {item}
                  </Typography>
                </Stack>
              ))}
            </Stack>
          </Stack>

          <Box sx={{ position: 'relative', maxWidth: 1120, mx: 'auto' }}>
            <Box
              aria-hidden
              sx={{
                position: 'absolute',
                inset: '12% 8% -8%',
                borderRadius: '50%',
                bgcolor: alpha('#0B5CAD', 0.18),
                filter: 'blur(80px)',
                pointerEvents: 'none',
              }}
            />
            <Box
              sx={{
                position: 'relative',
                borderRadius: 3,
                border: 1,
                borderColor: alpha('#0B5CAD', 0.16),
                bgcolor: '#FFFFFF',
                p: { xs: 0.75, sm: 1 },
                boxShadow: '0 32px 90px rgba(7,64,121,0.20)',
                overflow: 'hidden',
              }}
            >
              <Stack
                direction="row"
                alignItems="center"
                spacing={1.5}
                sx={{ bgcolor: '#111827', px: { xs: 1.5, sm: 2 }, py: 1.25 }}
              >
                <Stack direction="row" spacing={0.75} aria-hidden>
                  {['#F87171', '#FBBF24', '#34D399'].map((color) => (
                    <Box
                      key={color}
                      sx={{ width: 9, height: 9, borderRadius: '50%', bgcolor: color }}
                    />
                  ))}
                </Stack>
                <Box sx={{ flexGrow: 1, display: 'flex', justifyContent: 'center' }}>
                  <Box
                    sx={{
                      display: { xs: 'none', sm: 'block' },
                      px: 2,
                      py: 0.4,
                      borderRadius: 99,
                      bgcolor: 'rgba(255,255,255,0.08)',
                      color: 'rgba(255,255,255,0.65)',
                      fontFamily: 'monospace',
                      fontSize: 11,
                    }}
                  >
                    app.pams.local/requests
                  </Box>
                </Box>
                <Chip
                  size="small"
                  label="Live workspace"
                  sx={{
                    bgcolor: 'rgba(52,211,153,0.12)',
                    color: '#6EE7B7',
                    borderColor: 'rgba(52,211,153,0.24)',
                    fontSize: 10,
                  }}
                  variant="outlined"
                />
              </Stack>
              <Box
                component="img"
                src="/screenshots/request-detail.webp"
                alt="PAMS purchase request detail showing line items, a multi-level approval timeline and the audit trail"
                loading="eager"
                sx={{
                  display: 'block',
                  width: '100%',
                  aspectRatio: { xs: '3 / 2', md: '16 / 9' },
                  objectFit: 'cover',
                  objectPosition: 'top',
                }}
              />
            </Box>
          </Box>
        </Container>
      </Box>

      <Box sx={{ py: { xs: 9, md: 12 } }}>
        <Container maxWidth="lg">
          <MarketingSectionHeading
            eyebrow="Why PAMS"
            title="One source of truth for every purchase"
            description="Replace email threads, spreadsheets and disconnected finance checks with a shared operational record."
          />
          <Grid container spacing={2.5} sx={{ mt: 5 }}>
            {benefits.map((benefit) => {
              const Icon = benefit.icon;
              return (
                <Grid key={benefit.title} size={{ xs: 12, md: 4 }}>
                  <Card sx={{ height: '100%', borderRadius: 3, boxShadow: 'none' }}>
                    <CardContent sx={{ p: { xs: 3, md: 3.5 } }}>
                      <Box
                        sx={{
                          width: 48,
                          height: 48,
                          borderRadius: 2,
                          bgcolor: alpha('#0B5CAD', 0.08),
                          color: 'primary.main',
                          display: 'grid',
                          placeItems: 'center',
                          mb: 2.5,
                        }}
                      >
                        <Icon />
                      </Box>
                      <Typography variant="h6" sx={{ mb: 1 }}>
                        {benefit.title}
                      </Typography>
                      <Typography variant="body2" color="text.secondary" sx={{ lineHeight: 1.7 }}>
                        {benefit.description}
                      </Typography>
                    </CardContent>
                  </Card>
                </Grid>
              );
            })}
          </Grid>
        </Container>
      </Box>

      <Box
        sx={{
          py: { xs: 9, md: 12 },
          bgcolor: alpha('#0B5CAD', 0.035),
          borderTop: 1,
          borderBottom: 1,
          borderColor: 'divider',
        }}
      >
        <Container maxWidth="lg">
          <MarketingSectionHeading
            eyebrow="Request to payment"
            title="A workflow your team will actually follow"
            description="Every stage is connected, from the first request to the final vendor payment."
          />
          <Grid container spacing={2.5} sx={{ mt: 5 }}>
            {steps.map((step) => {
              const Icon = step.icon;
              return (
                <Grid key={step.number} size={{ xs: 12, sm: 6, md: 3 }}>
                  <Box
                    sx={{
                      height: '100%',
                      p: 3,
                      bgcolor: 'background.paper',
                      border: 1,
                      borderColor: 'divider',
                      borderRadius: 3,
                    }}
                  >
                    <Stack direction="row" alignItems="center" justifyContent="space-between">
                      <Box
                        sx={{
                          width: 38,
                          height: 38,
                          borderRadius: '50%',
                          border: 1,
                          borderColor: alpha('#0B5CAD', 0.2),
                          bgcolor: alpha('#0B5CAD', 0.06),
                          color: 'primary.main',
                          display: 'grid',
                          placeItems: 'center',
                          fontSize: 13,
                          fontWeight: 800,
                        }}
                      >
                        {step.number}
                      </Box>
                      <Icon sx={{ color: 'text.secondary', fontSize: 22 }} aria-hidden />
                    </Stack>
                    <Typography variant="subtitle1" fontWeight={700} sx={{ mt: 2.5 }}>
                      {step.title}
                    </Typography>
                    <Typography
                      variant="body2"
                      color="text.secondary"
                      sx={{ mt: 1, lineHeight: 1.7 }}
                    >
                      {step.text}
                    </Typography>
                  </Box>
                </Grid>
              );
            })}
          </Grid>
        </Container>
      </Box>

      <Box sx={{ py: { xs: 9, md: 12 } }}>
        <Container maxWidth="lg">
          <MarketingSectionHeading
            eyebrow="Inside PAMS"
            title="See the whole picture, not just the queue"
            description="A clear view of spend, commitments and payment exposure for procurement, finance and operations."
          />
          <Grid container spacing={3} sx={{ mt: 5 }}>
            {productShots.map((shot) => (
              <Grid key={shot.src} size={{ xs: 12, md: 6 }}>
                <Card
                  sx={{
                    height: '100%',
                    borderRadius: 3,
                    overflow: 'hidden',
                    boxShadow: '0 24px 60px rgba(7,64,121,0.10)',
                    transition: 'transform 180ms ease, box-shadow 180ms ease',
                    '&:hover': {
                      transform: 'translateY(-3px)',
                      boxShadow: '0 30px 70px rgba(7,64,121,0.16)',
                    },
                    '@media (prefers-reduced-motion: reduce)': {
                      transition: 'none',
                      '&:hover': { transform: 'none' },
                    },
                  }}
                >
                  <Box
                    component="img"
                    src={shot.src}
                    alt={shot.alt}
                    loading="lazy"
                    sx={{
                      display: 'block',
                      width: '100%',
                      borderBottom: 1,
                      borderColor: 'divider',
                    }}
                  />
                  <CardContent sx={{ p: { xs: 2.5, md: 3 } }}>
                    <Typography variant="h6">{shot.title}</Typography>
                    <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
                      {shot.description}
                    </Typography>
                  </CardContent>
                </Card>
              </Grid>
            ))}
          </Grid>
        </Container>
      </Box>

      <Box sx={{ pb: { xs: 7, md: 10 } }}>
        <Container maxWidth="lg">
          <MarketingCtaPanel
            title="Ready to modernize procurement?"
            description="See how PAMS fits your approval structure, spend controls and payment process."
            actionLabel="Request a demo"
            actionTo="/demo"
          />
        </Container>
      </Box>
    </PublicLayout>
  );
}
