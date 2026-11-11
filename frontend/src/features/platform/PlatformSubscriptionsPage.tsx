import { useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Alert, Box, Card, CardContent, Divider, Grid, Stack, Typography } from '@mui/material';
import AttachMoneyIcon from '@mui/icons-material/AttachMoney';
import AutorenewIcon from '@mui/icons-material/Autorenew';
import GroupsIcon from '@mui/icons-material/Groups';
import HourglassBottomIcon from '@mui/icons-material/HourglassBottom';
import PaidIcon from '@mui/icons-material/Paid';
import WarningAmberIcon from '@mui/icons-material/WarningAmber';
import { AreaChart, DonutChart } from '@/components/charts/Charts';
import { Loading, PageHeader } from '@/components/Page';
import { dateOnly } from '@/lib/format';
import { getBillingMetrics } from '@/features/subscription/api';

const usd = (value?: number | null) =>
  new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    maximumFractionDigits: 0,
  }).format(Number(value ?? 0));

export function PlatformSubscriptionsPage() {
  const metrics = useQuery({ queryKey: ['platform-billing-metrics'], queryFn: getBillingMetrics });

  const chart = useMemo(() => {
    if (!metrics.data) return { trend: [], plans: [], status: [] };
    return {
      trend: (metrics.data.mrrTrend ?? []).map((point) => ({
        label: dateOnly(point.date),
        value: Number(point.mrr ?? 0),
      })),
      plans: (metrics.data.planRevenue ?? []).map((plan) => ({
        label: plan.planName ?? plan.planCode ?? 'Plan',
        value: Number(plan.mrr ?? 0),
      })),
      status: [
        { label: 'Paying', value: metrics.data.payingTenants ?? 0 },
        { label: 'Trialing', value: metrics.data.trialingTenants ?? 0 },
        { label: 'At risk', value: metrics.data.pastDueTenants ?? 0 },
        { label: 'Canceled', value: metrics.data.canceledTenants ?? 0 },
      ],
    };
  }, [metrics.data]);

  if (metrics.isLoading) return <Loading />;
  if (metrics.error || !metrics.data) {
    return <Alert severity="error">Subscription analytics could not be loaded.</Alert>;
  }

  const data = metrics.data;
  return (
    <>
      <PageHeader title="Revenue" />
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        SaaS revenue performance, recurring revenue and renewal outlook.
      </Typography>

      <Grid container spacing={2} sx={{ mb: 3 }}>
        <Metric
          title="MRR"
          value={usd(data.mrr)}
          detail={
            data.mrrChange30d == null
              ? 'History starts today'
              : `${Number(data.mrrChange30d) >= 0 ? '+' : ''}${usd(data.mrrChange30d)} vs 30 days ago`
          }
          icon={<AttachMoneyIcon color="primary" />}
        />
        <Metric
          title="ARR"
          value={usd(data.arr)}
          detail="Annualized active MRR"
          icon={<PaidIcon color="success" />}
        />
        <Metric
          title="ARPU"
          value={usd(data.arpu)}
          detail={`${data.payingTenants ?? 0} paying tenant(s)`}
          icon={<GroupsIcon color="secondary" />}
        />
        <Metric
          title="Trial pipeline"
          value={usd(data.trialPipelineMrr)}
          detail={`${data.trialingTenants ?? 0} tenant(s) trialing`}
          icon={<HourglassBottomIcon color="info" />}
        />
        <Metric
          title="At-risk MRR"
          value={usd(data.atRiskMrr)}
          detail={`${data.pastDueTenants ?? 0} past due or unpaid`}
          icon={<WarningAmberIcon color="warning" />}
        />
        <Metric
          title="New paying"
          value={data.newPayingTenants30d ?? 0}
          detail="Activated in last 30 days"
          icon={<AutorenewIcon color="success" />}
        />
      </Grid>

      <Grid container spacing={2} sx={{ mb: 3 }}>
        <Grid size={{ xs: 12, lg: 8 }}>
          <Card sx={{ height: '100%' }}>
            <CardContent>
              <Typography variant="h6">MRR trend</Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
                Daily snapshots of active monthly recurring revenue.
              </Typography>
              {chart.trend.length < 2 && (
                <Alert severity="info" sx={{ mb: 1 }}>
                  The first snapshot was recorded today. The trend builds automatically each day.
                </Alert>
              )}
              <AreaChart data={chart.trend} ariaLabel="Platform MRR trend" />
            </CardContent>
          </Card>
        </Grid>
        <Grid size={{ xs: 12, lg: 4 }}>
          <Card sx={{ height: '100%' }}>
            <CardContent>
              <Typography variant="h6">MRR by plan</Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
                Active revenue mix by subscription plan.
              </Typography>
              <DonutChart
                data={chart.plans}
                ariaLabel="MRR by plan"
                size={180}
                valueFormatter={usd}
              />
            </CardContent>
          </Card>
        </Grid>
        <Grid size={{ xs: 12, md: 7 }}>
          <Card sx={{ height: '100%' }}>
            <CardContent>
              <Typography variant="h6">Subscription status</Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
                Tenant count by lifecycle state.
              </Typography>
              <DonutChart data={chart.status} ariaLabel="Subscription status" size={180} />
            </CardContent>
          </Card>
        </Grid>
        <Grid size={{ xs: 12, md: 5 }}>
          <Card sx={{ height: '100%' }}>
            <CardContent>
              <Typography variant="h6">Renewals in 30 days</Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
                Active subscriptions due for renewal.
              </Typography>
              {(data.renewals ?? []).length === 0 ? (
                <Typography variant="body2" color="text.secondary">
                  No renewals in the next 30 days.
                </Typography>
              ) : (
                <Stack divider={<Divider flexItem />} spacing={1}>
                  {(data.renewals ?? []).slice(0, 6).map((tenant) => (
                    <Stack
                      key={tenant.organizationId}
                      direction="row"
                      justifyContent="space-between"
                      spacing={2}
                    >
                      <Box>
                        <Typography variant="body2" fontWeight={600}>
                          {tenant.organizationName}
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                          {tenant.planName} · {tenant.billingInterval}
                        </Typography>
                      </Box>
                      <Box textAlign="right">
                        <Typography variant="body2">{usd(tenant.mrr)}</Typography>
                        <Typography variant="caption" color="text.secondary">
                          {dateOnly(tenant.currentPeriodEnd)}
                        </Typography>
                      </Box>
                    </Stack>
                  ))}
                </Stack>
              )}
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </>
  );
}

function Metric({
  title,
  value,
  detail,
  icon,
}: {
  title: string;
  value: string | number;
  detail: string;
  icon: React.ReactNode;
}) {
  return (
    <Grid size={{ xs: 12, sm: 6, lg: 2 }}>
      <Card sx={{ height: '100%' }}>
        <CardContent>
          <Stack direction="row" spacing={1} alignItems="center">
            {icon}
            <Typography color="text.secondary">{title}</Typography>
          </Stack>
          <Typography variant="h5" sx={{ mt: 1 }}>
            {value}
          </Typography>
          <Typography variant="caption" color="text.secondary">
            {detail}
          </Typography>
        </CardContent>
      </Card>
    </Grid>
  );
}
