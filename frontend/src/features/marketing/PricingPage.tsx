import { Link as RouterLink } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Button, Card, CardContent, Chip, Container, Grid, Stack, Typography } from '@mui/material';
import CheckCircleRoundedIcon from '@mui/icons-material/CheckCircleRounded';
import { Loading } from '@/components/Page';
import { dateOnly } from '@/lib/format';
import { listPublicPlans } from './api';
import { PublicLayout } from './PublicLayout';

export function PricingPage() {
  const plans = useQuery({ queryKey: ['public-plans'], queryFn: listPublicPlans });

  if (plans.isLoading) return <Loading />;

  return (
    <PublicLayout>
      <Container maxWidth="lg" sx={{ py: { xs: 6, md: 10 } }}>
        <Stack alignItems="center" spacing={2} sx={{ mb: 6, textAlign: 'center' }}>
          <Typography variant="h2" sx={{ fontSize: { xs: 36, md: 50 } }}>
            Simple plans. Serious control.
          </Typography>
          <Typography color="text.secondary" sx={{ maxWidth: 680 }}>
            Start with a trial, then choose the plan that matches your team, procurement volume and
            compliance needs.
          </Typography>
        </Stack>
        <Grid container spacing={3} alignItems="stretch">
          {(plans.data ?? []).map((plan) => (
            <Grid key={plan.code} size={{ xs: 12, md: 4 }}>
              <Card sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
                <CardContent sx={{ p: 3, display: 'flex', flexDirection: 'column', flexGrow: 1 }}>
                  <Stack direction="row" justifyContent="space-between" alignItems="center">
                    <Typography variant="h5">{plan.name}</Typography>
                    {plan.code === 'GROWTH' && (
                      <Chip size="small" label="Popular" color="primary" />
                    )}
                  </Stack>
                  <Typography variant="h3" sx={{ mt: 2 }}>
                    ${Number(plan.monthlyPrice).toLocaleString()}
                    <Typography component="span" variant="body2" color="text.secondary">
                      /month
                    </Typography>
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    ${Number(plan.annualPrice).toLocaleString()}/year · {plan.trialDays}-day trial
                  </Typography>
                  <Typography variant="body2" color="text.secondary" sx={{ mt: 2, minHeight: 44 }}>
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
          ))}
        </Grid>
        <Typography variant="body2" color="text.secondary" sx={{ mt: 4, textAlign: 'center' }}>
          Prices are in USD. Annual pricing is billed once per year. Generated{' '}
          {dateOnly(new Date().toISOString())}.
        </Typography>
      </Container>
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
