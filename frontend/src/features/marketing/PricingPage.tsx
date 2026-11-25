import { useState } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { alpha } from '@mui/material/styles';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Container,
  Divider,
  Grid,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  ToggleButton,
  ToggleButtonGroup,
  Typography,
} from '@mui/material';
import CheckCircleRoundedIcon from '@mui/icons-material/CheckCircleRounded';
import { Loading } from '@/components/Page';
import { listPublicPlans } from './api';
import { MarketingCtaPanel } from './MarketingCtaPanel';
import { MarketingSectionHeading } from './MarketingSectionHeading';
import { PublicLayout } from './PublicLayout';
import type { BillingCycle, PublicPlan } from './types';

export function PricingPage() {
  const plans = useQuery({ queryKey: ['public-plans'], queryFn: listPublicPlans });
  const [cycle, setCycle] = useState<BillingCycle>('MONTHLY');
  const bestAnnualSaving = Math.max(0, ...(plans.data ?? []).map(annualSaving));
  const pricingCurrency = plans.data?.[0]?.currency || 'USD';

  return (
    <PublicLayout>
      <Box sx={{ position: 'relative', overflow: 'hidden' }}>
        <Box
          aria-hidden
          sx={{
            position: 'absolute',
            top: -240,
            left: '50%',
            width: 1000,
            height: 700,
            transform: 'translateX(-50%)',
            background:
              'radial-gradient(circle, rgba(11,92,173,0.16) 0%, rgba(11,92,173,0.05) 40%, transparent 70%)',
            pointerEvents: 'none',
          }}
        />
        <Container maxWidth="lg" sx={{ position: 'relative', pt: { xs: 8, md: 11 }, pb: 2 }}>
          <Stack alignItems="center" textAlign="center" spacing={2}>
            <Typography
              variant="overline"
              color="primary"
              sx={{ fontWeight: 800, letterSpacing: 2 }}
            >
              Pricing
            </Typography>
            <Typography
              component="h1"
              variant="h2"
              sx={{
                maxWidth: 900,
                fontSize: { xs: 42, sm: 52, md: 64 },
                fontWeight: 800,
                letterSpacing: '-0.045em',
                lineHeight: 1.05,
              }}
            >
              Simple plans. Serious control.
            </Typography>
            <Typography
              color="text.secondary"
              sx={{ maxWidth: 700, fontSize: { xs: 16, md: 18 }, lineHeight: 1.7 }}
            >
              Start with a trial, then choose the plan that matches your team, procurement volume
              and compliance needs.
            </Typography>
            <ToggleButtonGroup
              value={cycle}
              exclusive
              aria-label="Billing cycle"
              onChange={(_, value: BillingCycle | null) => value && setCycle(value)}
              sx={{
                mt: 1,
                bgcolor: 'background.paper',
                border: 1,
                borderColor: 'divider',
                borderRadius: 2,
                p: 0.5,
                '& .MuiToggleButton-root': {
                  border: 0,
                  borderRadius: '8px !important',
                  px: { xs: 2.5, sm: 3.5 },
                  py: 1,
                  fontWeight: 600,
                  textTransform: 'none',
                },
              }}
            >
              <ToggleButton value="MONTHLY">Monthly</ToggleButton>
              <ToggleButton value="ANNUAL">Annual</ToggleButton>
            </ToggleButtonGroup>
            {bestAnnualSaving > 0 && (
              <Typography variant="body2" color="text.secondary">
                Annual billing saves up to {bestAnnualSaving}% compared with paying monthly.
              </Typography>
            )}
          </Stack>

          {plans.isLoading ? (
            <Box sx={{ py: 8, display: 'flex', justifyContent: 'center' }}>
              <Loading />
            </Box>
          ) : plans.isError ? (
            <Box sx={{ maxWidth: 720, mx: 'auto', mt: 6 }}>
              <Alert severity="error">
                Plans could not be loaded. Please refresh and try again.
              </Alert>
            </Box>
          ) : !plans.data?.length ? (
            <Box sx={{ maxWidth: 720, mx: 'auto', mt: 6 }}>
              <Alert severity="info">
                No plans are available yet. Contact us for a tailored quote.
              </Alert>
            </Box>
          ) : (
            <>
              <Grid container spacing={3} alignItems="stretch" sx={{ mt: 5 }}>
                {plans.data.map((plan) => {
                  const monthly = Number(plan.monthlyPrice);
                  const annual = Number(plan.annualPrice);
                  const saving = annualSaving(plan);
                  const price = cycle === 'ANNUAL' ? annual : monthly;
                  const popular = plan.code === 'GROWTH';
                  return (
                    <Grid key={plan.code} size={{ xs: 12, md: 4 }}>
                      <Card
                        sx={{
                          height: '100%',
                          display: 'flex',
                          flexDirection: 'column',
                          borderRadius: 3,
                          borderColor: popular ? 'primary.main' : 'divider',
                          borderWidth: popular ? 2 : 1,
                          boxShadow: popular ? '0 24px 60px rgba(7,64,121,0.14)' : 'none',
                        }}
                      >
                        <CardContent
                          sx={{ p: { xs: 3, md: 3.5 }, display: 'flex', flexDirection: 'column' }}
                        >
                          <Stack direction="row" justifyContent="space-between" alignItems="center">
                            <Typography variant="h5" fontWeight={700}>
                              {plan.name}
                            </Typography>
                            {popular && <Chip size="small" label="Most popular" color="primary" />}
                          </Stack>
                          <Stack direction="row" alignItems="baseline" spacing={0.5} sx={{ mt: 2 }}>
                            <Typography
                              sx={{
                                fontSize: { xs: 40, md: 46 },
                                fontWeight: 800,
                                letterSpacing: '-0.04em',
                                lineHeight: 1,
                              }}
                            >
                              {formatPrice(price, plan.currency)}
                            </Typography>
                            <Typography variant="body2" color="text.secondary">
                              /{cycle === 'ANNUAL' ? 'year' : 'month'}
                            </Typography>
                          </Stack>
                          <Stack
                            direction="row"
                            spacing={1}
                            flexWrap="wrap"
                            useFlexGap
                            sx={{ mt: 1 }}
                          >
                            <Chip
                              size="small"
                              variant="outlined"
                              label={`${plan.trialDays}-day trial`}
                            />
                            {cycle === 'ANNUAL' && saving > 0 && (
                              <Chip size="small" color="success" label={`Save ${saving}%`} />
                            )}
                          </Stack>
                          <Typography
                            variant="body2"
                            color="text.secondary"
                            sx={{ mt: 2, minHeight: 44, lineHeight: 1.65 }}
                          >
                            {plan.description}
                          </Typography>
                          <Button
                            component={RouterLink}
                            to={`/demo?plan=${plan.code}`}
                            variant={popular ? 'contained' : 'outlined'}
                            fullWidth
                            sx={{ mt: 2.5 }}
                          >
                            Talk to us
                          </Button>
                          <Divider sx={{ my: 2.5 }} />
                          <Typography variant="subtitle2" sx={{ mb: 1.25 }}>
                            Included
                          </Typography>
                          <Stack spacing={0.9} sx={{ flexGrow: 1 }}>
                            {(plan.features ?? []).map((feature) => (
                              <Stack
                                key={feature}
                                direction="row"
                                spacing={1}
                                alignItems="flex-start"
                              >
                                <CheckCircleRoundedIcon
                                  sx={{ fontSize: 17, color: 'success.main', mt: '2px' }}
                                />
                                <Typography variant="body2">{feature}</Typography>
                              </Stack>
                            ))}
                          </Stack>
                        </CardContent>
                      </Card>
                    </Grid>
                  );
                })}
              </Grid>

              <Card sx={{ mt: 5, borderRadius: 3, boxShadow: 'none' }}>
                <CardContent sx={{ p: { xs: 2.5, md: 4 } }}>
                  <MarketingSectionHeading
                    eyebrow="Compare"
                    title="Every limit, side by side"
                    description="Trial length and workspace limits across the available plans."
                  />
                  <Stack spacing={1.5} sx={{ mt: 3, display: { xs: 'flex', md: 'none' } }}>
                    {plans.data.map((plan) => (
                      <Box
                        key={plan.code}
                        sx={{ border: 1, borderColor: 'divider', borderRadius: 2, p: 2 }}
                      >
                        <Stack
                          direction="row"
                          justifyContent="space-between"
                          alignItems="center"
                          spacing={1}
                        >
                          <Stack direction="row" spacing={1} alignItems="center">
                            <Typography variant="body2" fontWeight={700}>
                              {plan.name}
                            </Typography>
                            {plan.code === 'GROWTH' && (
                              <Chip size="small" label="Popular" color="primary" />
                            )}
                          </Stack>
                          <Typography variant="body2" color="text.secondary">
                            {formatPrice(
                              cycle === 'ANNUAL'
                                ? Number(plan.annualPrice)
                                : Number(plan.monthlyPrice),
                              plan.currency,
                            )}
                            /{cycle === 'ANNUAL' ? 'year' : 'month'}
                          </Typography>
                        </Stack>
                        <Stack spacing={0.75} sx={{ mt: 1.5 }}>
                          {comparisonRows.map((row) => (
                            <Stack
                              key={row.label}
                              direction="row"
                              justifyContent="space-between"
                              spacing={2}
                            >
                              <Typography variant="body2" color="text.secondary">
                                {row.label}
                              </Typography>
                              <Typography variant="body2" fontWeight={500}>
                                {row.value(plan)}
                              </Typography>
                            </Stack>
                          ))}
                        </Stack>
                      </Box>
                    ))}
                  </Stack>
                  <TableContainer
                    sx={{ mt: 4, overflowX: 'auto', display: { xs: 'none', md: 'block' } }}
                  >
                    <Table size="small" sx={{ minWidth: 760 }} aria-label="Compare plans">
                      <TableHead>
                        <TableRow>
                          <TableCell
                            sx={{
                              fontWeight: 700,
                              position: 'sticky',
                              left: 0,
                              zIndex: 2,
                              bgcolor: 'background.paper',
                            }}
                          >
                            Plan details
                          </TableCell>
                          {plans.data.map((plan) => (
                            <TableCell key={plan.code} sx={{ minWidth: 170 }}>
                              <Stack direction="row" spacing={1} alignItems="center">
                                <Typography variant="body2" fontWeight={700}>
                                  {plan.name}
                                </Typography>
                                {plan.code === 'GROWTH' && (
                                  <Chip size="small" label="Popular" color="primary" />
                                )}
                              </Stack>
                              <Typography variant="caption" color="text.secondary">
                                {formatPrice(
                                  cycle === 'ANNUAL'
                                    ? Number(plan.annualPrice)
                                    : Number(plan.monthlyPrice),
                                  plan.currency,
                                )}
                                /{cycle === 'ANNUAL' ? 'year' : 'month'}
                              </Typography>
                            </TableCell>
                          ))}
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {comparisonRows.map((row) => (
                          <TableRow key={row.label}>
                            <TableCell
                              component="th"
                              scope="row"
                              sx={{
                                fontWeight: 600,
                                whiteSpace: 'nowrap',
                                position: 'sticky',
                                left: 0,
                                zIndex: 1,
                                bgcolor: 'background.paper',
                              }}
                            >
                              {row.label}
                            </TableCell>
                            {plans.data.map((plan) => (
                              <TableCell key={plan.code}>{row.value(plan)}</TableCell>
                            ))}
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  </TableContainer>
                  <Typography
                    variant="caption"
                    color="text.secondary"
                    textAlign="center"
                    component="p"
                    sx={{ mt: 2 }}
                  >
                    Prices are shown in {pricingCurrency}. Annual billing is charged once per year.
                  </Typography>
                </CardContent>
              </Card>
            </>
          )}
        </Container>
      </Box>

      <Box sx={{ py: { xs: 8, md: 10 }, bgcolor: alpha('#0B5CAD', 0.035) }}>
        <Container maxWidth="lg">
          <MarketingCtaPanel
            title="Need a plan for a larger or regulated team?"
            description="Tell us about your approval structure, volume and compliance requirements."
            actionLabel="Request a demo"
            actionTo="/demo"
          />
        </Container>
      </Box>
    </PublicLayout>
  );
}

const comparisonRows: {
  label: string;
  value: (plan: PublicPlan) => string;
}[] = [
  { label: 'Trial', value: (plan) => `${plan.trialDays} days` },
  {
    label: 'Users',
    value: (plan) => (plan.maxUsers == null ? 'Unlimited' : String(plan.maxUsers)),
  },
  {
    label: 'Departments',
    value: (plan) => (plan.maxDepartments == null ? 'Unlimited' : String(plan.maxDepartments)),
  },
  {
    label: 'Requests/month',
    value: (plan) => (plan.maxRequestsMonth == null ? 'Unlimited' : String(plan.maxRequestsMonth)),
  },
  {
    label: 'Storage',
    value: (plan) => (plan.maxStorageMb == null ? 'Unlimited' : `${plan.maxStorageMb} MB`),
  },
];

function formatPrice(value: number, currency: string) {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: currency || 'USD',
    maximumFractionDigits: 0,
  }).format(value);
}

function annualSaving(plan: PublicPlan) {
  const monthly = Number(plan.monthlyPrice);
  const annual = Number(plan.annualPrice);
  return monthly > 0 && annual < monthly * 12 ? Math.round((1 - annual / (monthly * 12)) * 100) : 0;
}
