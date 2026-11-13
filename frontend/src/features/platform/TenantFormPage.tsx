import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  Button,
  Card,
  CardContent,
  Grid,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import { Loading, PageHeader } from '@/components/Page';
import { toast } from '@/components/Toast';
import { problemMessage } from '@/api/client';
import { listSubscriptionPlans } from '@/features/subscription/api';
import { createTenant, getTenantDetail, updateTenant } from './api';

export function TenantFormPage() {
  const { id } = useParams();
  const editing = Boolean(id);
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const detail = useQuery({
    queryKey: ['tenant-detail', id],
    queryFn: () => getTenantDetail(id!),
    enabled: editing,
  });
  const plans = useQuery({
    queryKey: ['subscription-plans'],
    queryFn: listSubscriptionPlans,
    enabled: !editing,
  });
  const [name, setName] = useState('');
  const [slug, setSlug] = useState('');
  const [tolerance, setTolerance] = useState('2');
  const [planCode, setPlanCode] = useState('GROWTH');
  const [adminName, setAdminName] = useState('');
  const [adminEmail, setAdminEmail] = useState('');

  useEffect(() => {
    const tenant = detail.data?.tenant;
    if (!tenant) return;
    setName(tenant.name ?? '');
    setSlug(tenant.slug ?? '');
    setTolerance(String(tenant.invoiceTolerancePercent ?? 2));
  }, [detail.data?.tenant]);

  const save = useMutation({
    mutationFn: () =>
      editing
        ? updateTenant(id!, detail.data?.tenant?.version ?? 0, {
            name,
            slug,
            invoiceTolerancePercent: Number(tolerance),
          })
        : createTenant({
            name,
            slug,
            invoiceTolerancePercent: Number(tolerance),
            planCode,
            adminEmail,
            adminName,
          }),
    onSuccess: (tenant) => {
      void queryClient.invalidateQueries({ queryKey: ['platform-tenants'] });
      void queryClient.invalidateQueries({ queryKey: ['platform-subscriptions'] });
      void queryClient.invalidateQueries({ queryKey: ['platform-billing-metrics'] });
      if (editing) {
        void queryClient.invalidateQueries({ queryKey: ['tenant-detail', id] });
        toast.success('Tenant updated.');
        navigate(`/platform/tenants/${id}`);
      } else {
        toast.success('Tenant created. Invitation is being provisioned.');
        navigate(`/platform/tenants/${tenant.id}`);
      }
    },
    onError: (error) => toast.error(problemMessage(error)),
  });

  if (editing && detail.isLoading) return <Loading />;
  if (editing && (detail.error || !detail.data?.tenant)) {
    return <Alert severity="error">Tenant could not be loaded.</Alert>;
  }

  return (
    <>
      <PageHeader
        title={editing ? `Edit ${detail.data?.tenant?.name ?? 'tenant'}` : 'Add tenant'}
        backTo={editing ? `/platform/tenants/${id}` : '/platform/tenants'}
        backLabel={editing ? 'Back to tenant' : 'Back to tenants'}
      />
      <Card>
        <CardContent>
          <Stack spacing={2}>
            {!editing && (
              <Alert severity="info">
                The tenant starts on a trial subscription and the system admin receives a temporary
                password by email.
              </Alert>
            )}
            <Typography variant="h6">Tenant details</Typography>
            <Grid container spacing={2}>
              <Grid size={{ xs: 12, sm: 6 }}>
                <TextField
                  label="Name"
                  fullWidth
                  value={name}
                  onChange={(event) => setName(event.target.value)}
                  required
                />
              </Grid>
              <Grid size={{ xs: 12, sm: 6 }}>
                <TextField
                  label="Slug"
                  fullWidth
                  value={slug}
                  onChange={(event) => setSlug(event.target.value)}
                  helperText="Lowercase letters, numbers and hyphens."
                  required
                />
              </Grid>
              <Grid size={{ xs: 12, sm: 6 }}>
                <TextField
                  label="Invoice price tolerance (%)"
                  type="number"
                  fullWidth
                  value={tolerance}
                  onChange={(event) => setTolerance(event.target.value)}
                  slotProps={{ htmlInput: { min: 0, max: 100, step: 0.01 } }}
                />
              </Grid>
            </Grid>

            {!editing && (
              <>
                <Typography variant="h6" sx={{ pt: 1 }}>
                  Initial system administrator
                </Typography>
                <Grid container spacing={2}>
                  <Grid size={{ xs: 12, sm: 6 }}>
                    <TextField
                      label="Admin name"
                      fullWidth
                      value={adminName}
                      onChange={(event) => setAdminName(event.target.value)}
                      required
                    />
                  </Grid>
                  <Grid size={{ xs: 12, sm: 6 }}>
                    <TextField
                      label="Admin email"
                      type="email"
                      fullWidth
                      value={adminEmail}
                      onChange={(event) => setAdminEmail(event.target.value)}
                      required
                    />
                  </Grid>
                  <Grid size={{ xs: 12, sm: 6 }}>
                    <TextField
                      select
                      label="Subscription plan"
                      fullWidth
                      value={planCode}
                      onChange={(event) => setPlanCode(event.target.value)}
                    >
                      {(plans.data ?? []).map((plan) => (
                        <MenuItem key={plan.code} value={plan.code}>
                          {plan.name} — ${Number(plan.monthlyPrice).toLocaleString()}/month
                        </MenuItem>
                      ))}
                    </TextField>
                  </Grid>
                </Grid>
              </>
            )}

            <Stack direction="row" spacing={1} sx={{ pt: 1 }}>
              <Button
                variant="contained"
                disabled={
                  !name || !slug || (!editing && (!adminName || !adminEmail)) || save.isPending
                }
                onClick={() => save.mutate()}
              >
                {editing ? 'Save changes' : 'Create tenant'}
              </Button>
              <Button
                component={Link}
                to={editing ? `/platform/tenants/${id}` : '/platform/tenants'}
              >
                Cancel
              </Button>
            </Stack>
          </Stack>
        </CardContent>
      </Card>
    </>
  );
}
