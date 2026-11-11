import { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  Box,
  Card,
  CardContent,
  Grid,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import BusinessIcon from '@mui/icons-material/Business';
import PeopleIcon from '@mui/icons-material/People';
import DescriptionIcon from '@mui/icons-material/Description';
import ApprovalIcon from '@mui/icons-material/Approval';
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutline';
import { AreaChart, DonutChart } from '@/components/charts/Charts';
import { Loading, PageHeader } from '@/components/Page';
import { dateOnly } from '@/lib/format';
import { getPlatformDashboard } from './api';

const PERIODS = [
  { value: '7', label: 'Last 7 days' },
  { value: '30', label: 'Last 30 days' },
  { value: '90', label: 'Last 90 days' },
  { value: '365', label: 'Last 365 days' },
];

export function PlatformDashboardPage() {
  const [period, setPeriod] = useState('30');
  const [currency, setCurrency] = useState('');
  const range = useMemo(() => {
    const to = new Date();
    const from = new Date(to.getTime() - Number(period) * 24 * 60 * 60 * 1000);
    return { from: from.toISOString(), to: to.toISOString() };
  }, [period]);
  const dashboard = useQuery({
    queryKey: ['platform-dashboard', period],
    queryFn: () => getPlatformDashboard(range.from, range.to),
  });

  if (dashboard.isLoading) return <Loading />;
  if (dashboard.error || !dashboard.data) {
    return <Typography color="error">Platform analytics could not be loaded.</Typography>;
  }

  const data = dashboard.data;
  const summary = data.summary;
  const spendChart = data.spendByCurrency.map((row) => ({
    label: row.currency,
    value: Number(row.total ?? 0),
  }));
  const requestChart = data.monthlyRequests.map((row) => ({
    label: row.month,
    value: row.submitted + row.approved + row.rejected,
  }));
  const selectedCurrency = currency || data.spendByCurrency[0]?.currency || '';
  const monthlySpend = data.monthlySpend
    .filter((row) => row.currency === selectedCurrency)
    .sort((left, right) => left.month.localeCompare(right.month))
    .map((row) => ({
      label: row.month,
      value: Number(row.total ?? 0),
    }));
  const statusChart = data.requestStatus.map((row) => ({
    label: row.status.replaceAll('_', ' '),
    value: row.count,
  }));

  return (
    <>
      <PageHeader
        title="Platform dashboard"
        actions={
          <TextField
            select
            size="small"
            label="Period"
            value={period}
            onChange={(event) => setPeriod(event.target.value)}
            sx={{ minWidth: 180 }}
          >
            {PERIODS.map((option) => (
              <MenuItem key={option.value} value={option.value}>
                {option.label}
              </MenuItem>
            ))}
          </TextField>
        }
      />
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        {dateOnly(data.from)} – {dateOnly(data.to)} · aggregate tenant metrics only
      </Typography>

      <Grid container spacing={2} sx={{ mb: 3 }}>
        <MetricCard
          title="Tenants"
          value={summary.totalTenants}
          detail={`${summary.activeTenants} active · ${summary.suspendedTenants} suspended`}
          icon={<BusinessIcon color="primary" />}
        />
        <MetricCard
          title="New tenants"
          value={summary.newTenants}
          detail="Created in period"
          icon={<BusinessIcon color="secondary" />}
        />
        <MetricCard
          title="Active users"
          value={summary.totalUsers}
          detail="Tenant user profiles"
          icon={<PeopleIcon color="secondary" />}
        />
        <MetricCard
          title="Requests"
          value={summary.totalRequests}
          detail="Created in period"
          icon={<DescriptionIcon color="primary" />}
        />
        <MetricCard
          title="Pending approvals"
          value={summary.pendingApprovals}
          detail="Across all tenants"
          icon={<ApprovalIcon color="warning" />}
        />
        <MetricCard
          title="Exceptions"
          value={summary.mismatchedInvoices + summary.outboxDeadLetters}
          detail={`${summary.mismatchedInvoices} invoice · ${summary.outboxDeadLetters} outbox`}
          icon={<ErrorOutlineIcon color="error" />}
        />
      </Grid>

      <Grid container spacing={2} sx={{ mb: 3 }}>
        <Grid size={{ xs: 12, lg: 8 }}>
          <Card sx={{ height: '100%' }}>
            <CardContent>
              <Stack direction="row" justifyContent="space-between" alignItems="center" spacing={2}>
                <Box>
                  <Typography variant="h6">Monthly spend</Typography>
                  <Typography variant="body2" color="text.secondary">
                    Issued purchase orders by month; no FX conversion.
                  </Typography>
                </Box>
                <TextField
                  select
                  size="small"
                  label="Currency"
                  value={selectedCurrency}
                  onChange={(event) => setCurrency(event.target.value)}
                  sx={{ minWidth: 120 }}
                >
                  {data.spendByCurrency.map((row) => (
                    <MenuItem key={row.currency} value={row.currency}>
                      {row.currency}
                    </MenuItem>
                  ))}
                </TextField>
              </Stack>
              <AreaChart data={monthlySpend} ariaLabel="Platform monthly spend" />
            </CardContent>
          </Card>
        </Grid>
        <Grid size={{ xs: 12, lg: 4 }}>
          <Card sx={{ height: '100%' }}>
            <CardContent>
              <Typography variant="h6">Spend by currency</Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
                Share of issued spend; no FX conversion is applied.
              </Typography>
              <DonutChart
                data={spendChart}
                ariaLabel="Platform spend by currency"
                size={180}
                valueFormatter={(value) => Number(value).toLocaleString()}
              />
            </CardContent>
          </Card>
        </Grid>
        <Grid size={{ xs: 12, lg: 8 }}>
          <Card sx={{ height: '100%' }}>
            <CardContent>
              <Typography variant="h6">Request activity by month</Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
                Submitted, in approval, approved and rejected requests.
              </Typography>
              <AreaChart data={requestChart} ariaLabel="Platform request activity by month" />
            </CardContent>
          </Card>
        </Grid>
        <Grid size={{ xs: 12, lg: 4 }}>
          <Card sx={{ height: '100%' }}>
            <CardContent>
              <Typography variant="h6">Requests by status</Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
                Current request status mix.
              </Typography>
              <DonutChart data={statusChart} ariaLabel="Platform requests by status" size={180} />
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </>
  );
}

function MetricCard({
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
          <Typography variant="h4" sx={{ mt: 1 }}>
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
