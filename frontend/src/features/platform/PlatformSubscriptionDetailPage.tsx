import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
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
  MenuItem,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import { Loading, PageHeader } from '@/components/Page';
import { toast } from '@/components/Toast';
import { problemMessage } from '@/api/client';
import { dateOnly } from '@/lib/format';
import {
  activateTenantSubscription,
  cancelTenantSubscription,
  changeTenantPlan,
  extendTenantTrial,
  listPlatformSubscriptionPlans,
  reactivateTenantSubscription,
  reconcileTenantSubscription,
} from '@/features/subscription/api';
import { getTenantDetail } from './api';

const usd = (value?: number | null) =>
  new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    maximumFractionDigits: 2,
  }).format(Number(value ?? 0));

export function PlatformSubscriptionDetailPage() {
  const { id = '' } = useParams();
  const queryClient = useQueryClient();
  const detail = useQuery({ queryKey: ['tenant-detail', id], queryFn: () => getTenantDetail(id) });
  const plans = useQuery({
    queryKey: ['platform-subscription-plans'],
    queryFn: listPlatformSubscriptionPlans,
  });
  const [planCode, setPlanCode] = useState('');
  const [billingInterval, setBillingInterval] = useState<'MONTHLY' | 'ANNUAL'>('MONTHLY');
  const [trialDays, setTrialDays] = useState('14');

  const tenant = detail.data?.tenant;
  const subscription = detail.data?.subscription;

  useEffect(() => {
    if (!subscription) return;
    setPlanCode(subscription.planCode ?? 'GROWTH');
    setBillingInterval(subscription.billingInterval === 'ANNUAL' ? 'ANNUAL' : 'MONTHLY');
  }, [subscription]);

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: ['tenant-detail', id] });
    void queryClient.invalidateQueries({ queryKey: ['platform-billing-metrics'] });
    void queryClient.invalidateQueries({ queryKey: ['platform-subscriptions'] });
    void queryClient.invalidateQueries({ queryKey: ['platform-tenants'] });
    void queryClient.invalidateQueries({ queryKey: ['platform-dashboard'] });
  };

  const changePlan = useMutation({
    mutationFn: () => changeTenantPlan(id, planCode, billingInterval),
    onSuccess: () => {
      toast.success('Subscription plan updated.');
      refresh();
    },
    onError: (error) => toast.error(problemMessage(error)),
  });
  const extendTrial = useMutation({
    mutationFn: () => extendTenantTrial(id, Number(trialDays)),
    onSuccess: () => {
      toast.success('Trial extended.');
      refresh();
    },
    onError: (error) => toast.error(problemMessage(error)),
  });
  const activate = useMutation({
    mutationFn: () => activateTenantSubscription(id),
    onSuccess: () => {
      toast.success('Subscription activated.');
      refresh();
    },
    onError: (error) => toast.error(problemMessage(error)),
  });
  const cancelNow = useMutation({
    mutationFn: () => cancelTenantSubscription(id, false),
    onSuccess: () => {
      toast.success('Subscription canceled.');
      refresh();
    },
    onError: (error) => toast.error(problemMessage(error)),
  });
  const cancelAtEnd = useMutation({
    mutationFn: () => cancelTenantSubscription(id, true),
    onSuccess: () => {
      toast.success('Subscription will cancel at period end.');
      refresh();
    },
    onError: (error) => toast.error(problemMessage(error)),
  });
  const reactivate = useMutation({
    mutationFn: () => reactivateTenantSubscription(id),
    onSuccess: () => {
      toast.success('Subscription reactivated.');
      refresh();
    },
    onError: (error) => toast.error(problemMessage(error)),
  });
  const reconcile = useMutation({
    mutationFn: () => reconcileTenantSubscription(id),
    onSuccess: () => {
      toast.success('Subscription reconciled with Stripe.');
      refresh();
    },
    onError: (error) => toast.error(problemMessage(error)),
  });
  const busy =
    changePlan.isPending ||
    extendTrial.isPending ||
    activate.isPending ||
    cancelNow.isPending ||
    cancelAtEnd.isPending ||
    reactivate.isPending ||
    reconcile.isPending;

  if (detail.isLoading) return <Loading />;
  if (detail.error || !tenant) {
    return <Alert severity="error">Tenant subscription could not be loaded.</Alert>;
  }

  return (
    <>
      <PageHeader
        title={tenant.name ?? 'Tenant subscription'}
        backTo="/platform/revenue"
        backLabel="Back to revenue"
        actions={
          <Button component={Link} to={`/platform/tenants/${id}`} variant="outlined" size="small">
            Tenant details
          </Button>
        }
      />
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        {tenant.slug} · platform-side subscription administration
      </Typography>

      {!subscription ? (
        <Alert severity="warning">No subscription is configured for this tenant.</Alert>
      ) : (
        <Grid container spacing={2}>
          <Grid size={{ xs: 12, md: 4 }}>
            <Card sx={{ height: '100%' }}>
              <CardContent>
                <Typography color="text.secondary">Current subscription</Typography>
                <Typography variant="h4" sx={{ mt: 1 }}>
                  {subscription.planName ?? subscription.planCode}
                </Typography>
                <Stack direction="row" spacing={1} sx={{ mt: 1 }} flexWrap="wrap" useFlexGap>
                  <Chip
                    size="small"
                    label={subscription.status ?? '—'}
                    color={
                      subscription.status === 'ACTIVE'
                        ? 'success'
                        : subscription.status === 'TRIALING'
                          ? 'info'
                          : subscription.status === 'PAST_DUE' || subscription.status === 'UNPAID'
                            ? 'warning'
                            : 'default'
                    }
                    variant="outlined"
                  />
                  <Chip size="small" variant="outlined" label={subscription.billingInterval} />
                  <Chip size="small" label={`MRR ${usd(subscription.mrr)}`} />
                </Stack>
                <Divider sx={{ my: 2 }} />
                <Stack spacing={1}>
                  <Info label="Billing source" value={subscription.billingSource ?? '—'} />
                  <Info
                    label={subscription.status === 'TRIALING' ? 'Trial ends' : 'Period ends'}
                    value={
                      subscription.status === 'TRIALING'
                        ? dateOnly(subscription.trialEndsAt)
                        : dateOnly(subscription.currentPeriodEnd)
                    }
                  />
                  <Info
                    label="Renewal"
                    value={
                      subscription.status === 'CANCELED'
                        ? 'Canceled'
                        : subscription.cancelAtPeriodEnd
                          ? 'Cancels at period end'
                          : 'Auto-renews'
                    }
                  />
                  <Info label="Activated" value={dateOnly(subscription.activatedAt)} />
                  <Info
                    label="Monthly / annual price"
                    value={`${usd(subscription.monthlyPrice)} / ${usd(subscription.annualPrice)}`}
                  />
                </Stack>
              </CardContent>
            </Card>
          </Grid>

          <Grid size={{ xs: 12, md: 8 }}>
            <Card sx={{ mb: 2 }}>
              <CardContent>
                <Typography variant="h6" sx={{ mb: 2 }}>
                  Change plan
                </Typography>
                <Grid container spacing={2}>
                  <Grid size={{ xs: 12, sm: 7 }}>
                    <TextField
                      select
                      fullWidth
                      label="Plan"
                      value={planCode}
                      onChange={(event) => setPlanCode(event.target.value)}
                    >
                      {(plans.data ?? []).map((plan) => (
                        <MenuItem key={plan.code} value={plan.code}>
                          {plan.name} — ${Number(plan.monthlyPrice).toLocaleString()}/mo
                        </MenuItem>
                      ))}
                    </TextField>
                  </Grid>
                  <Grid size={{ xs: 12, sm: 5 }}>
                    <TextField
                      select
                      fullWidth
                      label="Interval"
                      value={billingInterval}
                      onChange={(event) =>
                        setBillingInterval(event.target.value as 'MONTHLY' | 'ANNUAL')
                      }
                    >
                      <MenuItem value="MONTHLY">Monthly</MenuItem>
                      <MenuItem value="ANNUAL">Annual</MenuItem>
                    </TextField>
                  </Grid>
                </Grid>
                <Button
                  variant="contained"
                  sx={{ mt: 2 }}
                  disabled={
                    busy ||
                    (planCode === subscription.planCode &&
                      billingInterval === subscription.billingInterval)
                  }
                  onClick={() => changePlan.mutate()}
                >
                  Apply plan
                </Button>
              </CardContent>
            </Card>

            {subscription.status === 'TRIALING' && (
              <Card sx={{ mb: 2 }}>
                <CardContent>
                  <Typography variant="h6" sx={{ mb: 2 }}>
                    Extend trial
                  </Typography>
                  <Stack direction="row" spacing={1} alignItems="center">
                    <TextField
                      label="Additional days"
                      type="number"
                      value={trialDays}
                      onChange={(event) => setTrialDays(event.target.value)}
                      slotProps={{ htmlInput: { min: 1, max: 365 } }}
                      sx={{ width: 170 }}
                    />
                    <Button
                      variant="outlined"
                      disabled={busy || !trialDays}
                      onClick={() => extendTrial.mutate()}
                    >
                      Extend trial
                    </Button>
                  </Stack>
                </CardContent>
              </Card>
            )}

            <Card>
              <CardContent>
                <Typography variant="h6" sx={{ mb: 2 }}>
                  Lifecycle
                </Typography>
                <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                  {subscription.status !== 'ACTIVE' && (
                    <Button
                      variant="contained"
                      color="success"
                      disabled={busy}
                      onClick={() => activate.mutate()}
                    >
                      Activate / mark paid
                    </Button>
                  )}
                  {subscription.status === 'ACTIVE' && !subscription.cancelAtPeriodEnd && (
                    <Button
                      variant="outlined"
                      color="warning"
                      disabled={busy}
                      onClick={() => cancelAtEnd.mutate()}
                    >
                      Cancel at period end
                    </Button>
                  )}
                  {(subscription.status === 'ACTIVE' || subscription.status === 'TRIALING') && (
                    <Button
                      variant="outlined"
                      color="error"
                      disabled={busy}
                      onClick={() => cancelNow.mutate()}
                    >
                      Cancel immediately
                    </Button>
                  )}
                  {(subscription.status === 'CANCELED' || subscription.cancelAtPeriodEnd) && (
                    <Button variant="contained" disabled={busy} onClick={() => reactivate.mutate()}>
                      Reactivate
                    </Button>
                  )}
                  {subscription.billingSource === 'STRIPE' && (
                    <Button variant="outlined" disabled={busy} onClick={() => reconcile.mutate()}>
                      Reconcile with Stripe
                    </Button>
                  )}
                </Stack>
                <Alert severity="info" sx={{ mt: 2 }}>
                  Activate is for offline or comped payments. Use the tenant&apos;s billing page
                  when the customer pays online.
                </Alert>
              </CardContent>
            </Card>
          </Grid>
        </Grid>
      )}
    </>
  );
}

function Info({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <Box>
      <Typography variant="caption" color="text.secondary" display="block">
        {label}
      </Typography>
      <Typography variant="body2">{value || '—'}</Typography>
    </Box>
  );
}
