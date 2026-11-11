import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link as RouterLink, useParams } from 'react-router-dom';
import {
  Alert,
  Button,
  Card,
  CardContent,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Grid,
  IconButton,
  Stack,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import { problemMessage } from '@/api/client';
import { Can } from '@/auth/AbilityProvider';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { DataTable } from '@/components/DataTable';
import { Loading, PageHeader } from '@/components/Page';
import { toast } from '@/components/Toast';
import { dateOnly, dateTime, downloadBlob, money } from '@/lib/format';
import {
  approveVendorInvoice,
  disputeVendorInvoice,
  getVendorInvoice,
  rejectVendorInvoice,
  vendorInvoicePdf,
  voidVendorInvoice,
} from './api';

const statusColor = {
  MATCHED: 'success',
  MISMATCHED: 'error',
  APPROVED: 'info',
  DISPUTED: 'warning',
  REJECTED: 'error',
  VOID: 'default',
  PARTIALLY_PAID: 'warning',
  PAID: 'success',
} as const;

export function InvoiceDetailPage() {
  const { id } = useParams();
  const queryClient = useQueryClient();
  const [confirmApprove, setConfirmApprove] = useState(false);
  const [resolutionAction, setResolutionAction] = useState<'reject' | 'dispute' | 'void' | null>(
    null,
  );
  const [resolutionNote, setResolutionNote] = useState('');
  const invoice = useQuery({
    queryKey: ['vendor-invoice', id],
    queryFn: () => getVendorInvoice(id!),
  });
  const approve = useMutation({
    mutationFn: () => approveVendorInvoice(id!, invoice.data?.version ?? 0),
    onSuccess: () => {
      toast.success('Invoice approved.');
      setConfirmApprove(false);
      void queryClient.invalidateQueries({ queryKey: ['vendor-invoice', id] });
      void queryClient.invalidateQueries({ queryKey: ['vendor-invoices'] });
      void queryClient.invalidateQueries({ queryKey: ['purchase-order'] });
    },
    onError: () => setConfirmApprove(false),
  });

  const resolve = useMutation({
    mutationFn: () => {
      if (!invoice.data) throw new Error('Invoice not loaded');
      if (resolutionAction === 'reject')
        return rejectVendorInvoice(invoice.data.id!, invoice.data.version ?? 0, resolutionNote);
      if (resolutionAction === 'dispute')
        return disputeVendorInvoice(invoice.data.id!, invoice.data.version ?? 0, resolutionNote);
      return voidVendorInvoice(invoice.data.id!, invoice.data.version ?? 0, resolutionNote);
    },
    onSuccess: () => {
      toast.success(
        resolutionAction === 'reject'
          ? 'Invoice rejected.'
          : resolutionAction === 'dispute'
            ? 'Invoice disputed.'
            : 'Invoice voided.',
      );
      setResolutionAction(null);
      setResolutionNote('');
      void queryClient.invalidateQueries({ queryKey: ['vendor-invoice', id] });
      void queryClient.invalidateQueries({ queryKey: ['vendor-invoices'] });
      void queryClient.invalidateQueries({ queryKey: ['purchase-order'] });
    },
    onError: (error) => toast.error(problemMessage(error)),
  });

  if (invoice.isLoading) return <Loading />;
  if (invoice.error || !invoice.data) {
    return <Alert severity="error">Vendor invoice not found.</Alert>;
  }
  const data = invoice.data;

  return (
    <>
      <PageHeader
        title={`Invoice ${data.invoiceNumber ?? ''}`}
        actions={
          <Stack direction="row" spacing={1}>
            <Button component={RouterLink} to="/invoices" variant="outlined">
              Back
            </Button>
            <Button
              onClick={async () => {
                const blob = await vendorInvoicePdf(data.id!);
                downloadBlob(blob, `${data.internalReference ?? data.invoiceNumber}.pdf`);
              }}
            >
              PDF
            </Button>
            <Can I="approve" a="Invoice">
              {['MATCHED', 'MISMATCHED'].includes(data.status ?? '') && (
                <Button variant="contained" onClick={() => setConfirmApprove(true)}>
                  {data.status === 'MISMATCHED' ? 'Approve mismatch' : 'Approve invoice'}
                </Button>
              )}
              {['MATCHED', 'MISMATCHED'].includes(data.status ?? '') && (
                <Button color="error" onClick={() => setResolutionAction('reject')}>
                  Reject
                </Button>
              )}
              {data.status === 'MISMATCHED' && (
                <Button color="warning" onClick={() => setResolutionAction('dispute')}>
                  Dispute
                </Button>
              )}
              {!['VOID', 'REJECTED'].includes(data.status ?? '') && (
                <Button
                  color="error"
                  variant="outlined"
                  onClick={() => setResolutionAction('void')}
                >
                  Void
                </Button>
              )}
            </Can>
          </Stack>
        }
      />
      {data.resolutionNote && (
        <Alert severity={data.status === 'DISPUTED' ? 'warning' : 'error'} sx={{ mb: 2 }}>
          {data.status}: {data.resolutionNote}
          {data.resolvedBy ? ` · ${data.resolvedBy}` : ''}
        </Alert>
      )}
      {data.status === 'MISMATCHED' ? (
        <Alert severity="error" sx={{ mb: 2 }}>
          Three-way match failed. Review the line-level reasons below. Finance approval accepts the
          variance.
        </Alert>
      ) : data.status === 'APPROVED' ? (
        <Alert severity="info" sx={{ mb: 2 }}>
          Approved by {data.approvedBy ?? 'Finance'} on {dateTime(data.approvedAt)}.
        </Alert>
      ) : (
        <Alert severity="success" sx={{ mb: 2 }}>
          Invoice matches the purchase order and received quantities within tolerance.
        </Alert>
      )}

      <Grid container spacing={2}>
        <Grid size={{ xs: 12 }}>
          <Card>
            <CardContent>
              <Stack direction={{ xs: 'column', sm: 'row' }} spacing={3} flexWrap="wrap">
                <Stack>
                  <Typography color="text.secondary">Status</Typography>
                  <Chip
                    label={data.status}
                    color={statusColor[data.status ?? 'MISMATCHED']}
                    size="small"
                    sx={{ alignSelf: 'flex-start', mt: 0.5 }}
                  />
                </Stack>
                <Stack>
                  <Typography color="text.secondary">Purchase order</Typography>
                  <Typography
                    component={RouterLink}
                    to={`/purchase-orders/${data.purchaseOrderId}`}
                    color="primary"
                  >
                    {data.poNumber}
                  </Typography>
                </Stack>
                <Stack>
                  <Typography color="text.secondary">Internal reference</Typography>
                  <Stack direction="row" spacing={0.5} alignItems="center">
                    <Typography>{data.internalReference ?? '—'}</Typography>
                    {data.internalReference && (
                      <Tooltip title="Copy internal reference">
                        <IconButton
                          size="small"
                          aria-label="Copy internal reference"
                          onClick={() =>
                            void navigator.clipboard.writeText(data.internalReference!)
                          }
                        >
                          <ContentCopyIcon fontSize="small" />
                        </IconButton>
                      </Tooltip>
                    )}
                  </Stack>
                </Stack>
                <Stack>
                  <Typography color="text.secondary">Vendor invoice no.</Typography>
                  <Typography>{data.invoiceNumber}</Typography>
                </Stack>
                <Stack>
                  <Typography color="text.secondary">Invoice date</Typography>
                  <Typography>{dateOnly(data.invoiceDate)}</Typography>
                </Stack>
                <Stack>
                  <Typography color="text.secondary">Due date</Typography>
                  <Typography>{dateOnly(data.dueDate)}</Typography>
                </Stack>
                <Stack>
                  <Typography color="text.secondary">Tolerance</Typography>
                  <Typography>{data.tolerancePercent}%</Typography>
                </Stack>
              </Stack>
              <Stack direction={{ xs: 'column', sm: 'row' }} spacing={3} sx={{ mt: 2 }}>
                <Stack>
                  <Typography color="text.secondary">Subtotal</Typography>
                  <Typography>{money(data.subtotal)}</Typography>
                </Stack>
                <Stack>
                  <Typography color="text.secondary">Tax</Typography>
                  <Typography>{money(data.tax)}</Typography>
                </Stack>
                <Stack>
                  <Typography color="text.secondary">Total</Typography>
                  <Typography variant="h6">{money(data.total)}</Typography>
                </Stack>
              </Stack>
              {data.notes && (
                <Typography variant="body2" color="text.secondary" sx={{ mt: 2 }}>
                  {data.notes}
                </Typography>
              )}
            </CardContent>
          </Card>

          <Card sx={{ mt: 2 }}>
            <CardContent>
              <Typography variant="h6" sx={{ mb: 2 }}>
                Three-way match
              </Typography>
              <DataTable
                ariaLabel="Invoice lines"
                rows={data.lines ?? []}
                getRowId={(line) => line.purchaseOrderLineId ?? ''}
                columns={[
                  { key: 'description', label: 'Description', render: (line) => line.description },
                  { key: 'qty', label: 'Qty', render: (line) => line.quantity },
                  { key: 'unit', label: 'Unit', render: (line) => line.unit },
                  {
                    key: 'price',
                    label: 'Invoice price',
                    render: (line) => money(line.unitPrice),
                  },
                  {
                    key: 'variance',
                    label: 'Variance',
                    render: (line) => `${line.priceVariancePercent ?? 0}%`,
                  },
                  {
                    key: 'matched',
                    label: 'Match',
                    render: (line) => (
                      <Chip
                        size="small"
                        label={line.matched ? 'Matched' : 'Mismatch'}
                        color={line.matched ? 'success' : 'error'}
                      />
                    ),
                  },
                  {
                    key: 'reason',
                    label: 'Reason',
                    render: (line) => line.matchMessage ?? '—',
                  },
                ]}
              />
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      <Dialog
        open={Boolean(resolutionAction)}
        onClose={() => setResolutionAction(null)}
        fullWidth
        maxWidth="sm"
      >
        <DialogTitle>
          {resolutionAction === 'reject'
            ? 'Reject invoice'
            : resolutionAction === 'dispute'
              ? 'Dispute invoice'
              : 'Void invoice'}
        </DialogTitle>
        <DialogContent>
          <TextField
            fullWidth
            multiline
            minRows={3}
            label="Reason"
            value={resolutionNote}
            onChange={(event) => setResolutionNote(event.target.value)}
            sx={{ mt: 1 }}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setResolutionAction(null)}>Cancel</Button>
          <Button
            variant="contained"
            color={resolutionAction === 'dispute' ? 'warning' : 'error'}
            disabled={!resolutionNote.trim() || resolve.isPending}
            onClick={() => resolve.mutate()}
          >
            Confirm
          </Button>
        </DialogActions>
      </Dialog>

      <ConfirmDialog
        open={confirmApprove}
        title={data.status === 'MISMATCHED' ? 'Approve mismatched invoice' : 'Approve invoice'}
        message={
          data.status === 'MISMATCHED'
            ? 'This accepts the quantity or price variance and records Finance approval.'
            : 'Authorize this matched invoice for payment processing.'
        }
        confirmLabel="Approve invoice"
        color="warning"
        busy={approve.isPending}
        onCancel={() => setConfirmApprove(false)}
        onConfirm={() => approve.mutate()}
      />
    </>
  );
}
