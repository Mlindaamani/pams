import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Card, CardContent, Chip, MenuItem, Stack, TextField, Typography } from '@mui/material';
import { DataTable } from '@/components/DataTable';
import { Loading } from '@/components/Page';
import { dateOnly } from '@/lib/format';
import { getPlatformDashboard } from './api';

const PERIODS = [
  { value: '7', label: 'Last 7 days' },
  { value: '30', label: 'Last 30 days' },
  { value: '90', label: 'Last 90 days' },
  { value: '365', label: 'Last 365 days' },
];

export function TenantPerformanceTab() {
  const [period, setPeriod] = useState('30');
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
    return <Typography color="error">Tenant performance could not be loaded.</Typography>;
  }

  return (
    <>
      <Stack
        direction={{ xs: 'column', sm: 'row' }}
        justifyContent="space-between"
        alignItems={{ xs: 'flex-start', sm: 'center' }}
        spacing={2}
        sx={{ mb: 2 }}
      >
        <Typography variant="body2" color="text.secondary">
          {dateOnly(dashboard.data.from)} – {dateOnly(dashboard.data.to)} · aggregate tenant metrics
          only
        </Typography>
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
      </Stack>
      <Card>
        <CardContent>
          <DataTable
            ariaLabel="Tenant performance"
            rows={dashboard.data.tenants}
            getRowId={(tenant) => tenant.id}
            emptyMessage="No tenant activity in this period."
            columns={[
              {
                key: 'tenant',
                label: 'Tenant',
                render: (tenant) => (
                  <Typography
                    component={Link}
                    to={`/platform/tenants/${tenant.id}`}
                    variant="body2"
                    fontWeight={600}
                    sx={{ color: 'primary.main', textDecoration: 'none' }}
                  >
                    {tenant.name}
                  </Typography>
                ),
                sortValue: (tenant) => tenant.name,
              },
              {
                key: 'status',
                label: 'Status',
                render: (tenant) => (
                  <Chip
                    size="small"
                    label={tenant.status}
                    color={tenant.status === 'SUSPENDED' ? 'error' : 'success'}
                    variant="outlined"
                  />
                ),
                sortValue: (tenant) => tenant.status,
              },
              {
                key: 'users',
                label: 'Users',
                render: (tenant) => tenant.users,
                sortValue: (tenant) => tenant.users,
              },
              {
                key: 'requests',
                label: 'Requests',
                render: (tenant) => tenant.requests,
                sortValue: (tenant) => tenant.requests,
              },
              {
                key: 'pending',
                label: 'Pending approvals',
                render: (tenant) => tenant.pendingApprovals,
                sortValue: (tenant) => tenant.pendingApprovals,
              },
              {
                key: 'exceptions',
                label: 'Invoice mismatches',
                render: (tenant) => tenant.mismatchedInvoices,
                sortValue: (tenant) => tenant.mismatchedInvoices,
              },
              {
                key: 'spend',
                label: 'Issued spend',
                render: (tenant) =>
                  Object.entries(tenant.spendByCurrency).length === 0
                    ? '—'
                    : Object.entries(tenant.spendByCurrency)
                        .map(([currency, total]) => `${Number(total).toLocaleString()} ${currency}`)
                        .join(' · '),
              },
              {
                key: 'last',
                label: 'Last activity',
                render: (tenant) => (tenant.lastActivity ? dateOnly(tenant.lastActivity) : '—'),
                sortValue: (tenant) => tenant.lastActivity ?? '',
              },
            ]}
          />
        </CardContent>
      </Card>
    </>
  );
}
