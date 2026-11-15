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
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
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
import { VirtualizedCombobox } from '@/components/VirtualizedCombobox';

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
        <VirtualizedCombobox
          size="small"
          label="Billing interval"
          value={interval}
          onChange={(event) => setInterval(event.target.value as 'MONTHLY' | 'ANNUAL')}
          sx={{ minWidth: 160 }}
        >
          <MenuItem value="MONTHLY">Monthly</MenuItem>
          <MenuItem value="ANNUAL">Annual</MenuItem>
        </VirtualizedCombobox>
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
                  <Typography variant="body2" color="text.secondary" sx={{ mt: 1, minHeight: 42 }}>
                    {plan.description}
                  </Typography>
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
                  <Divider sx={{ my: 2 }} />
                  <Typography variant="subtitle2" sx={{ mb: 1 }}>
                    Included features
                  </Typography>
                  <PlanFeatureList features={plan.features ?? []} />
                </CardContent>
              </Card>
            </Grid>
          );
        })}
      </Grid>

      <Card sx={{ mt: 2 }}>
        <CardContent>
          <Typography variant="h6" sx={{ mb: 0.5 }}>
            Compare plans
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            Limits across every plan.
          </Typography>
          <TableContainer sx={{ overflowX: 'auto' }}>
            <Table size="small" sx={{ minWidth: 760 }} aria-label="Compare subscription plans">
              <TableHead>
                <TableRow>
                  <TableCell sx={{ fontWeight: 700 }}>Limits</TableCell>
                  {(plans.data ?? []).map((plan) => {
                    const price = interval === 'ANNUAL' ? plan.annualPrice : plan.monthlyPrice;
                    const current = plan.code === data.plan?.code;
                    return (
                      <TableCell key={plan.code} sx={{ minWidth: 180 }}>
                        <Stack direction="row" spacing={1} alignItems="center">
                          <Typography variant="body2" fontWeight={700}>
                            {plan.name}
                          </Typography>
                          {current && <Chip size="small" label="Current" color="primary" />}
                        </Stack>
                        <Typography variant="caption" color="text.secondary">
                          ${Number(price).toLocaleString()}/
                          {interval === 'ANNUAL' ? 'year' : 'month'}
                        </Typography>
                      </TableCell>
                    );
                  })}
                </TableRow>
              </TableHead>
              <TableBody>
                {[
                  {
                    label: 'Users',
                    value: (plan: SubscriptionPlan) => plan.maxUsers,
                  },
                  {
                    label: 'Departments',
                    value: (plan: SubscriptionPlan) => plan.maxDepartments,
                  },
                  {
                    label: 'Requests/month',
                    value: (plan: SubscriptionPlan) => plan.maxRequestsMonth,
                  },
                  {
                    label: 'Storage',
                    value: (plan: SubscriptionPlan) => plan.maxStorageMb,
                    suffix: ' MB',
                  },
                ].map((row) => (
                  <TableRow key={row.label}>
                    <TableCell component="th" scope="row" sx={{ fontWeight: 600 }}>
                      {row.label}
                    </TableCell>
                    {(plans.data ?? []).map((plan) => {
                      const value = row.value(plan);
                      return (
                        <TableCell key={plan.code}>
                          {value == null
                            ? 'Unlimited'
                            : `${value}${value ? (row.suffix ?? '') : ''}`}
                        </TableCell>
                      );
                    })}
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        </CardContent>
      </Card>
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
