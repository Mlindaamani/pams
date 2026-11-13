import { useState } from 'react';
import { Link as RouterLink, useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  FormControlLabel,
  Grid,
  Stack,
  Switch,
  TextField,
  Typography,
} from '@mui/material';
import { problemMessage } from '@/api/client';
import { Loading, PageHeader } from '@/components/Page';
import { toast } from '@/components/Toast';
import type { WebhookCreated, WebhookSubscription } from '@/api/types';
import { createWebhook, listWebhooks, updateWebhook } from './webhooksApi';
import { WebhookEventPicker } from './WebhookEventPicker';
import { WebhookSecretDialog } from './WebhookSecretDialog';
import { WEBHOOK_EVENT_GROUPS, WEBHOOK_EVENTS } from './webhookEvents';

interface WebhookFormState {
  name: string;
  url: string;
  description: string;
  eventTypes: string[];
  signingMethod: string;
  timeoutSeconds: number;
  maxAttempts: number;
  active: boolean;
}

function initialState(subscription?: WebhookSubscription): WebhookFormState {
  return {
    name: subscription?.name ?? '',
    url: subscription?.url ?? '',
    description: subscription?.description ?? '',
    eventTypes: subscription?.eventTypes ?? ['PurchaseOrderIssued'],
    signingMethod: subscription?.signingMethod ?? 'HMAC_SHA256',
    timeoutSeconds: subscription?.timeoutSeconds ?? 10,
    maxAttempts: subscription?.maxAttempts ?? 5,
    active: subscription?.active ?? true,
  };
}

export function WebhookFormPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const webhooks = useQuery({
    queryKey: ['webhooks'],
    queryFn: listWebhooks,
    enabled: Boolean(id),
  });
  const subscription = webhooks.data?.find((candidate) => candidate.id === id);

  if (id && webhooks.isLoading) return <Loading />;
  if (id && (webhooks.error || !subscription)) {
    return <Typography color="error">This webhook endpoint could not be loaded.</Typography>;
  }

  return (
    <WebhookForm
      key={id ?? 'new'}
      subscription={subscription}
      onDone={() => navigate('/admin/webhooks')}
    />
  );
}

function WebhookForm({
  subscription,
  onDone,
}: {
  subscription?: WebhookSubscription;
  onDone: () => void;
}) {
  const queryClient = useQueryClient();
  const [form, setForm] = useState<WebhookFormState>(() => initialState(subscription));
  const [secret, setSecret] = useState<string | null>(null);
  const [created, setCreated] = useState(false);

  const save = useMutation<WebhookCreated | WebhookSubscription>({
    mutationFn: () =>
      subscription
        ? updateWebhook(subscription.id!, subscription.version ?? 0, form)
        : createWebhook(form),
    onSuccess: (result) => {
      void queryClient.invalidateQueries({ queryKey: ['webhooks'] });
      void queryClient.invalidateQueries({ queryKey: ['webhook-delivery-log'] });
      if (subscription) {
        toast.success('Webhook endpoint updated.');
        onDone();
        return;
      }
      setCreated(true);
      if ('signingSecret' in result && result.signingSecret) {
        setSecret(result.signingSecret);
        toast.success('Webhook endpoint created.');
      } else {
        onDone();
      }
    },
    onError: (error) => toast.error(problemMessage(error)),
  });

  const update = <K extends keyof WebhookFormState>(key: K, value: WebhookFormState[K]) =>
    setForm((current) => ({ ...current, [key]: value }));

  const timeoutValid = form.timeoutSeconds >= 1 && form.timeoutSeconds <= 60;
  const attemptsValid = form.maxAttempts >= 1 && form.maxAttempts <= 10;
  const saveDisabled =
    !form.name.trim() ||
    !form.url.trim() ||
    form.eventTypes.length === 0 ||
    !timeoutValid ||
    !attemptsValid ||
    save.isPending;

  return (
    <Box sx={{ width: '100%' }}>
      <PageHeader
        title={subscription ? 'Edit webhook endpoint' : 'Add webhook endpoint'}
        backTo="/admin/webhooks"
        backLabel="Back to webhooks"
      />

      <Box
        sx={{
          display: 'grid',
          gridTemplateColumns: { xs: '1fr', lg: 'minmax(0, 1fr) 340px' },
          gap: 2,
          alignItems: 'start',
        }}
      >
        <Stack spacing={2}>
          <Card>
            <CardContent>
              <Typography variant="h6">Receiver</Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                Name this endpoint for your team, then provide the HTTPS URL that should receive
                event deliveries.
              </Typography>
              <Grid container spacing={2}>
                <Grid size={12}>
                  <TextField
                    fullWidth
                    required
                    label="Endpoint name"
                    value={form.name}
                    onChange={(event) => update('name', event.target.value)}
                    placeholder="Finance ERP"
                    slotProps={{ htmlInput: { maxLength: 200 } }}
                  />
                </Grid>
                <Grid size={12}>
                  <TextField
                    fullWidth
                    required
                    label="Endpoint URL"
                    value={form.url}
                    onChange={(event) => update('url', event.target.value)}
                    placeholder="https://example.com/hooks/pams"
                    slotProps={{ htmlInput: { maxLength: 1000 } }}
                  />
                </Grid>
                <Grid size={12}>
                  <TextField
                    fullWidth
                    label="Description"
                    value={form.description}
                    onChange={(event) => update('description', event.target.value)}
                    placeholder="What this endpoint is used for"
                    slotProps={{ htmlInput: { maxLength: 300 } }}
                  />
                </Grid>
              </Grid>
            </CardContent>
          </Card>

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
                  <Typography variant="h6">Events</Typography>
                  <Typography variant="body2" color="text.secondary">
                    Choose the events this endpoint should receive.
                  </Typography>
                </Box>
                <Chip
                  size="small"
                  variant="outlined"
                  label={`${form.eventTypes.length} of ${WEBHOOK_EVENTS.length} selected`}
                />
              </Stack>
              <WebhookEventPicker
                groups={WEBHOOK_EVENT_GROUPS}
                selected={form.eventTypes}
                onChange={(eventTypes) => update('eventTypes', eventTypes)}
              />
            </CardContent>
          </Card>

          <Stack direction="row" spacing={1} justifyContent="flex-end">
            <Button component={RouterLink} to="/admin/webhooks">
              Cancel
            </Button>
            <Button variant="contained" disabled={saveDisabled} onClick={() => save.mutate()}>
              {subscription ? 'Save changes' : 'Create endpoint'}
            </Button>
          </Stack>
        </Stack>

        <Card
          sx={{
            position: { lg: 'sticky' },
            top: { lg: 88 },
            alignSelf: 'start',
          }}
        >
          <CardContent>
            <Stack
              direction="row"
              justifyContent="space-between"
              alignItems="flex-start"
              spacing={2}
              sx={{ mb: 1 }}
            >
              <Box>
                <Typography variant="h6">Delivery settings</Typography>
              </Box>
              <FormControlLabel
                sx={{ m: 0 }}
                control={
                  <Switch
                    checked={form.active}
                    onChange={(event) => update('active', event.target.checked)}
                  />
                }
                label={form.active ? 'Active' : 'Paused'}
              />
            </Stack>
            <Stack spacing={2}>
              <TextField
                select
                fullWidth
                label="Signing method"
                value={form.signingMethod}
                onChange={(event) => update('signingMethod', event.target.value)}
                helperText="HMAC-SHA256 signs every delivery payload."
                slotProps={{ select: { native: true } }}
              >
                <option value="HMAC_SHA256">HMAC-SHA256</option>
              </TextField>
              <TextField
                fullWidth
                type="number"
                label="Timeout"
                value={form.timeoutSeconds}
                onChange={(event) => update('timeoutSeconds', Number(event.target.value))}
                helperText="Seconds, between 1 and 60."
                error={!timeoutValid}
                slotProps={{ htmlInput: { min: 1, max: 60 } }}
              />
              <TextField
                fullWidth
                type="number"
                label="Max attempts"
                value={form.maxAttempts}
                onChange={(event) => update('maxAttempts', Number(event.target.value))}
                helperText="Attempts, between 1 and 10."
                error={!attemptsValid}
                slotProps={{ htmlInput: { min: 1, max: 10 } }}
              />
            </Stack>
          </CardContent>
        </Card>
      </Box>

      <WebhookSecretDialog
        secret={secret}
        onClose={() => {
          setSecret(null);
          if (created) onDone();
        }}
      />
    </Box>
  );
}
