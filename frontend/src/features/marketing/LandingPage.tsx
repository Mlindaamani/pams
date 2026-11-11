import { Link as RouterLink } from 'react-router-dom';
import { Box, Button, Card, CardContent, Container, Grid, Stack, Typography } from '@mui/material';
import ArrowForwardIcon from '@mui/icons-material/ArrowForward';
import CheckCircleRoundedIcon from '@mui/icons-material/CheckCircleRounded';
import { PublicLayout } from './PublicLayout';

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
      <Container maxWidth="lg">
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

        <Box sx={{ py: { xs: 4, md: 7 } }}>
          <Typography variant="h3" sx={{ mb: 1 }}>
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
      </Container>
    </PublicLayout>
  );
}
