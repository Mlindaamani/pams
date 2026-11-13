import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Grid,
  Stack,
  Typography,
} from '@mui/material';
import BusinessIcon from '@mui/icons-material/Business';
import CreditCardIcon from '@mui/icons-material/CreditCard';
import GroupsIcon from '@mui/icons-material/Groups';
import AccountTreeIcon from '@mui/icons-material/AccountTree';
import DescriptionIcon from '@mui/icons-material/Description';
import ApprovalIcon from '@mui/icons-material/Approval';
import MarkEmailReadIcon from '@mui/icons-material/MarkEmailRead';
import { Can } from '@/auth/AbilityProvider';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { Loading, PageHeader } from '@/components/Page';
import { toast } from '@/components/Toast';
import { problemMessage } from '@/api/client';
import { dateOnly, dateTime } from '@/lib/format';
import { activateTenant, getTenantDetail, retryTenantProvisioning, suspendTenant } from './api';

const usd = (value?: number | null) =>
  new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    maximumFractionDigits: 2,
  }).format(Number(value ?? 0));

export function TenantDetailPage() {
  const { id = '' } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const detail = useQuery({
    queryKey: ['tenant-detail', id],
    queryFn: () => getTenantDetail(id),
    enabled: Boolean(id),
  });
  const [confirmStatus, setConfirmStatus] = useState(false);

  const tenant = detail.data?.tenant;
  const subscription = detail.data?.subscription;
  const provisioning = detail.data?.provisioning;

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: ['tenant-detail', id] });
    void queryClient.invalidateQueries({ queryKey: ['platform-tenants'] });
    void queryClient.invalidateQueries({ queryKey: ['platform-subscriptions'] });
    void queryClient.invalidateQueries({ queryKey: ['platform-billing-metrics'] });
    void queryClient.invalidateQueries({ queryKey: ['platform-dashboard'] });
  };

  const resend = useMutation({
    mutationFn: () => retryTenantProvisioning(id),
    onSuccess: () => {
      toast.success('Invitation resent.');
      refresh();
    },
    onError: (error) => toast.error(problemMessage(error)),
  });
  const changeStatus = useMutation({
    mutationFn: () =>
      tenant?.status === 'SUSPENDED'
        ? activateTenant(id, tenant.version ?? 0)
        : suspendTenant(id, tenant?.version ?? 0),
    onSuccess: () => {
      setConfirmStatus(false);
      refresh();
      toast.success(tenant?.status === 'SUSPENDED' ? 'Tenant activated.' : 'Tenant suspended.');
    },
    onError: (error) => toast.error(problemMessage(error)),
  });

  if (detail.isLoading) return <Loading />;
  if (detail.error || !tenant) {
    return <Alert severity="error">Tenant details could not be loaded.</Alert>;
  }

  return (
    <>
      <PageHeader
        title={tenant.name ?? 'Tenant'}
        backTo="/platform/tenants"
        backLabel="Back to tenants"
        actions={
          <Can I="manage" a="Tenant">
            <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
              <Button component={Link} to={`/platform/tenants/${id}/edit`} variant="outlined">
                Edit
              </Button>
              <Button
                variant="outlined"
                startIcon={<MarkEmailReadIcon />}
                disabled={resend.isPending}
                onClick={() => {
                  if (provisioning?.adminEmail) resend.mutate();
                  else navigate(`/platform/tenants/${id}/invite`);
                }}
              >
                {provisioning?.adminEmail ? 'Resend invitation' : 'Invite admin'}
              </Button>
              <Button
                color={tenant.status === 'SUSPENDED' ? 'success' : 'error'}
                variant="outlined"
                onClick={() => setConfirmStatus(true)}
              >
                {tenant.status === 'SUSPENDED' ? 'Activate' : 'Suspend'}
              </Button>
            </Stack>
          </Can>
        }
      />
      <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 2 }}>
        <Typography variant="body2" color="text.secondary">
          {tenant.slug}
        </Typography>
        <Chip
          size="small"
          label={tenant.status ?? 'ACTIVE'}
          color={tenant.status === 'SUSPENDED' ? 'error' : 'success'}
          variant="outlined"
        />
      </Stack>

      <Grid container spacing={2} sx={{ mb: 2 }}>
        <Grid size={{ xs: 12, md: 8 }}>
          <Card sx={{ height: '100%' }}>
            <CardContent>
              <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 2 }}>
                <BusinessIcon color="primary" />
                <Typography variant="h6">Tenant overview</Typography>
              </Stack>
              <Grid container spacing={2}>
                <Info label="Tenant ID" value={tenant.id} mono />
                <Info label="Created" value={dateTime(tenant.createdAt)} />
                <Info label="Invoice tolerance" value={`${tenant.invoiceTolerancePercent ?? 0}%`} />
                <Info label="Version" value={String(tenant.version ?? '—')} />
              </Grid>
              <Grid container spacing={2} sx={{ mt: 0.5 }}>
                <Usage
                  icon={<GroupsIcon color="primary" />}
                  label="Active users"
                  value={detail.data?.usage?.users ?? 0}
                />
                <Usage
                  icon={<AccountTreeIcon color="secondary" />}
                  label="Departments"
                  value={detail.data?.usage?.departments ?? 0}
                />
                <Usage
                  icon={<DescriptionIcon color="info" />}
                  label="Requests"
                  value={detail.data?.usage?.requests ?? 0}
                />
                <Usage
                  icon={<ApprovalIcon color="warning" />}
                  label="Pending approvals"
                  value={detail.data?.usage?.pendingApprovals ?? 0}
                />
              </Grid>
            </CardContent>
          </Card>
        </Grid>
        <Grid size={{ xs: 12, md: 4 }}>
          <Card sx={{ height: '100%' }}>
            <CardContent>
              <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 2 }}>
                <MarkEmailReadIcon color="secondary" />
                <Typography variant="h6">System admin</Typography>
              </Stack>
              {provisioning ? (
                <Stack spacing={1.5}>
                  <Info label="Name" value={provisioning.adminName || '—'} />
                  <Info label="Email" value={provisioning.adminEmail || '—'} />
                  <Info
                    label="Status"
                    value={
                      <Chip
                        size="small"
                        label={provisioning.status ?? 'PENDING'}
                        color={
                          provisioning.status === 'ACTIVE'
                            ? 'success'
                            : provisioning.status === 'FAILED'
                              ? 'error'
                              : 'warning'
                        }
                        variant="outlined"
                      />
                    }
                  />
                  <Info label="Updated" value={dateTime(provisioning.updatedAt)} />
                  {provisioning.lastError && (
                    <Alert severity="error">{provisioning.lastError}</Alert>
                  )}
                </Stack>
              ) : (
                <Stack spacing={1.5}>
                  <Typography variant="body2" color="text.secondary">
                    No system administrator has been provisioned for this tenant.
                  </Typography>
                  <Can I="manage" a="Tenant">
                    <Button
                      component={Link}
                      to={`/platform/tenants/${id}/invite`}
                      size="small"
                      variant="contained"
                    >
                      Invite system admin
                    </Button>
                  </Can>
                </Stack>
              )}
            </CardContent>
          </Card>
        </Grid>
        <Grid size={{ xs: 12 }}>
          <Card>
            <CardContent>
              <Stack
                direction={{ xs: 'column', sm: 'row' }}
                justifyContent="space-between"
                alignItems={{ xs: 'flex-start', sm: 'center' }}
                spacing={2}
              >
                <Stack direction="row" spacing={1} alignItems="center">
                  <CreditCardIcon color="primary" />
                  <Box>
                    <Typography variant="h6">Subscription</Typography>
                    <Typography variant="body2" color="text.secondary">
                      Platform-managed plan, lifecycle and revenue.
                    </Typography>
                  </Box>
                </Stack>
                <Button
                  component={Link}
                  to={`/platform/revenue/${id}`}
                  variant="outlined"
                  size="small"
                >
                  Manage subscription
                </Button>
              </Stack>
              {subscription ? (
                <Grid container spacing={2} sx={{ mt: 1 }}>
                  <Info label="Plan" value={subscription.planName ?? subscription.planCode} />
                  <Info
                    label="Status"
                    value={
                      <Chip
                        size="small"
                        label={subscription.status ?? '—'}
                        color={
                          subscription.status === 'ACTIVE'
                            ? 'success'
                            : subscription.status === 'TRIALING'
                              ? 'info'
                              : subscription.status === 'PAST_DUE' ||
                                  subscription.status === 'UNPAID'
                                ? 'warning'
                                : 'default'
                        }
                        variant="outlined"
                      />
                    }
                  />
                  <Info label="Interval" value={subscription.billingInterval ?? '—'} />
                  <Info label="MRR" value={usd(subscription.mrr)} />
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
                </Grid>
              ) : (
                <Alert severity="warning" sx={{ mt: 2 }}>
                  No subscription is configured for this tenant.
                </Alert>
              )}
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      <ConfirmDialog
        open={confirmStatus}
        title={tenant.status === 'SUSPENDED' ? 'Activate tenant' : 'Suspend tenant'}
        message={
          tenant.status === 'SUSPENDED'
            ? `Allow ${tenant.name} to access PAMS again?`
            : `Suspend ${tenant.name}? All tenant API requests will be rejected until reactivated.`
        }
        confirmLabel={tenant.status === 'SUSPENDED' ? 'Activate' : 'Suspend'}
        color={tenant.status === 'SUSPENDED' ? 'primary' : 'error'}
        busy={changeStatus.isPending}
        onCancel={() => setConfirmStatus(false)}
        onConfirm={() => changeStatus.mutate()}
      />
    </>
  );
}

function Info({
  label,
  value,
  mono = false,
}: {
  label: string;
  value: React.ReactNode;
  mono?: boolean;
}) {
  return (
    <Grid size={{ xs: 12, sm: 6, md: 3 }}>
      <Typography variant="caption" color="text.secondary" display="block">
        {label}
      </Typography>
      <Typography
        variant="body2"
        component="div"
        sx={{ fontFamily: mono ? 'monospace' : undefined, wordBreak: 'break-all' }}
      >
        {value || '—'}
      </Typography>
    </Grid>
  );
}

function Usage({ icon, label, value }: { icon: React.ReactNode; label: string; value: number }) {
  return (
    <Grid size={{ xs: 6, sm: 3 }}>
      <Card variant="outlined" sx={{ boxShadow: 'none' }}>
        <CardContent>
          <Stack direction="row" spacing={1} alignItems="center">
            {icon}
            <Typography variant="body2" color="text.secondary">
              {label}
            </Typography>
          </Stack>
          <Typography variant="h5" sx={{ mt: 0.5 }}>
            {value}
          </Typography>
        </CardContent>
      </Card>
    </Grid>
  );
}
