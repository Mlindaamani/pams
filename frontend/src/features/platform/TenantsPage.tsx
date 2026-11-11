import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import {
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Grid,
  IconButton,
  MenuItem,
  Stack,
  Tab,
  Tabs,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import AddIcon from '@mui/icons-material/Add';
import { Can } from '@/auth/AbilityProvider';
import { DataTable } from '@/components/DataTable';
import { Loading, PageHeader } from '@/components/Page';
import { toast } from '@/components/Toast';
import { dateTime } from '@/lib/format';
import { listSubscriptionSummaries } from '@/features/subscription/api';
import { listTenants } from './api';
import { TenantPerformanceTab } from './TenantPerformanceTab';
import { TenantSubscriptionsTab } from './TenantSubscriptionsTab';

export function TenantsPage() {
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState('');
  const tenants = useQuery({
    queryKey: ['platform-tenants', search, status],
    queryFn: () =>
      listTenants({
        q: search || undefined,
        status: (status || undefined) as 'ACTIVE' | 'SUSPENDED' | undefined,
      }),
  });
  const subscriptions = useQuery({
    queryKey: ['platform-subscriptions'],
    queryFn: listSubscriptionSummaries,
  });
  const subscriptionByTenant = new Map(
    (subscriptions.data ?? []).map((summary) => [summary.organizationId, summary]),
  );
  const [searchParams, setSearchParams] = useSearchParams();
  const tabParam = searchParams.get('tab');
  const tab = tabParam === 'performance' ? 1 : tabParam === 'subscriptions' ? 2 : 0;

  if (tab === 0 && tenants.isLoading) return <Loading />;

  return (
    <>
      <PageHeader
        title="Tenants"
        actions={
          tab === 0 ? (
            <Can I="manage" a="Tenant">
              <Button
                component={Link}
                to="/platform/tenants/new"
                variant="contained"
                startIcon={<AddIcon />}
              >
                Add tenant
              </Button>
            </Can>
          ) : undefined
        }
      />
      <Tabs
        value={tab}
        onChange={(_, value: number) => {
          const next = value === 1 ? 'performance' : value === 2 ? 'subscriptions' : '';
          setSearchParams(next ? { tab: next } : {}, { replace: true });
        }}
        sx={{ mb: 2 }}
      >
        <Tab label="Tenants" />
        <Tab label="Tenant performance" />
        <Tab label="Subscriptions" />
      </Tabs>
      {tab === 0 ? (
        <>
          <Card sx={{ mb: 2 }}>
            <CardContent>
              <Grid container spacing={2}>
                <Grid size={{ xs: 12, md: 5 }}>
                  <TextField
                    size="small"
                    fullWidth
                    label="Search tenants"
                    value={search}
                    onChange={(event) => setSearch(event.target.value)}
                  />
                </Grid>
                <Grid size={{ xs: 12, md: 3 }}>
                  <TextField
                    select
                    size="small"
                    fullWidth
                    label="Status"
                    value={status}
                    onChange={(event) => setStatus(event.target.value)}
                  >
                    <MenuItem value="">All statuses</MenuItem>
                    <MenuItem value="ACTIVE">Active</MenuItem>
                    <MenuItem value="SUSPENDED">Suspended</MenuItem>
                  </TextField>
                </Grid>
                <Grid size={{ xs: 12, md: 4 }} sx={{ display: 'flex', alignItems: 'center' }}>
                  <Typography variant="body2" color="text.secondary">
                    {tenants.data?.totalElements ?? 0} tenant(s). Open a tenant for actions.
                  </Typography>
                </Grid>
              </Grid>
            </CardContent>
          </Card>

          <DataTable
            ariaLabel="Tenants"
            rows={tenants.data?.content ?? []}
            getRowId={(tenant) => tenant.id!}
            emptyMessage="No tenants match these filters."
            columns={[
              {
                key: 'name',
                label: 'Tenant',
                render: (tenant) => (
                  <Box>
                    <Typography
                      component={Link}
                      to={`/platform/tenants/${tenant.id}`}
                      variant="body2"
                      fontWeight={600}
                      sx={{ color: 'primary.main', textDecoration: 'none' }}
                    >
                      {tenant.name}
                    </Typography>
                  </Box>
                ),
                sortValue: (tenant) => tenant.name ?? '',
              },
              {
                key: 'id',
                label: 'Tenant ID',
                render: (tenant) => (
                  <Stack direction="row" spacing={0.5} alignItems="center">
                    <Typography variant="caption" sx={{ fontFamily: 'monospace' }}>
                      {tenant.id}
                    </Typography>
                    <Tooltip title="Copy tenant ID">
                      <IconButton
                        size="small"
                        aria-label="Copy tenant ID"
                        onClick={() => {
                          void navigator.clipboard.writeText(tenant.id!);
                          toast.success('Tenant ID copied.');
                        }}
                      >
                        <ContentCopyIcon sx={{ fontSize: 15 }} />
                      </IconButton>
                    </Tooltip>
                  </Stack>
                ),
              },
              {
                key: 'status',
                label: 'Status',
                render: (tenant) => (
                  <Chip
                    size="small"
                    label={tenant.status ?? 'ACTIVE'}
                    color={tenant.status === 'SUSPENDED' ? 'error' : 'success'}
                    variant={tenant.status === 'SUSPENDED' ? 'filled' : 'outlined'}
                  />
                ),
                sortValue: (tenant) => tenant.status ?? 'ACTIVE',
              },
              {
                key: 'plan',
                label: 'Plan',
                render: (tenant) => subscriptionByTenant.get(tenant.id!)?.planName ?? '—',
                sortValue: (tenant) => subscriptionByTenant.get(tenant.id!)?.planName ?? '',
              },
              {
                key: 'subscription',
                label: 'Subscription',
                render: (tenant) => {
                  const summary = subscriptionByTenant.get(tenant.id!);
                  if (!summary) return '—';
                  return (
                    <Box>
                      <Typography variant="body2">{summary.status}</Typography>
                      <Typography variant="caption" color="text.secondary">
                        {summary.trialEndsAt
                          ? `Trial ends ${dateTime(summary.trialEndsAt)}`
                          : summary.currentPeriodEnd
                            ? `Renews ${dateTime(summary.currentPeriodEnd)}`
                            : summary.billingInterval}
                      </Typography>
                    </Box>
                  );
                },
                sortValue: (tenant) => subscriptionByTenant.get(tenant.id!)?.status ?? '',
              },
              {
                key: 'provisioning',
                label: 'Provisioning',
                render: (tenant) => {
                  const summary = subscriptionByTenant.get(tenant.id!);
                  if (!summary?.provisioningStatus) return '—';
                  return (
                    <Box>
                      <Typography variant="body2">{summary.provisioningStatus}</Typography>
                      {summary.provisioningError && (
                        <Typography variant="caption" color="error">
                          {summary.provisioningError}
                        </Typography>
                      )}
                    </Box>
                  );
                },
                sortValue: (tenant) =>
                  subscriptionByTenant.get(tenant.id!)?.provisioningStatus ?? '',
              },
              {
                key: 'tolerance',
                label: 'Tolerance',
                render: (tenant) => `${tenant.invoiceTolerancePercent ?? 0}%`,
              },
              {
                key: 'created',
                label: 'Created',
                render: (tenant) => dateTime(tenant.createdAt),
                sortValue: (tenant) => tenant.createdAt ?? '',
              },
            ]}
          />
        </>
      ) : tab === 1 ? (
        <TenantPerformanceTab />
      ) : (
        <TenantSubscriptionsTab />
      )}
    </>
  );
}
