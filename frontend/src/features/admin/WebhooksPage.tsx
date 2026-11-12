import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Checkbox,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  Drawer,
  FormControlLabel,
  IconButton,
  Menu,
  MenuItem,
  Stack,
  Switch,
  Tab,
  Tabs,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import DeleteIcon from '@mui/icons-material/Delete';
import EditIcon from '@mui/icons-material/Edit';
import KeyIcon from '@mui/icons-material/Key';
import ListAltIcon from '@mui/icons-material/ListAlt';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import PowerSettingsNewIcon from '@mui/icons-material/PowerSettingsNew';
import ReplayIcon from '@mui/icons-material/Replay';
import SendIcon from '@mui/icons-material/Send';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { DataTable } from '@/components/DataTable';
import { Loading, PageHeader } from '@/components/Page';
import { toast } from '@/components/Toast';
import { problemMessage } from '@/api/client';
import { dateTime } from '@/lib/format';
import type { WebhookDelivery, WebhookSubscription } from '@/api/types';
import {
  createWebhook,
  deleteWebhook,
  listDeliveryLog,
  listWebhooks,
  retryWebhookDelivery,
  rotateWebhookSecret,
  sendWebhookTestEvent,
  updateWebhook,
  type WebhookInput,
} from './webhooksApi';

const EVENT_GROUPS = [
  {
    id: 'purchase_orders',
    label: 'Purchase orders',
    helperText: 'Notify ERP and vendor systems when an approved purchase order is issued.',
    events: [{ value: 'PurchaseOrderIssued', label: 'Issued' }],
  },
  {
    id: 'goods_receipts',
    label: 'Goods receipts',
    helperText: 'Trigger receiving, inventory and project-delivery updates when goods arrive.',
    events: [{ value: 'GoodsReceiptRecorded', label: 'Recorded' }],
  },
  {
    id: 'vendor_invoices',
    label: 'Vendor invoices',
    helperText:
      'Send recorded invoice details and matching status to finance and accounting systems.',
    events: [{ value: 'VendorInvoiceRecorded', label: 'Recorded' }],
  },
];

const EVENTS = EVENT_GROUPS.flatMap((group) => group.events);

const DELIVERY_EVENTS = [...EVENTS, { value: 'WebhookTest', label: 'Test event' }];

const DELIVERY_STATUSES = [
  { value: 'PENDING', label: 'Pending' },
  { value: 'SUCCEEDED', label: 'Succeeded' },
  { value: 'DEAD_LETTER', label: 'Dead letter' },
];

const emptyDraft = (): WebhookInput => ({
  url: '',
  description: '',
  eventTypes: ['PurchaseOrderIssued'],
  active: true,
});

export function WebhooksPage() {
  const queryClient = useQueryClient();
  const webhooks = useQuery({ queryKey: ['webhooks'], queryFn: listWebhooks });
  const [tab, setTab] = useState<'endpoints' | 'logs'>('endpoints');
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editing, setEditing] = useState<WebhookSubscription | null>(null);
  const [draft, setDraft] = useState<WebhookInput>(emptyDraft);
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

  const create = useMutation({
    mutationFn: () => createWebhook(draft),
    onSuccess: (result) => {
      setDialogOpen(false);
      invalidate();
      if (result.signingSecret) setSecret(result.signingSecret);
      toast.success('Webhook endpoint created.');
    },
    onError: (error) => toast.error(problemMessage(error)),
  });
  const update = useMutation({
    mutationFn: () => updateWebhook(editing!.id!, editing!.version ?? 0, draft),
    onSuccess: () => {
      setDialogOpen(false);
      setEditing(null);
      invalidate();
      toast.success('Webhook endpoint updated.');
    },
    onError: (error) => toast.error(problemMessage(error)),
  });
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
        url: subscription.url ?? '',
        description: subscription.description,
        eventTypes: subscription.eventTypes ?? [],
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

  const openCreate = () => {
    setEditing(null);
    setDraft(emptyDraft());
    setDialogOpen(true);
  };

  const openEdit = (subscription: WebhookSubscription) => {
    setEditing(subscription);
    setDraft({
      url: subscription.url ?? '',
      description: subscription.description ?? '',
      eventTypes: subscription.eventTypes ?? [],
      active: subscription.active ?? true,
    });
    setDialogOpen(true);
  };

  const toggleEvent = (eventType: string) => {
    setDraft((current) => ({
      ...current,
      eventTypes: current.eventTypes.includes(eventType)
        ? current.eventTypes.filter((value) => value !== eventType)
        : [...current.eventTypes, eventType],
    }));
  };

  const saveDisabled =
    !draft.url.trim() || draft.eventTypes.length === 0 || create.isPending || update.isPending;

  if (webhooks.isLoading) return <Loading />;

  return (
    <>
      <PageHeader
        title="Webhooks"
        actions={
          <Button variant="contained" onClick={openCreate}>
            Add endpoint
          </Button>
        }
      />
      <Alert severity="info" sx={{ mb: 2 }}>
        Deliveries are HMAC-SHA256 signed with the endpoint secret. The secret is shown only once
        when created or rotated.
      </Alert>

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
          columns={[
            {
              key: 'url',
              label: 'Endpoint',
              render: (row) => (
                <Box sx={{ minWidth: 0, maxWidth: { xs: 220, md: 420 } }}>
                  <CopyableUrl value={row.url ?? ''} />
                  {row.description && (
                    <Typography variant="caption" color="text.secondary">
                      {row.description}
                    </Typography>
                  )}
                </Box>
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
                <Tooltip title="Endpoint actions">
                  <IconButton
                    size="small"
                    aria-label="Endpoint actions"
                    onClick={(event) =>
                      setMenu({ anchorEl: event.currentTarget, subscription: row })
                    }
                  >
                    <MoreVertIcon fontSize="small" />
                  </IconButton>
                </Tooltip>
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
                      {subscription.url}
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
                  {DELIVERY_EVENTS.map((event) => (
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
                render: (delivery) => eventLabel(delivery.eventType),
              },
              {
                key: 'endpoint',
                label: 'Endpoint',
                render: (delivery) => (
                  <Box sx={{ minWidth: 0, maxWidth: { xs: 220, md: 320 } }}>
                    <CopyableUrl value={delivery.subscriptionUrl ?? 'Endpoint unavailable'} />
                  </Box>
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
            if (menu) openEdit(menu.subscription);
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

      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>{editing ? 'Edit webhook endpoint' : 'Add webhook endpoint'}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField
              label="Endpoint URL"
              value={draft.url}
              onChange={(event) => setDraft((current) => ({ ...current, url: event.target.value }))}
              placeholder="https://example.com/hooks/pams"
              fullWidth
              required
            />
            <TextField
              label="Description"
              value={draft.description}
              onChange={(event) =>
                setDraft((current) => ({ ...current, description: event.target.value }))
              }
              fullWidth
            />
            <Box>
              <Stack
                direction="row"
                justifyContent="space-between"
                alignItems="center"
                sx={{ mb: 1 }}
              >
                <Typography variant="subtitle2">Events</Typography>
                <Typography variant="caption" color="text.secondary">
                  {draft.eventTypes.length} of {EVENTS.length} selected
                </Typography>
              </Stack>
              <Stack spacing={1.5}>
                {EVENT_GROUPS.map((group) => (
                  <EventGroupCard
                    key={group.id}
                    group={group}
                    selected={draft.eventTypes}
                    onToggleEvent={toggleEvent}
                    onToggleGroup={() => {
                      const groupValues = group.events.map((event) => event.value);
                      const allSelected = groupValues.every((value) =>
                        draft.eventTypes.includes(value),
                      );
                      setDraft((current) => ({
                        ...current,
                        eventTypes: allSelected
                          ? current.eventTypes.filter((value) => !groupValues.includes(value))
                          : [...new Set([...current.eventTypes, ...groupValues])],
                      }));
                    }}
                  />
                ))}
              </Stack>
            </Box>
            <FormControlLabel
              control={
                <Switch
                  checked={draft.active}
                  onChange={(event) =>
                    setDraft((current) => ({ ...current, active: event.target.checked }))
                  }
                />
              }
              label="Active"
            />
            {editing && (
              <Typography variant="body2" color="text.secondary">
                Use “Rotate secret” from the actions menu if the signing secret was lost.
              </Typography>
            )}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDialogOpen(false)}>Cancel</Button>
          <Button
            variant="contained"
            disabled={saveDisabled}
            onClick={() => (editing ? update.mutate() : create.mutate())}
          >
            {editing ? 'Save' : 'Create'}
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={Boolean(secret)} onClose={() => setSecret(null)} fullWidth maxWidth="sm">
        <DialogTitle>Webhook signing secret</DialogTitle>
        <DialogContent>
          <Alert severity="warning" sx={{ mb: 2 }}>
            Copy this secret now. It will not be shown again.
          </Alert>
          <TextField
            value={secret ?? ''}
            fullWidth
            multiline
            slotProps={{ htmlInput: { readOnly: true } }}
          />
        </DialogContent>
        <DialogActions>
          <Button
            startIcon={<ContentCopyIcon />}
            onClick={async () => {
              if (secret) {
                await navigator.clipboard.writeText(secret);
                toast.success('Secret copied to clipboard.');
              }
            }}
          >
            Copy
          </Button>
          <Button variant="contained" onClick={() => setSecret(null)}>
            Done
          </Button>
        </DialogActions>
      </Dialog>

      <ConfirmDialog
        open={Boolean(deleteTarget)}
        title="Delete webhook endpoint"
        message={`Delete ${deleteTarget?.url ?? 'this endpoint'} and its delivery history?`}
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
              <Typography variant="h6">{eventLabel(delivery.eventType)}</Typography>
              <Typography variant="body2" color="text.secondary">
                To
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

function EventGroupCard({
  group,
  selected,
  onToggleEvent,
  onToggleGroup,
}: {
  group: (typeof EVENT_GROUPS)[number];
  selected: string[];
  onToggleEvent: (eventType: string) => void;
  onToggleGroup: () => void;
}) {
  const selectedCount = group.events.filter((event) => selected.includes(event.value)).length;
  const allSelected = selectedCount === group.events.length;
  const someSelected = selectedCount > 0 && !allSelected;
  const helperId = `${group.id}-events-helper`;

  return (
    <Box
      sx={{
        p: 1.5,
        border: 1,
        borderColor: 'divider',
        borderRadius: 2,
        bgcolor: 'background.paper',
      }}
    >
      <Stack direction="row" alignItems="flex-start" spacing={0.5}>
        <Checkbox
          size="small"
          checked={allSelected}
          indeterminate={someSelected}
          onChange={onToggleGroup}
          slotProps={{
            input: {
              'aria-label': `Select all ${group.label} events`,
              'aria-describedby': helperId,
            },
          }}
        />
        <Box sx={{ pt: 0.75 }}>
          <Typography variant="subtitle2">{group.label}</Typography>
          <Typography
            id={helperId}
            variant="caption"
            color="text.secondary"
            display="block"
            sx={{ mb: 0.5 }}
          >
            {group.helperText}
          </Typography>
          <Stack sx={{ ml: -1 }}>
            {group.events.map((event) => (
              <FormControlLabel
                key={event.value}
                control={
                  <Checkbox
                    size="small"
                    checked={selected.includes(event.value)}
                    onChange={() => onToggleEvent(event.value)}
                  />
                }
                label={event.label}
              />
            ))}
          </Stack>
        </Box>
      </Stack>
    </Box>
  );
}

function eventLabel(eventType?: string): string {
  return DELIVERY_EVENTS.find((event) => event.value === eventType)?.label ?? eventType ?? '—';
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

function CopyableUrl({ value }: { value: string }) {
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(value);
      toast.success('Endpoint URL copied.');
    } catch {
      toast.error('Could not copy endpoint URL.');
    }
  };

  return (
    <Stack direction="row" spacing={0.5} alignItems="center" sx={{ minWidth: 0 }}>
      <Tooltip title={value}>
        <Typography
          variant="body2"
          noWrap
          sx={{
            minWidth: 0,
            fontFamily: 'monospace',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
          }}
        >
          {value}
        </Typography>
      </Tooltip>
      <Tooltip title="Copy endpoint URL">
        <IconButton
          size="small"
          aria-label="Copy endpoint URL"
          onClick={() => void copy()}
          sx={{ flexShrink: 0 }}
        >
          <ContentCopyIcon sx={{ fontSize: 15 }} />
        </IconButton>
      </Tooltip>
    </Stack>
  );
}
