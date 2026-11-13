import { useState } from 'react';
import { Link as RouterLink, useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Divider,
  Drawer,
  IconButton,
  Menu,
  MenuItem,
  Stack,
  Tab,
  Tabs,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import DeleteIcon from '@mui/icons-material/Delete';
import EditIcon from '@mui/icons-material/Edit';
import KeyIcon from '@mui/icons-material/Key';
import ListAltIcon from '@mui/icons-material/ListAlt';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import PowerSettingsNewIcon from '@mui/icons-material/PowerSettingsNew';
import ReplayIcon from '@mui/icons-material/Replay';
import SendIcon from '@mui/icons-material/Send';
import { Can } from '@/auth/AbilityProvider';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { DataTable } from '@/components/DataTable';
import { Loading, PageHeader } from '@/components/Page';
import { toast } from '@/components/Toast';
import { problemMessage } from '@/api/client';
import { dateTime } from '@/lib/format';
import type { WebhookDelivery, WebhookSubscription } from '@/api/types';
import {
  deleteWebhook,
  listDeliveryLog,
  listWebhooks,
  retryWebhookDelivery,
  rotateWebhookSecret,
  sendWebhookTestEvent,
  updateWebhook,
} from './webhooksApi';
import { WebhookSecretDialog } from './WebhookSecretDialog';
import { CopyableUrl } from './WebhookCopyableUrl';
import { WEBHOOK_DELIVERY_EVENTS, webhookEventLabel } from './webhookEvents';

const DELIVERY_STATUSES = [
  { value: 'PENDING', label: 'Pending' },
  { value: 'SUCCEEDED', label: 'Succeeded' },
  { value: 'DEAD_LETTER', label: 'Dead letter' },
];

export function WebhooksPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const webhooks = useQuery({ queryKey: ['webhooks'], queryFn: listWebhooks });
  const [tab, setTab] = useState<'endpoints' | 'logs'>('endpoints');
  const [secret, setSecret] = useState<string | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<WebhookSubscription | null>(null);
  const [menu, setMenu] = useState<{
    anchorEl: HTMLElement;
    subscription: WebhookSubscription;
  } | null>(null);
  const [selectedDelivery, setSelectedDelivery] = useState<WebhookDelivery | null>(null);
  const [logFilter, setLogFilter] = useState({
    endpointId: '',
    status: '',
    eventType: '',
  });

  const deliveries = useQuery({
    queryKey: ['webhook-delivery-log', logFilter],
    queryFn: () =>
      listDeliveryLog({
        endpointId: logFilter.endpointId || undefined,
        status: logFilter.status || undefined,
        eventType: logFilter.eventType || undefined,
        size: 100,
      }),
    enabled: tab === 'logs',
  });

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ['webhooks'] });
    void queryClient.invalidateQueries({ queryKey: ['webhook-delivery-log'] });
  };

  const remove = useMutation({
    mutationFn: () => deleteWebhook(deleteTarget!.id!, deleteTarget!.version ?? 0),
    onSuccess: () => {
      setDeleteTarget(null);
      invalidate();
      toast.success('Webhook endpoint deleted.');
    },
    onError: (error) => toast.error(problemMessage(error)),
  });
  const rotate = useMutation({
    mutationFn: (subscription: WebhookSubscription) =>
      rotateWebhookSecret(subscription.id!, subscription.version ?? 0),
    onSuccess: (result) => {
      invalidate();
      if (result.signingSecret) setSecret(result.signingSecret);
      toast.success('Signing secret rotated.');
    },
    onError: (error) => toast.error(problemMessage(error)),
  });
  const toggleActive = useMutation({
    mutationFn: (subscription: WebhookSubscription) =>
      updateWebhook(subscription.id!, subscription.version ?? 0, {
        name: subscription.name ?? subscription.url ?? 'Webhook endpoint',
        url: subscription.url ?? '',
        description: subscription.description,
        eventTypes: subscription.eventTypes ?? [],
        signingMethod: subscription.signingMethod ?? 'HMAC_SHA256',
        timeoutSeconds: subscription.timeoutSeconds ?? 10,
        maxAttempts: subscription.maxAttempts ?? 5,
        active: !subscription.active,
      }),
    onSuccess: (_result, subscription) => {
      invalidate();
      toast.success(subscription.active ? 'Endpoint turned off.' : 'Endpoint turned on.');
    },
    onError: (error) => toast.error(problemMessage(error)),
  });
  const sendTest = useMutation({
    mutationFn: (subscription: WebhookSubscription) => sendWebhookTestEvent(subscription.id!),
    onSuccess: (_delivery, subscription) => {
      invalidate();
      setTab('logs');
      setLogFilter({ endpointId: subscription.id ?? '', status: '', eventType: 'WebhookTest' });
      toast.success('Test event queued for delivery.');
    },
    onError: (error) => toast.error(problemMessage(error)),
  });
  const retry = useMutation({
    mutationFn: retryWebhookDelivery,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['webhook-delivery-log'] });
      toast.success('Delivery queued for retry.');
    },
    onError: (error) => toast.error(problemMessage(error)),
  });

  if (webhooks.isLoading) return <Loading />;

  return (
    <>
      <PageHeader
        title="Webhooks"
        actions={
          <Can I="manage" a="Webhook">
            <Button variant="contained" onClick={() => navigate('/admin/webhooks/new')}>
              Add endpoint
            </Button>
          </Can>
        }
      />
      <Tabs value={tab} onChange={(_, value: 'endpoints' | 'logs') => setTab(value)} sx={{ mb: 2 }}>
        <Tab value="endpoints" label="Endpoints" />
        <Tab value="logs" label="Delivery Logs" />
      </Tabs>

      {tab === 'endpoints' ? (
        <DataTable
          ariaLabel="Webhook endpoints"
          rows={webhooks.data ?? []}
          getRowId={(subscription) => subscription.id ?? ''}
          emptyMessage="No webhook endpoints configured."
          onRowClick={(subscription) => navigate(`/admin/webhooks/${subscription.id}`)}
          columns={[
            {
              key: 'url',
              label: 'Endpoint',
              render: (row) => (
                <Typography
                  component={RouterLink}
                  to={`/admin/webhooks/${row.id}`}
                  variant="body2"
                  fontWeight={600}
                  noWrap
                  sx={{
                    maxWidth: 420,
                    color: 'primary.main',
                    textDecoration: 'none',
                    '&:hover': { textDecoration: 'underline' },
                  }}
                >
                  {row.name ?? row.url}
                </Typography>
              ),
            },
            {
              key: 'status',
              label: 'Status',
              render: (row) => (
                <Chip
                  size="small"
                  label={row.active ? 'Active' : 'Paused'}
                  color={row.active ? 'success' : 'default'}
                  variant="outlined"
                />
              ),
            },
            {
              key: 'events',
              label: 'Events',
              render: (row) => row.eventTypes?.length ?? 0,
              sortValue: (row) => row.eventTypes?.length ?? 0,
            },
            {
              key: 'deliveries',
              label: 'Deliveries',
              render: (row) => row.deliveryCount ?? 0,
              sortValue: (row) => row.deliveryCount ?? 0,
            },
            {
              key: 'success',
              label: 'Last success',
              render: (row) => dateTime(row.lastSuccessAt),
            },
            {
              key: 'actions',
              label: 'Actions',
              align: 'right',
              width: 90,
              render: (row) => (
                <Can I="manage" a="Webhook">
                  <Tooltip title="Endpoint actions">
                    <IconButton
                      size="small"
                      aria-label="Endpoint actions"
                      onClick={(event) => {
                        event.stopPropagation();
                        setMenu({ anchorEl: event.currentTarget, subscription: row });
                      }}
                    >
                      <MoreVertIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                </Can>
              ),
            },
          ]}
        />
      ) : (
        <>
          <Card sx={{ mb: 2 }}>
            <CardContent>
              <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
                <TextField
                  select
                  size="small"
                  label="Endpoint"
                  value={logFilter.endpointId}
                  onChange={(event) =>
                    setLogFilter((current) => ({ ...current, endpointId: event.target.value }))
                  }
                  sx={{ minWidth: 280 }}
                >
                  <MenuItem value="">All endpoints</MenuItem>
                  {(webhooks.data ?? []).map((subscription) => (
                    <MenuItem key={subscription.id} value={subscription.id}>
                      {subscription.name ?? subscription.url}
                    </MenuItem>
                  ))}
                </TextField>
                <TextField
                  select
                  size="small"
                  label="Status"
                  value={logFilter.status}
                  onChange={(event) =>
                    setLogFilter((current) => ({ ...current, status: event.target.value }))
                  }
                  sx={{ minWidth: 180 }}
                >
                  <MenuItem value="">All statuses</MenuItem>
                  {DELIVERY_STATUSES.map((status) => (
                    <MenuItem key={status.value} value={status.value}>
                      {status.label}
                    </MenuItem>
                  ))}
                </TextField>
                <TextField
                  select
                  size="small"
                  label="Event"
                  value={logFilter.eventType}
                  onChange={(event) =>
                    setLogFilter((current) => ({ ...current, eventType: event.target.value }))
                  }
                  sx={{ minWidth: 240 }}
                >
                  <MenuItem value="">All events</MenuItem>
                  {WEBHOOK_DELIVERY_EVENTS.map((event) => (
                    <MenuItem key={event.value} value={event.value}>
                      {event.label}
                    </MenuItem>
                  ))}
                </TextField>
              </Stack>
            </CardContent>
          </Card>

          <DataTable
            ariaLabel="Webhook delivery logs"
            rows={deliveries.data?.content ?? []}
            getRowId={(delivery) => delivery.id ?? ''}
            emptyMessage="No deliveries match these filters."
            onRowClick={setSelectedDelivery}
            columns={[
              {
                key: 'event',
                label: 'Event',
                render: (delivery) => webhookEventLabel(delivery.eventType),
              },
              {
                key: 'endpoint',
                label: 'Endpoint',
                render: (delivery) => (
                  <Typography variant="body2" fontWeight={600} noWrap sx={{ maxWidth: 320 }}>
                    {delivery.subscriptionName ?? delivery.subscriptionUrl ?? 'Endpoint'}
                  </Typography>
                ),
              },
              {
                key: 'status',
                label: 'Status',
                render: (delivery) => (
                  <Chip
                    size="small"
                    label={statusLabel(delivery.status)}
                    color={deliveryColor(delivery.status)}
                    variant="outlined"
                  />
                ),
              },
              { key: 'attempts', label: 'Attempts', render: (delivery) => delivery.attempts },
              {
                key: 'response',
                label: 'Response',
                render: (delivery) => responseLabel(delivery),
              },
              {
                key: 'lastAttempt',
                label: 'Last attempt',
                render: (delivery) => dateTime(delivery.lastAttemptAt),
              },
              {
                key: 'actions',
                label: 'Actions',
                align: 'right',
                width: 80,
                render: (delivery) =>
                  delivery.status !== 'SUCCEEDED' ? (
                    <Tooltip title="Retry delivery">
                      <IconButton
                        size="small"
                        aria-label="Retry delivery"
                        disabled={retry.isPending}
                        onClick={(event) => {
                          event.stopPropagation();
                          retry.mutate(delivery.id!);
                        }}
                      >
                        <ReplayIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                  ) : (
                    '—'
                  ),
              },
            ]}
          />
        </>
      )}

      <DeliveryDetailDrawer
        delivery={selectedDelivery}
        onClose={() => setSelectedDelivery(null)}
        onRetry={() => {
          if (selectedDelivery?.id) {
            retry.mutate(selectedDelivery.id);
            setSelectedDelivery(null);
          }
        }}
        retryPending={retry.isPending}
      />

      <Menu
        anchorEl={menu?.anchorEl}
        open={Boolean(menu)}
        onClose={() => setMenu(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
        transformOrigin={{ vertical: 'top', horizontal: 'right' }}
      >
        <MenuItem
          onClick={() => {
            if (menu) navigate(`/admin/webhooks/${menu.subscription.id}/edit`);
            setMenu(null);
          }}
        >
          <EditIcon fontSize="small" sx={{ mr: 1 }} />
          Edit
        </MenuItem>
        <MenuItem
          disabled={!menu?.subscription.active || sendTest.isPending}
          onClick={() => {
            if (menu) sendTest.mutate(menu.subscription);
            setMenu(null);
          }}
        >
          <SendIcon fontSize="small" sx={{ mr: 1 }} />
          Send test event
        </MenuItem>
        <MenuItem
          disabled={rotate.isPending}
          onClick={() => {
            if (menu) rotate.mutate(menu.subscription);
            setMenu(null);
          }}
        >
          <KeyIcon fontSize="small" sx={{ mr: 1 }} />
          Rotate secret
        </MenuItem>
        <MenuItem
          onClick={() => {
            if (menu) toggleActive.mutate(menu.subscription);
            setMenu(null);
          }}
        >
          <PowerSettingsNewIcon fontSize="small" sx={{ mr: 1 }} />
          {menu?.subscription.active ? 'Turn off' : 'Turn on'}
        </MenuItem>
        <MenuItem
          onClick={() => {
            if (menu) {
              setTab('logs');
              setLogFilter({ endpointId: menu.subscription.id ?? '', status: '', eventType: '' });
            }
            setMenu(null);
          }}
        >
          <ListAltIcon fontSize="small" sx={{ mr: 1 }} />
          View delivery logs
        </MenuItem>
        <Divider />
        <MenuItem
          sx={{ color: 'error.main' }}
          onClick={() => {
            if (menu) setDeleteTarget(menu.subscription);
            setMenu(null);
          }}
        >
          <DeleteIcon fontSize="small" sx={{ mr: 1 }} />
          Delete endpoint
        </MenuItem>
      </Menu>

      <WebhookSecretDialog secret={secret} onClose={() => setSecret(null)} />

      <ConfirmDialog
        open={Boolean(deleteTarget)}
        title="Delete webhook endpoint"
        message={`Delete ${deleteTarget?.name ?? deleteTarget?.url ?? 'this endpoint'} and its delivery history?`}
        confirmLabel="Delete endpoint"
        busy={remove.isPending}
        onCancel={() => setDeleteTarget(null)}
        onConfirm={() => remove.mutate()}
      />
    </>
  );
}

function DeliveryDetailDrawer({
  delivery,
  onClose,
  onRetry,
  retryPending,
}: {
  delivery: WebhookDelivery | null;
  onClose: () => void;
  onRetry: () => void;
  retryPending: boolean;
}) {
  return (
    <Drawer
      anchor="right"
      open={Boolean(delivery)}
      onClose={onClose}
      sx={{ zIndex: (theme) => theme.zIndex.appBar + 10 }}
      slotProps={{
        paper: {
          sx: {
            width: { xs: '100%', sm: 560 },
            px: 3,
            pb: 3,
            pt: 9,
          },
        },
      }}
    >
      {delivery && (
        <Stack spacing={2} sx={{ height: '100%', overflowY: 'auto' }}>
          <Stack direction="row" justifyContent="space-between" alignItems="flex-start" spacing={2}>
            <Box sx={{ minWidth: 0 }}>
              <Typography variant="h6">{webhookEventLabel(delivery.eventType)}</Typography>
              <Typography variant="body2" color="text.secondary">
                {delivery.subscriptionName ?? 'To'}
              </Typography>
              <CopyableUrl value={delivery.subscriptionUrl ?? 'Endpoint unavailable'} />
            </Box>
            <Chip
              size="small"
              label={statusLabel(delivery.status)}
              color={deliveryColor(delivery.status)}
              variant="outlined"
            />
          </Stack>

          <Box>
            <Typography variant="h5">
              {delivery.status === 'SUCCEEDED'
                ? 'Delivered'
                : delivery.status === 'DEAD_LETTER'
                  ? 'Delivery failed'
                  : 'Pending delivery'}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              {delivery.status === 'SUCCEEDED'
                ? 'Receiver answered 2xx.'
                : (delivery.errorMessage ?? 'Waiting for the next delivery attempt.')}
            </Typography>
            {delivery.status !== 'SUCCEEDED' && (
              <Button
                size="small"
                variant="outlined"
                startIcon={<ReplayIcon />}
                sx={{ mt: 1 }}
                disabled={retryPending}
                onClick={onRetry}
              >
                Redeliver
              </Button>
            )}
          </Box>

          <Divider />

          <Box
            sx={{
              display: 'grid',
              gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' },
              gap: 1.5,
            }}
          >
            <Detail label="Attempts" value={String(delivery.attempts ?? 0)} />
            <Detail label="HTTP status" value={delivery.responseStatus ?? '—'} />
            <Detail
              label="Duration"
              value={delivery.durationMs == null ? '—' : `${delivery.durationMs} ms`}
            />
            <Detail label="Occurred" value={dateTime(delivery.createdAt)} />
            <Detail label="Last attempt" value={dateTime(delivery.lastAttemptAt)} />
            <Detail label="Next attempt" value={dateTime(delivery.nextAttemptAt)} />
            <Box sx={{ gridColumn: { sm: '1 / -1' } }}>
              <Detail label="Event ID" value={delivery.eventId ?? '—'} mono />
            </Box>
          </Box>

          <Divider />
          <Box>
            <Typography variant="subtitle2" sx={{ mb: 1 }}>
              Payload sent
            </Typography>
            <CodeBlock value={prettyJson(delivery.payload)} />
          </Box>

          <Box>
            <Typography variant="subtitle2" sx={{ mb: 1 }}>
              Receiver response
            </Typography>
            <CodeBlock value={delivery.responseBody?.trim() || delivery.errorMessage || '—'} />
          </Box>

          <Button variant="outlined" onClick={onClose} sx={{ alignSelf: 'flex-end' }}>
            Close
          </Button>
        </Stack>
      )}
    </Drawer>
  );
}

function Detail({
  label,
  value,
  mono = false,
}: {
  label: string;
  value: React.ReactNode;
  mono?: boolean;
}) {
  return (
    <Box>
      <Typography variant="caption" color="text.secondary" display="block">
        {label}
      </Typography>
      <Typography variant="body2" sx={{ fontFamily: mono ? 'monospace' : undefined }}>
        {value}
      </Typography>
    </Box>
  );
}

function CodeBlock({ value }: { value: string }) {
  return (
    <Typography
      component="pre"
      variant="body2"
      sx={{
        m: 0,
        p: 2,
        maxHeight: 280,
        overflow: 'auto',
        whiteSpace: 'pre-wrap',
        wordBreak: 'break-word',
        bgcolor: 'grey.50',
        border: 1,
        borderColor: 'divider',
        borderRadius: 1,
        fontFamily: 'monospace',
        fontSize: 12,
      }}
    >
      {value}
    </Typography>
  );
}

function prettyJson(value?: string): string {
  if (!value) return '—';
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return value;
  }
}

function statusLabel(status?: string): string {
  return DELIVERY_STATUSES.find((value) => value.value === status)?.label ?? status ?? '—';
}

function deliveryColor(status?: string): 'success' | 'error' | 'warning' {
  if (status === 'SUCCEEDED') return 'success';
  if (status === 'DEAD_LETTER') return 'error';
  return 'warning';
}

function responseLabel(delivery: WebhookDelivery): string {
  if (delivery.responseStatus) {
    return delivery.errorMessage
      ? `${delivery.responseStatus} — ${delivery.errorMessage}`
      : String(delivery.responseStatus);
  }
  return delivery.errorMessage ?? '—';
}
