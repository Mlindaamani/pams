import { Link, useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Stack,
  Tab,
  Tabs,
  Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import { Can } from '@/auth/AbilityProvider';
import { DataTable } from '@/components/DataTable';
import { Loading, PageHeader } from '@/components/Page';
import { listAllPlatformPlans } from '@/features/subscription/api';
import { PlatformFeaturesTab } from './PlatformFeaturesTab';

export function PlatformPlansPage() {
  const plans = useQuery({ queryKey: ['platform-all-plans'], queryFn: listAllPlatformPlans });
  const [searchParams, setSearchParams] = useSearchParams();
  const tab = searchParams.get('tab') === 'features' ? 1 : 0;

  if (tab === 0 && plans.isLoading) return <Loading />;

  return (
    <>
      <PageHeader
        title="Subscription plans"
        actions={
          tab === 0 ? (
            <Can I="manage" a="Tenant">
              <Button
                component={Link}
                to="/platform/plans/new"
                variant="contained"
                startIcon={<AddIcon />}
              >
                New plan
              </Button>
            </Can>
          ) : undefined
        }
      />
      <Tabs
        value={tab}
        onChange={(_, value: number) =>
          setSearchParams(value === 1 ? { tab: 'features' } : {}, { replace: true })
        }
        sx={{ mb: 2 }}
      >
        <Tab label="Plans" />
        <Tab label="Features" />
      </Tabs>
      {tab === 0 ? (
        <>
          <Alert severity="info" sx={{ mb: 2 }}>
            Plans define pricing, trial length and tenant limits. Deactivated plans remain on
            existing tenants but are no longer offered to new tenants or shown in checkout.
          </Alert>
          <Card>
            <CardContent>
              <DataTable
                ariaLabel="Subscription plans"
                rows={plans.data ?? []}
                getRowId={(plan) => plan.code!}
                emptyMessage="No subscription plans yet."
                columns={[
                  {
                    key: 'plan',
                    label: 'Plan',
                    render: (plan) => (
                      <Box>
                        <Typography variant="body2" fontWeight={600}>
                          {plan.name}
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                          {plan.code} · {plan.description}
                        </Typography>
                      </Box>
                    ),
                    sortValue: (plan) => plan.name ?? '',
                  },
                  {
                    key: 'price',
                    label: 'Price',
                    render: (plan) => (
                      <Box>
                        <Typography variant="body2">
                          ${Number(plan.monthlyPrice ?? 0).toLocaleString()}/mo
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                          ${Number(plan.annualPrice ?? 0).toLocaleString()}/yr
                        </Typography>
                      </Box>
                    ),
                    sortValue: (plan) => Number(plan.monthlyPrice ?? 0),
                  },
                  {
                    key: 'limits',
                    label: 'Limits',
                    render: (plan) => (
                      <Typography variant="caption" color="text.secondary">
                        {plan.maxUsers ?? '∞'} users · {plan.maxDepartments ?? '∞'} depts ·{' '}
                        {plan.maxRequestsMonth ?? '∞'} req/mo · {plan.maxStorageMb ?? '∞'} MB
                      </Typography>
                    ),
                  },
                  { key: 'trial', label: 'Trial', render: (plan) => `${plan.trialDays ?? 0} days` },
                  {
                    key: 'status',
                    label: 'Status',
                    render: (plan) => (
                      <Chip
                        size="small"
                        label={plan.active ? 'Active' : 'Inactive'}
                        color={plan.active ? 'success' : 'default'}
                        variant="outlined"
                      />
                    ),
                  },
                  {
                    key: 'actions',
                    label: 'Actions',
                    align: 'right',
                    render: (plan) => (
                      <Can I="manage" a="Tenant">
                        <Stack direction="row" spacing={0.5} justifyContent="flex-end">
                          <Button
                            component={Link}
                            to={`/platform/plans/${plan.code}/edit`}
                            size="small"
                          >
                            Edit
                          </Button>
                        </Stack>
                      </Can>
                    ),
                  },
                ]}
              />
            </CardContent>
          </Card>
        </>
      ) : (
        <PlatformFeaturesTab />
      )}
    </>
  );
}
