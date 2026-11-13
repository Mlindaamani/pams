import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, Button, Card, CardContent, Stack, TextField, Typography } from '@mui/material';
import { Loading, PageHeader } from '@/components/Page';
import { toast } from '@/components/Toast';
import { problemMessage } from '@/api/client';
import { getTenantDetail, inviteTenantAdministrator, retryTenantProvisioning } from './api';

export function TenantInvitePage() {
  const { id = '' } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const detail = useQuery({ queryKey: ['tenant-detail', id], queryFn: () => getTenantDetail(id) });
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');

  const submit = useMutation({
    mutationFn: () => inviteTenantAdministrator(id, { email, name }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['tenant-detail', id] });
      void queryClient.invalidateQueries({ queryKey: ['platform-subscriptions'] });
      toast.success('System admin invitation queued.');
      navigate(`/platform/tenants/${id}`);
    },
    onError: (error) => toast.error(problemMessage(error)),
  });
  const resend = useMutation({
    mutationFn: () => retryTenantProvisioning(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['tenant-detail', id] });
      toast.success('Invitation resent.');
      navigate(`/platform/tenants/${id}`);
    },
    onError: (error) => toast.error(problemMessage(error)),
  });

  if (detail.isLoading) return <Loading />;
  if (detail.error || !detail.data?.tenant) {
    return <Alert severity="error">Tenant could not be loaded.</Alert>;
  }
  const tenant = detail.data.tenant;
  const provisioning = detail.data.provisioning;

  return (
    <>
      <PageHeader
        title="System administrator invitation"
        backTo={`/platform/tenants/${id}`}
        backLabel="Back to tenant"
      />
      <Card>
        <CardContent>
          <Stack spacing={2}>
            <Typography variant="body2" color="text.secondary">
              {tenant.name} · {tenant.slug}
            </Typography>
            {provisioning?.adminEmail ? (
              <>
                <Alert severity="info">
                  An administrator is already configured for this tenant. Resending generates a new
                  temporary password and emails it to the existing administrator.
                </Alert>
                <Stack spacing={0.5}>
                  <Typography variant="body2">
                    <strong>{provisioning.adminName}</strong>
                  </Typography>
                  <Typography variant="body2">{provisioning.adminEmail}</Typography>
                </Stack>
                <Button
                  variant="contained"
                  disabled={resend.isPending}
                  onClick={() => resend.mutate()}
                  sx={{ alignSelf: 'flex-start' }}
                >
                  Resend invitation
                </Button>
              </>
            ) : (
              <>
                <TextField
                  label="Admin name"
                  value={name}
                  onChange={(event) => setName(event.target.value)}
                  required
                />
                <TextField
                  label="Admin email"
                  type="email"
                  value={email}
                  onChange={(event) => setEmail(event.target.value)}
                  required
                />
                <Alert severity="info">
                  A Keycloak ADMIN account is created for this tenant and a temporary password is
                  sent by email.
                </Alert>
                <Stack direction="row" spacing={1}>
                  <Button
                    variant="contained"
                    disabled={!name || !email || submit.isPending}
                    onClick={() => submit.mutate()}
                  >
                    Send invitation
                  </Button>
                  <Button component={Link} to={`/platform/tenants/${id}`}>
                    Cancel
                  </Button>
                </Stack>
              </>
            )}
          </Stack>
        </CardContent>
      </Card>
    </>
  );
}
