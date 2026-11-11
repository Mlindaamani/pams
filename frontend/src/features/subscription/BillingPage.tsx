import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Divider,
  Grid,
  LinearProgress,
  MenuItem,
  Stack,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import CreditCardIcon from '@mui/icons-material/CreditCard';
import CheckCircleRoundedIcon from '@mui/icons-material/CheckCircleRounded';
import WorkspacePremiumIcon from '@mui/icons-material/WorkspacePremium';
import PeopleIcon from '@mui/icons-material/People';
import AccountTreeIcon from '@mui/icons-material/AccountTree';
import DescriptionIcon from '@mui/icons-material/Description';
import StorageIcon from '@mui/icons-material/Storage';
import { useRefreshEntitlements } from '@/auth/AbilityProvider';
import { Loading, PageHeader } from '@/components/Page';
import { toast } from '@/components/Toast';
import { problemMessage } from '@/api/client';
import { dateOnly } from '@/lib/format';
import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import {
  getSubscription,
  listSubscriptionPlans,
  openBillingPortal,
  type SubscriptionPlan,
} from './api';

export function BillingPage() {
  const [interval, setInterval] = useState<'MONTHLY' | 'ANNUAL'>('MONTHLY');
  const [searchParams, setSearchParams] = useSearchParams();
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const refreshEntitlements = useRefreshEntitlements();
  const subscription = useQuery({ queryKey: ['subscription'], queryFn: getSubscription });
  const plans = useQuery({ queryKey: ['subscription-plans'], queryFn: listSubscriptionPlans });
  const checkoutState = searchParams.get('checkout');

  useEffect(() => {
    if (!checkoutState) return;
    if (checkoutState === 'cancel') {
      toast.info('Checkout was canceled. Your current plan is unchanged.');
      setSearchParams({}, { replace: true });
      return;
    }
    if (checkoutState !== 'success') return;
    toast.success('Payment received. Your subscription and features are updating.');
    const refresh = () => {
      void queryClient.invalidateQueries({ queryKey: ['subscription'] });
      void queryClient.invalidateQueries({ queryKey: ['subscription-plans'] });
      refreshEntitlements();
    };
    refresh();
    const refreshes = [2000, 5000, 10000].map((delay) => window.setTimeout(refresh, delay));
    const cleanupUrl = window.setTimeout(() => setSearchParams({}, { replace: true }), 12000);
    return () => {
      refreshes.forEach((timer) => window.clearTimeout(timer));
      window.clearTimeout(cleanupUrl);
    };
  }, [checkoutState, queryClient, refreshEntitlements, setSearchParams]);

  const portal = useMutation({
    mutationFn: openBillingPortal,
    onSuccess: (result) => {
      if (result?.url) window.location.assign(result.url);
    },
    onError: (error) => toast.error(problemMessage(error)),
  });

  if (subscription.isLoading || plans.isLoading) return <Loading />;
  if (subscription.error || !subscription.data) {
    return <Alert severity="error">Subscription could not be loaded.</Alert>;
  }

  const data = subscription.data;
  if (!data.plan) {
    return <Alert severity="error">No subscription plan is configured for this tenant.</Alert>;
  }
  const plan = data.plan;
  const usage = data.usage ?? { users: 0, departments: 0, requestsThisMonth: 0, storageMb: 0 };

  return (
    <>
      <PageHeader
        title="Billing & subscription"
        actions={
          <Button
            variant="outlined"
            startIcon={<CreditCardIcon />}
            disabled={portal.isPending}
            onClick={() => portal.mutate()}
          >
            Manage billing
          </Button>
        }
      />
      <Grid container spacing={2} sx={{ mb: 2 }}>
        <Grid size={{ xs: 12, md: 4 }}>
          <Card sx={{ height: '100%' }}>
            <CardContent>
              <Stack direction="row" spacing={1} alignItems="center">
                <WorkspacePremiumIcon color="primary" />
                <Typography color="text.secondary">Current plan</Typography>
              </Stack>
              <Typography variant="h4" sx={{ mt: 1 }}>
                {plan.name}
              </Typography>
              <Stack direction="row" spacing={1} sx={{ mt: 1 }}>
                <Chip
                  size="small"
                  label={data.status}
                  color={
                    data.status === 'ACTIVE'
                      ? 'success'
                      : data.status === 'TRIALING'
                        ? 'info'
                        : 'warning'
                  }
                />
                <Chip size="small" variant="outlined" label={data.billingInterval} />
              </Stack>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 2 }}>
                {data.trialEndsAt
                  ? `Trial ends ${dateOnly(data.trialEndsAt)}`
                  : data.currentPeriodEnd
                    ? `Renews ${dateOnly(data.currentPeriodEnd)}`
                    : 'No renewal date'}
              </Typography>
            </CardContent>
          </Card>
        </Grid>
        <Grid size={{ xs: 12, md: 8 }}>
          <Card sx={{ height: '100%' }}>
            <CardContent>
              <Typography variant="h6" sx={{ mb: 2 }}>
                Usage
              </Typography>
              <Grid container spacing={2}>
                <UsageBar
                  icon={<PeopleIcon />}
                  label="Users"
                  value={usage.users ?? 0}
                  limit={plan.maxUsers}
                />
                <UsageBar
                  icon={<AccountTreeIcon />}
                  label="Departments"
                  value={usage.departments ?? 0}
                  limit={plan.maxDepartments}
                />
                <UsageBar
                  icon={<DescriptionIcon />}
                  label="Requests this month"
                  value={usage.requestsThisMonth ?? 0}
                  limit={plan.maxRequestsMonth}
                />
                <UsageBar
                  icon={<StorageIcon />}
                  label="Storage (MB)"
                  value={usage.storageMb ?? 0}
                  limit={plan.maxStorageMb}
                  suffix=" MB"
                />
              </Grid>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      {!data.checkoutAvailable && (
        <Alert severity="warning" sx={{ mb: 2 }}>
          Stripe test mode is not fully configured for this plan. Set the Stripe secret key and plan
          price IDs before starting checkout.
        </Alert>
      )}

      <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 2 }}>
        <Typography variant="h6">Available plans</Typography>
        <TextField
          select
          size="small"
          label="Billing interval"
          value={interval}
          onChange={(event) => setInterval(event.target.value as 'MONTHLY' | 'ANNUAL')}
          sx={{ minWidth: 160 }}
        >
          <MenuItem value="MONTHLY">Monthly</MenuItem>
          <MenuItem value="ANNUAL">Annual</MenuItem>
        </TextField>
      </Stack>

      <Grid container spacing={2}>
        {(plans.data ?? []).map((plan) => {
          const current = plan.code === data.plan?.code;
          const price = interval === 'ANNUAL' ? plan.annualPrice : plan.monthlyPrice;
          return (
            <Grid key={plan.code} size={{ xs: 12, md: 4 }}>
              <Card
                sx={{
                  height: '100%',
                  display: 'flex',
                  flexDirection: 'column',
                  borderColor: current ? 'primary.main' : 'divider',
                  borderWidth: current ? 2 : 1,
                }}
              >
                <CardContent sx={{ display: 'flex', flexDirection: 'column', flexGrow: 1 }}>
                  <Stack direction="row" justifyContent="space-between" alignItems="center">
                    <Typography variant="h6">{plan.name}</Typography>
                    {current && <Chip size="small" label="Current" color="primary" />}
                  </Stack>
                  <Typography variant="h4" sx={{ mt: 1 }}>
                    ${Number(price).toLocaleString()}
                    <Typography component="span" variant="body2" color="text.secondary">
                      /{interval === 'ANNUAL' ? 'year' : 'month'}
                    </Typography>
                  </Typography>
                  <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
                    {plan.description}
                  </Typography>
                  <Box sx={{ mt: 2 }}>
                    <PlanLine label="Users" value={plan.maxUsers} />
                    <PlanLine label="Departments" value={plan.maxDepartments} />
                    <PlanLine label="Requests/month" value={plan.maxRequestsMonth} />
                    <PlanLine label="Storage" value={plan.maxStorageMb} suffix=" MB" />
                  </Box>
                  <Divider sx={{ my: 2 }} />
                  <Typography variant="subtitle2" sx={{ mb: 1 }}>
                    Included features
                  </Typography>
                  <PlanFeatureList features={plan.features ?? []} />
                  <Box sx={{ flexGrow: 1 }} />
                  <Button
                    fullWidth
                    sx={{ mt: 2 }}
                    variant={current ? 'outlined' : 'contained'}
                    disabled={current || !data.checkoutAvailable}
                    onClick={() =>
                      navigate(
                        `/admin/billing/change-plan?planCode=${plan.code}&interval=${interval}`,
                      )
                    }
                  >
                    {current ? 'Current plan' : 'Choose plan'}
                  </Button>
                </CardContent>
              </Card>
            </Grid>
          );
        })}
      </Grid>
    </>
  );
}

function PlanFeatureList({ features }: { features: NonNullable<SubscriptionPlan['features']> }) {
  if (features.length === 0) {
    return (
      <Typography variant="body2" color="text.secondary">
        Feature list is being configured.
      </Typography>
    );
  }
  return (
    <Stack component="ul" spacing={0.75} sx={{ listStyle: 'none', p: 0, m: 0 }}>
      {features.map((feature) => (
        <Box component="li" key={feature.code}>
          <Tooltip title={feature.description ?? ''} placement="top" arrow>
            <Stack direction="row" spacing={1} alignItems="flex-start">
              <CheckCircleRoundedIcon
                aria-hidden
                sx={{ fontSize: 18, color: 'success.main', mt: '1px', flexShrink: 0 }}
              />
              <Typography variant="body2">{feature.name ?? feature.code}</Typography>
            </Stack>
          </Tooltip>
        </Box>
      ))}
    </Stack>
  );
}

function UsageBar({
  icon,
  label,
  value,
  limit,
  suffix = '',
}: {
  icon: React.ReactNode;
  label: string;
  value: number;
  limit?: number | null;
  suffix?: string;
}) {
  const percentage = limit ? Math.min(100, (value / limit) * 100) : 0;
  return (
    <Grid size={{ xs: 12, sm: 6 }}>
      <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 0.5 }}>
        {icon}
        <Typography variant="body2" sx={{ flexGrow: 1 }}>
          {label}
        </Typography>
        <Typography variant="body2" color="text.secondary">
          {value}
          {suffix} / {limit ?? '∞'}
        </Typography>
      </Stack>
      <LinearProgress
        variant="determinate"
        value={percentage}
        color={percentage >= 90 ? 'error' : percentage >= 75 ? 'warning' : 'primary'}
        sx={{ height: 7, borderRadius: 1 }}
      />
    </Grid>
  );
}

function PlanLine({
  label,
  value,
  suffix = '',
}: {
  label: string;
  value?: number | null;
  suffix?: string;
}) {
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
