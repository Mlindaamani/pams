import { Link as RouterLink, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Box, Button, Card, CardContent, Chip, Grid, Stack, Typography } from '@mui/material';
import { Can } from '@/auth/AbilityProvider';
import { Loading, PageHeader } from '@/components/Page';
import { dateTime } from '@/lib/format';
import { CopyableUrl } from './WebhookCopyableUrl';
import { listWebhooks } from './webhooksApi';
import { WEBHOOK_EVENT_GROUPS } from './webhookEvents';

export function WebhookDetailPage() {
  const { id } = useParams();
  const webhooks = useQuery({ queryKey: ['webhooks'], queryFn: listWebhooks });
  const endpoint = webhooks.data?.find((candidate) => candidate.id === id);

  if (webhooks.isLoading) return <Loading />;
  if (webhooks.error || !endpoint) {
    return <Typography color="error">This webhook endpoint could not be loaded.</Typography>;
  }

  const selectedGroups = WEBHOOK_EVENT_GROUPS.map((group) => ({
    ...group,
    selected: group.events.filter((event) => endpoint.eventTypes?.includes(event.value)),
  })).filter((group) => group.selected.length > 0);

  return (
    <>
      <PageHeader
        title={endpoint.name ?? 'Webhook endpoint'}
        backTo="/admin/webhooks"
        actions={
          <Can I="manage" a="Webhook">
            <Button
              component={RouterLink}
              to={`/admin/webhooks/${endpoint.id}/edit`}
              variant="contained"
            >
              Edit endpoint
            </Button>
          </Can>
        }
      />

      <Grid container spacing={2}>
        <Grid size={{ xs: 12, md: 8 }}>
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
                  <Typography variant="h6">Receiver</Typography>
                  <Typography variant="body2" color="text.secondary">
                    Where PAMS sends this endpoint's signed events.
                  </Typography>
                </Box>
                <Chip
                  size="small"
                  label={endpoint.active ? 'Active' : 'Paused'}
                  color={endpoint.active ? 'success' : 'default'}
                  variant="outlined"
                />
              </Stack>
              <Grid container spacing={2}>
                <Grid size={12}>
                  <Detail label="Endpoint name" value={endpoint.name ?? '—'} />
                </Grid>
                <Grid size={12}>
                  <Typography variant="caption" color="text.secondary" display="block">
                    Endpoint URL
                  </Typography>
                  <CopyableUrl value={endpoint.url ?? 'Endpoint URL unavailable'} />
                </Grid>
                <Grid size={12}>
                  <Detail label="Description" value={endpoint.description ?? '—'} />
                </Grid>
              </Grid>
            </CardContent>
          </Card>

          <Card sx={{ mt: 2 }}>
            <CardContent>
              <Typography variant="h6">Subscribed events</Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                This endpoint receives the following {endpoint.eventTypes?.length ?? 0} events.
              </Typography>
              <Stack spacing={2}>
                {selectedGroups.map((group) => (
                  <Box key={group.id}>
                    <Typography variant="subtitle2">{group.label}</Typography>
                    <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap" sx={{ mt: 1 }}>
                      {group.selected.map((event) => (
                        <Chip key={event.value} size="small" label={event.label} />
                      ))}
                    </Stack>
                  </Box>
                ))}
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid size={{ xs: 12, md: 4 }}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Delivery settings
              </Typography>
              <Stack spacing={1.5}>
                <Detail label="Signing method" value={endpoint.signingMethod ?? 'HMAC_SHA256'} />
                <Detail label="Timeout" value={`${endpoint.timeoutSeconds ?? 10} seconds`} />
                <Detail label="Max attempts" value={String(endpoint.maxAttempts ?? 5)} />
              </Stack>
            </CardContent>
          </Card>

          <Card sx={{ mt: 2 }}>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Health
              </Typography>
              <Stack spacing={1.5}>
                <Detail label="Deliveries" value={String(endpoint.deliveryCount ?? 0)} />
                <Detail label="Last success" value={dateTime(endpoint.lastSuccessAt)} />
                <Detail label="Last failure" value={dateTime(endpoint.lastFailureAt)} />
                <Detail label="Created" value={dateTime(endpoint.createdAt)} />
                <Detail label="Updated" value={dateTime(endpoint.updatedAt)} />
              </Stack>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </>
  );
}

function Detail({ label, value }: { label: string; value: string }) {
  return (
    <Box>
      <Typography variant="caption" color="text.secondary" display="block">
        {label}
      </Typography>
      <Typography variant="body2">{value}</Typography>
    </Box>
  );
}
