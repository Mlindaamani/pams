import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Alert, Box, Button, Card, CardContent, Chip, Stack, Typography } from '@mui/material';
import { Can } from '@/auth/AbilityProvider';
import { DataTable } from '@/components/DataTable';
import { Loading } from '@/components/Page';
import { dateOnly } from '@/lib/format';
import {
  auditSubscriptionReconciliation,
  listSubscriptionSummaries,
} from '@/features/subscription/api';

const usd = (value?: number | null) =>
  new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    maximumFractionDigits: 0,
  }).format(Number(value ?? 0));

export function TenantSubscriptionsTab() {
  const [auditRequested, setAuditRequested] = useState(false);
  const subscriptions = useQuery({
    queryKey: ['platform-subscriptions'],
    queryFn: listSubscriptionSummaries,
  });
  const reconciliation = useQuery({
    queryKey: ['subscription-reconciliation'],
    queryFn: auditSubscriptionReconciliation,
    enabled: auditRequested,
    retry: false,
  });

  if (subscriptions.isLoading) return <Loading />;

  return (
    <Stack spacing={2}>
      <Card>
        <CardContent>
          <Stack
            direction={{ xs: 'column', sm: 'row' }}
            justifyContent="space-between"
            alignItems={{ xs: 'flex-start', sm: 'center' }}
            spacing={1}
            sx={{ mb: 2 }}
          >
            <Box>
              <Typography variant="h6">Tenant subscriptions</Typography>
              <Typography variant="body2" color="text.secondary">
                Plan, lifecycle and renewal state for every tenant.
              </Typography>
            </Box>
            <Stack direction="row" spacing={1}>
              <Button
                size="small"
                variant="outlined"
                disabled={reconciliation.isFetching}
                onClick={() => {
                  if (auditRequested) void reconciliation.refetch();
                  else setAuditRequested(true);
                }}
              >
                {reconciliation.isFetching ? 'Checking Stripe…' : 'Run reconciliation audit'}
              </Button>
              <Button component={Link} to="/platform/revenue" size="small" variant="outlined">
                View revenue
              </Button>
            </Stack>
          </Stack>
          <DataTable
            ariaLabel="Tenant subscriptions"
            rows={subscriptions.data ?? []}
            getRowId={(tenant) => tenant.organizationId ?? ''}
            emptyMessage="No tenant subscriptions yet."
            columns={[
              {
                key: 'tenant',
                label: 'Tenant',
                render: (tenant) => (
                  <Box>
                    <Typography variant="body2" fontWeight={600}>
                      {tenant.organizationName}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      {tenant.organizationId}
                    </Typography>
                  </Box>
                ),
                sortValue: (tenant) => tenant.organizationName ?? '',
              },
              {
                key: 'plan',
                label: 'Plan',
                render: (tenant) => (
                  <Box>
                    <Typography variant="body2">{tenant.planName ?? tenant.planCode}</Typography>
                    <Typography variant="caption" color="text.secondary">
                      {tenant.billingInterval}
                    </Typography>
                  </Box>
                ),
                sortValue: (tenant) => tenant.planName ?? '',
              },
              {
                key: 'status',
                label: 'Status',
                render: (tenant) => <Typography variant="body2">{tenant.status ?? '—'}</Typography>,
                sortValue: (tenant) => tenant.status ?? '',
              },
              {
                key: 'mrr',
                label: 'MRR',
                align: 'right',
                render: (tenant) => usd(tenant.mrr),
                sortValue: (tenant) => Number(tenant.mrr ?? 0),
              },
              {
                key: 'renewal',
                label: 'Trial / renewal',
                render: (tenant) =>
                  tenant.status === 'TRIALING'
                    ? `Trial ends ${dateOnly(tenant.trialEndsAt)}`
                    : tenant.currentPeriodEnd
                      ? `${tenant.cancelAtPeriodEnd ? 'Cancels' : 'Renews'} ${dateOnly(tenant.currentPeriodEnd)}`
                      : '—',
                sortValue: (tenant) => tenant.currentPeriodEnd ?? tenant.trialEndsAt ?? '',
              },
              {
                key: 'actions',
                label: 'Actions',
                align: 'right',
                render: (tenant) => (
                  <Can I="manage" a="Tenant">
                    <Button
                      component={Link}
                      to={`/platform/revenue/${tenant.organizationId}`}
                      size="small"
                    >
                      Manage
                    </Button>
                  </Can>
                ),
              },
            ]}
          />
        </CardContent>
      </Card>
      {auditRequested && reconciliation.data && (
        <Card>
          <CardContent>
            <Typography variant="h6">Stripe reconciliation</Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
              Checked {reconciliation.data.checked ?? 0} Stripe-backed subscription(s) at{' '}
              {reconciliation.data.generatedAt
                ? new Date(reconciliation.data.generatedAt).toLocaleString()
                : '—'}
              .
            </Typography>
            {(reconciliation.data.issues ?? []).length === 0 ? (
              <Alert severity="success">Stripe and local subscription state are aligned.</Alert>
            ) : (
              <Stack spacing={1}>
                {(reconciliation.data.issues ?? []).map((issue) => (
                  <Box
                    key={issue.organizationId}
                    sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 1.5, p: 1.5 }}
                  >
                    <Stack
                      direction="row"
                      justifyContent="space-between"
                      alignItems="center"
                      spacing={2}
                    >
                      <Typography variant="body2" fontWeight={600}>
                        {issue.organizationName}
                      </Typography>
                      <Chip size="small" color="warning" label="Mismatch" variant="outlined" />
                    </Stack>
                    {issue.error ? (
                      <Typography variant="body2" color="error" sx={{ mt: 1 }}>
                        {issue.error}
                      </Typography>
                    ) : (
                      <Stack component="ul" sx={{ mt: 1, mb: 0, pl: 2.5 }}>
                        {(issue.differences ?? []).map((difference) => (
                          <Typography
                            component="li"
                            variant="body2"
                            color="text.secondary"
                            key={difference}
                          >
                            {difference}
                          </Typography>
                        ))}
                      </Stack>
                    )}
                    <Button
                      component={Link}
                      to={`/platform/revenue/${issue.organizationId}`}
                      size="small"
                      sx={{ mt: 1 }}
                    >
                      Open subscription
                    </Button>
                  </Box>
                ))}
              </Stack>
            )}
            {reconciliation.error && (
              <Alert severity="error">Reconciliation audit could not be completed.</Alert>
            )}
          </CardContent>
        </Card>
      )}
    </Stack>
  );
}
