import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, Button, Card, CardContent, Stack, TextField, Typography } from '@mui/material';
import { Loading, PageHeader } from '@/components/Page';
import { toast } from '@/components/Toast';
import { useAbility } from '@/auth/AbilityProvider';
import { currentOrganization, updateOrganization } from './organizationsApi';

export function OrganizationPage() {
  const queryClient = useQueryClient();
  const ability = useAbility();
  const organization = useQuery({
    queryKey: ['organization', 'current'],
    queryFn: currentOrganization,
  });
  const [name, setName] = useState('');
  const [tolerance, setTolerance] = useState('2');
  useEffect(() => {
    if (organization.data?.name) setName(organization.data.name);
    if (organization.data?.invoiceTolerancePercent !== undefined) {
      setTolerance(String(organization.data.invoiceTolerancePercent));
    }
  }, [organization.data]);

  const rename = useMutation({
    mutationFn: () =>
      updateOrganization(organization.data!.id!, {
        name,
        invoiceTolerancePercent: Number(tolerance),
      }),
    onSuccess: () => {
      toast.success('Organization updated.');
      void queryClient.invalidateQueries({ queryKey: ['organization'] });
    },
  });

  if (organization.isLoading) return <Loading />;
  if (organization.error || !organization.data) {
    return <Alert severity="error">Organization could not be loaded.</Alert>;
  }

  return (
    <>
      <PageHeader title="Organization" />
      <Card>
        <CardContent>
          <Stack spacing={2} sx={{ maxWidth: 520 }}>
            <Typography color="text.secondary">Tenant ID: {organization.data.id}</Typography>
            <Typography color="text.secondary">Slug: {organization.data.slug}</Typography>
            <TextField
              label="Name"
              value={name}
              onChange={(event) => setName(event.target.value)}
              disabled={!ability.can('update', 'Organization')}
            />
            <TextField
              label="Invoice price tolerance (%)"
              type="number"
              value={tolerance}
              onChange={(event) => setTolerance(event.target.value)}
              disabled={!ability.can('update', 'Organization')}
              helperText="Invoice lines whose unit-price variance exceeds this are flagged for Finance."
              slotProps={{ htmlInput: { min: 0, max: 100, step: 0.01 } }}
            />
            {ability.can('update', 'Organization') && (
              <Button
                variant="contained"
                disabled={
                  !name ||
                  !tolerance ||
                  (saveDisabled(name, organization.data.name) &&
                    Number(tolerance) === organization.data.invoiceTolerancePercent) ||
                  rename.isPending
                }
                onClick={() => rename.mutate()}
                sx={{ alignSelf: 'flex-start' }}
              >
                Save changes
              </Button>
            )}
          </Stack>
        </CardContent>
      </Card>
    </>
  );
}

function saveDisabled(next: string, current?: string) {
  return next.trim() === (current ?? '').trim();
}
