import { useState } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Grid,
  Stack,
  ToggleButton,
  ToggleButtonGroup,
  Typography,
} from '@mui/material';
import CheckCircleRoundedIcon from '@mui/icons-material/CheckCircleRounded';
import ArrowForwardIcon from '@mui/icons-material/ArrowForward';
import { Loading } from '@/components/Page';
import { dateOnly } from '@/lib/format';
import { listPublicPlans } from './api';
import { PublicLayout } from './PublicLayout';

type BillingCycle = 'MONTHLY' | 'ANNUAL';

export function PricingPage() {
  const plans = useQuery({ queryKey: ['public-plans'], queryFn: listPublicPlans });
  const [cycle, setCycle] = useState<BillingCycle>('MONTHLY');

  return (
    <PublicLayout>
      <Box sx={{ width: '100%', px: { xs: 2, sm: 4, md: 6, lg: 8 }, py: { xs: 6, md: 10 } }}>
        <Stack
          direction={{ xs: 'column', md: 'row' }}
          justifyContent="space-between"
          alignItems={{ xs: 'flex-start', md: 'flex-end' }}
          spacing={3}
          sx={{ mb: { xs: 5, md: 7 } }}
        >
          <Stack spacing={1.5} sx={{ maxWidth: 860 }}>
            <Typography variant="overline" color="primary" fontWeight={700} letterSpacing={2}>
              Pricing
            </Typography>
            <Typography variant="h2" sx={{ fontSize: { xs: 36, md: 56 }, lineHeight: 1.1 }}>
              Simple plans. Serious control.
            </Typography>
            <Typography color="text.secondary">
              Start with a trial, then choose the plan that matches your team, procurement volume
              and compliance needs. Every plan includes the full request-to-payment workflow.
            </Typography>
          </Stack>
          <ToggleButtonGroup
            value={cycle}
            exclusive
            size="small"
            aria-label="Billing cycle"
            onChange={(_, value: BillingCycle | null) => value && setCycle(value)}
          >
            <ToggleButton value="MONTHLY">Monthly</ToggleButton>
            <ToggleButton value="ANNUAL">Annual</ToggleButton>
          </ToggleButtonGroup>
        </Stack>

        {plans.isLoading ? (
          <Loading />
        ) : plans.isError ? (
          <Alert severity="error">Plans could not be loaded. Please refresh and try again.</Alert>
        ) : !plans.data?.length ? (
          <Alert severity="info">
            No plans are available yet. Contact us for a tailored quote.
          </Alert>
        ) : (
          <Grid container spacing={3} alignItems="stretch">
            {plans.data.map((plan) => {
              const monthly = Number(plan.monthlyPrice);
              const annual = Number(plan.annualPrice);
              const annualSaving =
                monthly > 0 && annual < monthly * 12
                  ? Math.round((1 - annual / (monthly * 12)) * 100)
                  : 0;
              const price = cycle === 'ANNUAL' ? annual : monthly;
              return (
                <Grid key={plan.code} size={{ xs: 12, md: 4 }}>
                  <Card
                    sx={{
                      height: '100%',
                      display: 'flex',
                      flexDirection: 'column',
                      borderColor: plan.code === 'GROWTH' ? 'primary.main' : 'divider',
                      borderWidth: plan.code === 'GROWTH' ? 2 : 1,
                    }}
                  >
                    <CardContent
                      sx={{ p: 3, display: 'flex', flexDirection: 'column', flexGrow: 1 }}
                    >
                      <Stack direction="row" justifyContent="space-between" alignItems="center">
                        <Typography variant="h5">{plan.name}</Typography>
                        {plan.code === 'GROWTH' && (
                          <Chip size="small" label="Popular" color="primary" />
                        )}
                      </Stack>
                      <Stack direction="row" alignItems="baseline" spacing={0.5} sx={{ mt: 2 }}>
                        <Typography variant="h3">${price.toLocaleString()}</Typography>
                        <Typography variant="body2" color="text.secondary">
                          /{cycle === 'ANNUAL' ? 'year' : 'month'}
                        </Typography>
                      </Stack>
                      <Typography variant="caption" color="text.secondary">
                        {cycle === 'ANNUAL' && annualSaving > 0
                          ? `Save ${annualSaving}% vs monthly · `
                          : ''}
                        {plan.trialDays}-day trial
                      </Typography>
                      <Typography
                        variant="body2"
                        color="text.secondary"
                        sx={{ mt: 2, minHeight: 44 }}
                      >
                        {plan.description}
                      </Typography>
                      <Stack spacing={0.9} sx={{ mt: 2 }}>
                        <Limit label="Users" value={plan.maxUsers} />
                        <Limit label="Departments" value={plan.maxDepartments} />
                        <Limit label="Requests/month" value={plan.maxRequestsMonth} />
                        <Limit label="Storage" value={plan.maxStorageMb} suffix=" MB" />
                      </Stack>
                      <Typography variant="subtitle2" sx={{ mt: 3, mb: 1 }}>
                        Included
                      </Typography>
                      <Stack spacing={0.75} sx={{ flexGrow: 1 }}>
                        {(plan.features ?? []).map((feature) => (
                          <Stack key={feature} direction="row" spacing={1} alignItems="flex-start">
                            <CheckCircleRoundedIcon
                              sx={{ fontSize: 17, color: 'success.main', mt: '2px' }}
                            />
                            <Typography variant="body2">{feature}</Typography>
                          </Stack>
                        ))}
                      </Stack>
                      <Button
                        component={RouterLink}
                        to={`/demo?plan=${plan.code}`}
                        variant={plan.code === 'GROWTH' ? 'contained' : 'outlined'}
                        fullWidth
                        sx={{ mt: 3 }}
                      >
                        Talk to us
                      </Button>
                    </CardContent>
                  </Card>
                </Grid>
              );
            })}
          </Grid>
        )}

        <Card sx={{ mt: 5, p: { xs: 3, md: 5 }, bgcolor: '#074079', color: '#FFFFFF' }}>
          <Stack
            direction={{ xs: 'column', md: 'row' }}
            spacing={3}
            alignItems={{ xs: 'flex-start', md: 'center' }}
            justifyContent="space-between"
          >
            <Box>
              <Typography variant="h4" sx={{ color: '#FFFFFF' }}>
                Need a plan for a larger or regulated team?
              </Typography>
              <Typography sx={{ mt: 1, color: 'rgba(255,255,255,0.78)' }}>
                Tell us about your approval structure, volume and compliance requirements.
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

        <Typography variant="body2" color="text.secondary" sx={{ mt: 3 }}>
          Prices are in USD. Annual pricing is billed once per year. Generated{' '}
          {dateOnly(new Date().toISOString())}.
        </Typography>
      </Box>
    </PublicLayout>
  );
}

function Limit({ label, value, suffix = '' }: { label: string; value?: number; suffix?: string }) {
  return (
    <Stack direction="row" justifyContent="space-between">
      <Typography variant="body2" color="text.secondary">
        {label}
      </Typography>
      <Typography variant="body2">
        {value ?? 'Unlimited'}
        {value ? suffix : ''}
      </Typography>
    </Stack>
  );
}
