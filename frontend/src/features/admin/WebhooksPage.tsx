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
  FormControlLabel,
  IconButton,
  Stack,
  Switch,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { DataTable } from '@/components/DataTable';
import { Loading, PageHeader } from '@/components/Page';
import { toast } from '@/components/Toast';
import { problemMessage } from '@/api/client';
import { dateTime } from '@/lib/format';
import type { WebhookSubscription } from '@/api/types';
import {
  createWebhook,
  deleteWebhook,
  listWebhookDeliveries,
  listWebhooks,
  retryWebhookDelivery,
  rotateWebhookSecret,
  updateWebhook,
  type WebhookInput,
} from './webhooksApi';

const EVENTS = [
  { value: 'PurchaseOrderIssued', label: 'Purchase order issued' },
  { value: 'GoodsReceiptRecorded', label: 'Goods receipt recorded' },
  { value: 'VendorInvoiceRecorded', label: 'Vendor invoice recorded' },
];

const emptyDraft = (): WebhookInput => ({
  url: '',
  description: '',
  eventTypes: ['PurchaseOrderIssued'],
  active: true,
});

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

export function WebhooksPage() {
  const queryClient = useQueryClient();
  const webhooks = useQuery({ queryKey: ['webhooks'], queryFn: listWebhooks });
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editing, setEditing] = useState<WebhookSubscription | null>(null);
  const [draft, setDraft] = useState<WebhookInput>(emptyDraft);
  const [secret, setSecret] = useState<string | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<WebhookSubscription | null>(null);
  const [selected, setSelected] = useState<WebhookSubscription | null>(null);

  const deliveries = useQuery({
    queryKey: ['webhook-deliveries', selected?.id],
    queryFn: () => listWebhookDeliveries(selected!.id!),
    enabled: Boolean(selected?.id),
  });

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ['webhooks'] });
    void queryClient.invalidateQueries({ queryKey: ['webhook-deliveries'] });
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
      if (selected?.id === deleteTarget?.id) setSelected(null);
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
  const retry = useMutation({
    mutationFn: retryWebhookDelivery,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['webhook-deliveries'] });
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

      <DataTable
        ariaLabel="Webhook endpoints"
        rows={webhooks.data ?? []}
        getRowId={(subscription) => subscription.id ?? ''}
        emptyMessage="No webhook endpoints configured."
        columns={[
          {
            key: 'url',
            label: 'Endpoint URL',
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
            key: 'events',
            label: 'Events',
            render: (row) =>
              (row.eventTypes ?? [])
                .map((event) => EVENTS.find((option) => option.value === event)?.label ?? event)
                .join(', '),
          },
          {
            key: 'active',
            label: 'Active',
            render: (row) => (
              <Chip
                size="small"
                label={row.active ? 'Active' : 'Paused'}
                color={row.active ? 'success' : 'default'}
              />
            ),
          },
          {
            key: 'success',
            label: 'Last success',
            render: (row) => dateTime(row.lastSuccessAt),
          },
          {
            key: 'failure',
            label: 'Last failure',
            render: (row) => dateTime(row.lastFailureAt),
          },
          {
            key: 'actions',
            label: 'Actions',
            align: 'right',
            render: (row) => (
              <Stack direction="row" spacing={0.5} justifyContent="flex-end" flexWrap="wrap">
                <Button size="small" onClick={() => setSelected(row)}>
                  Deliveries
                </Button>
                <Button size="small" onClick={() => openEdit(row)}>
                  Edit
                </Button>
                <Button size="small" onClick={() => rotate.mutate(row)} disabled={rotate.isPending}>
                  Rotate secret
                </Button>
                <Button size="small" color="error" onClick={() => setDeleteTarget(row)}>
                  Delete
                </Button>
              </Stack>
            ),
          },
        ]}
      />

      {selected && (
        <Card sx={{ mt: 2 }}>
          <CardContent>
            <Stack
              direction="row"
              justifyContent="space-between"
              alignItems="center"
              sx={{ mb: 2 }}
            >
              <Box sx={{ minWidth: 0, maxWidth: { xs: '70%', md: 560 } }}>
                <Typography variant="h6">Deliveries</Typography>
                <CopyableUrl value={selected.url ?? ''} />
              </Box>
              <Button size="small" onClick={() => setSelected(null)}>
                Close
              </Button>
            </Stack>
            {deliveries.isLoading ? (
              <Loading rows={3} />
            ) : (
              <DataTable
                ariaLabel="Webhook deliveries"
                rows={deliveries.data ?? []}
                getRowId={(delivery) => delivery.id ?? ''}
                emptyMessage="No deliveries yet."
                columns={[
                  {
                    key: 'created',
                    label: 'Created',
                    render: (delivery) => dateTime(delivery.createdAt),
                  },
                  {
                    key: 'type',
                    label: 'Event',
                    render: (delivery) =>
                      EVENTS.find((option) => option.value === delivery.eventType)?.label ??
                      delivery.eventType,
                  },
                  {
                    key: 'status',
                    label: 'Status',
                    render: (delivery) => (
                      <Chip
                        size="small"
                        label={delivery.status}
                        color={
                          delivery.status === 'SUCCEEDED'
                            ? 'success'
                            : delivery.status === 'DEAD_LETTER'
                              ? 'error'
                              : 'warning'
                        }
                      />
                    ),
                  },
                  { key: 'attempts', label: 'Attempts', render: (delivery) => delivery.attempts },
                  {
                    key: 'response',
                    label: 'Response',
                    render: (delivery) =>
                      delivery.responseStatus
                        ? `${delivery.responseStatus}${delivery.errorMessage ? ` — ${delivery.errorMessage}` : ''}`
                        : (delivery.errorMessage ?? '—'),
                  },
                  {
                    key: 'actions',
                    label: 'Actions',
                    align: 'right',
                    render: (delivery) =>
                      delivery.status !== 'SUCCEEDED' ? (
                        <Button
                          size="small"
                          disabled={retry.isPending}
                          onClick={() => retry.mutate(delivery.id!)}
                        >
                          Retry
                        </Button>
                      ) : (
                        '—'
                      ),
                  },
                ]}
              />
            )}
          </CardContent>
        </Card>
      )}

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
              <Typography variant="subtitle2" sx={{ mb: 1 }}>
                Events
              </Typography>
              <Stack>
                {EVENTS.map((event) => (
                  <FormControlLabel
                    key={event.value}
                    control={
                      <Checkbox
                        checked={draft.eventTypes.includes(event.value)}
                        onChange={() => toggleEvent(event.value)}
                      />
                    }
                    label={event.label}
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
                Use “Rotate secret” from the list if the signing secret was lost.
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
