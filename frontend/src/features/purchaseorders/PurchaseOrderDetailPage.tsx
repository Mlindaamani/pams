import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link as RouterLink, useParams } from 'react-router-dom';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  Grid,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import { useAbility } from '@/auth/AbilityProvider';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { DataTable } from '@/components/DataTable';
import { Loading, PageHeader } from '@/components/Page';
import { toast } from '@/components/Toast';
import { dateOnly, dateTime, downloadBlob, money } from '@/lib/format';
import { problemMessage } from '@/api/client';
import type { RecordReceiptRequest, RecordVendorInvoiceRequest } from '@/api/types';
import { listVendors } from '@/features/vendors/api';
import {
  approvePurchaseOrder,
  cancelPurchaseOrder,
  getPurchaseOrder,
  getPurchaseOrderActions,
  getPurchaseOrderApproval,
  issuePurchaseOrder,
  listGoodsReceipts,
  purchaseOrderPdf,
  recordGoodsReceipt,
  rejectPurchaseOrder,
  requestPurchaseOrderChanges,
  submitPurchaseOrderForApproval,
  updatePurchaseOrder,
} from './api';
import { listInvoicesForPurchaseOrder, recordVendorInvoice } from '@/features/invoices/api';

export function PurchaseOrderDetailPage() {
  const { id } = useParams();
  const queryClient = useQueryClient();
  const ability = useAbility();
  const order = useQuery({
    queryKey: ['purchase-order', id],
    queryFn: () => getPurchaseOrder(id!),
  });
  const actionsQuery = useQuery({
    queryKey: ['purchase-order', id, 'actions'],
    queryFn: () => getPurchaseOrderActions(id!),
    enabled: Boolean(id),
  });
  const vendors = useQuery({ queryKey: ['vendors'], queryFn: () => listVendors({ size: 100 }) });
  const receipts = useQuery({
    queryKey: ['goods-receipts', id],
    queryFn: () => listGoodsReceipts(id!),
    enabled: Boolean(id),
  });
  const canReadInvoices = ability.can('read', 'Invoice');
  const invoices = useQuery({
    queryKey: ['vendor-invoices', 'po', id],
    queryFn: () => listInvoicesForPurchaseOrder(id!),
    enabled: Boolean(id) && canReadInvoices,
  });
  const approval = useQuery({
    queryKey: ['purchase-order-approval', id],
    queryFn: () => getPurchaseOrderApproval(id!),
    enabled: Boolean(id),
    retry: false,
  });
  const [approvalAction, setApprovalAction] = useState<'reject' | 'changes' | null>(null);
  const [approvalComment, setApprovalComment] = useState('');
  const [editOpen, setEditOpen] = useState(false);
  const [confirmCancel, setConfirmCancel] = useState(false);
  const [paymentTerms, setPaymentTerms] = useState('');
  const [deliveryDate, setDeliveryDate] = useState('');
  const [receiptOpen, setReceiptOpen] = useState(false);
  const [receivedDate, setReceivedDate] = useState(new Date().toISOString().slice(0, 10));
  const [deliveryNote, setDeliveryNote] = useState('');
  const [receiptNotes, setReceiptNotes] = useState('');
  const [receiptQuantities, setReceiptQuantities] = useState<Record<string, string>>({});
  const [receiptError, setReceiptError] = useState<string | null>(null);
  const [invoiceOpen, setInvoiceOpen] = useState(false);
  const [invoiceNumber, setInvoiceNumber] = useState('');
  const [invoiceDate, setInvoiceDate] = useState(new Date().toISOString().slice(0, 10));
  const [invoiceDueDate, setInvoiceDueDate] = useState('');
  const [invoiceCurrency, setInvoiceCurrency] = useState('');
  const [invoiceTax, setInvoiceTax] = useState('0');
  const [invoiceNotes, setInvoiceNotes] = useState('');
  const [invoiceFormLines, setInvoiceFormLines] = useState<
    Record<string, { quantity: string; unitPrice: string }>
  >({});
  const [invoiceError, setInvoiceError] = useState<string | null>(null);

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ['purchase-order', id] });
    void queryClient.invalidateQueries({ queryKey: ['purchase-orders'] });
  };
  const issue = useMutation({
    mutationFn: () => issuePurchaseOrder(id!, order.data?.version ?? 0),
    onSuccess: () => {
      toast.success('Purchase order issued.');
      invalidate();
    },
  });
  const update = useMutation({
    mutationFn: () =>
      updatePurchaseOrder(id!, order.data?.version ?? 0, {
        paymentTerms,
        deliveryDate: deliveryDate || undefined,
      }),
    onSuccess: () => {
      toast.success('Purchase order updated.');
      setEditOpen(false);
      invalidate();
    },
  });
  const cancel = useMutation({
    mutationFn: () => cancelPurchaseOrder(id!, order.data?.version ?? 0),
    onSuccess: () => {
      toast.success('Purchase order cancelled.');
      invalidate();
    },
  });
  const recordInvoice = useMutation({
    mutationFn: (body: RecordVendorInvoiceRequest) => recordVendorInvoice(id!, body),
    onSuccess: (invoice) => {
      if (invoice.status === 'MISMATCHED') {
        toast.info('Invoice recorded and flagged for Finance review.', 'Three-way mismatch');
      } else {
        toast.success('Invoice matched successfully.');
      }
      setInvoiceOpen(false);
      setInvoiceError(null);
      void queryClient.invalidateQueries({ queryKey: ['vendor-invoices'] });
    },
    onError: (error) => setInvoiceError(problemMessage(error)),
  });

  const openInvoice = () => {
    if (!order.data) return;
    setInvoiceNumber('');
    setInvoiceDate(new Date().toISOString().slice(0, 10));
    setInvoiceDueDate('');
    setInvoiceCurrency(order.data.currency ?? '');
    setInvoiceTax('0');
    setInvoiceNotes('');
    setInvoiceError(null);
    const initial: Record<string, { quantity: string; unitPrice: string }> = {};
    (order.data.lines ?? []).forEach((line) => {
      if (line.id) {
        initial[line.id] = { quantity: '', unitPrice: line.unitPrice?.amount ?? '0' };
      }
    });
    setInvoiceFormLines(initial);
    setInvoiceOpen(true);
  };

  const submitInvoice = () => {
    if (!order.data) return;
    setInvoiceError(null);
    if (!invoiceNumber.trim()) {
      setInvoiceError('Invoice number is required.');
      return;
    }
    const lines = (order.data.lines ?? [])
      .map((line) => {
        const entry = invoiceFormLines[line.id ?? ''];
        return {
          line,
          quantity: Number(entry?.quantity ?? ''),
          unitPrice: Number(entry?.unitPrice ?? ''),
        };
      })
      .filter((item) => Number.isFinite(item.quantity) && item.quantity > 0);
    if (lines.length === 0) {
      setInvoiceError('Enter an invoiced quantity for at least one line.');
      return;
    }
    if (lines.some((item) => !Number.isFinite(item.unitPrice) || item.unitPrice < 0)) {
      setInvoiceError('Unit prices must be zero or greater.');
      return;
    }
    recordInvoice.mutate({
      invoiceNumber,
      invoiceDate,
      dueDate: invoiceDueDate || undefined,
      currency: invoiceCurrency,
      taxAmount: Number(invoiceTax || 0),
      notes: invoiceNotes || undefined,
      lines: lines.map((item) => ({
        purchaseOrderLineId: item.line.id!,
        quantity: item.quantity,
        unitPrice: item.unitPrice,
      })),
    });
  };

  const receive = useMutation({
    mutationFn: (body: RecordReceiptRequest) =>
      recordGoodsReceipt(id!, order.data?.version ?? 0, body),
    onSuccess: () => {
      toast.success('Goods receipt recorded.');
      setReceiptOpen(false);
      setReceiptError(null);
      invalidate();
      void queryClient.invalidateQueries({ queryKey: ['goods-receipts', id] });
    },
    onError: (error) => setReceiptError(problemMessage(error)),
  });

  const openReceipt = () => {
    setReceivedDate(new Date().toISOString().slice(0, 10));
    setDeliveryNote('');
    setReceiptNotes('');
    setReceiptQuantities({});
    setReceiptError(null);
    setReceiptOpen(true);
  };

  const submitReceipt = () => {
    if (!order.data) return;
    setReceiptError(null);
    const lines = (order.data.lines ?? [])
      .map((line) => ({
        line,
        quantity: Number(receiptQuantities[line.id ?? ''] ?? ''),
      }))
      .filter((item) => Number.isFinite(item.quantity) && item.quantity > 0);
    if (lines.length === 0) {
      setReceiptError('Enter a received quantity for at least one line.');
      return;
    }
    const excessive = lines.find(
      (item) => item.quantity > Number(item.line.remainingQuantity ?? 0),
    );
    if (excessive) {
      setReceiptError(
        `Line "${excessive.line.description}" exceeds the remaining quantity of ${excessive.line.remainingQuantity}.`,
      );
      return;
    }
    receive.mutate({
      receivedDate,
      deliveryNoteReference: deliveryNote || undefined,
      notes: receiptNotes || undefined,
      lines: lines.map((item) => ({
        purchaseOrderLineId: item.line.id!,
        quantity: item.quantity,
      })),
    });
  };

  const submitApproval = useMutation({
    mutationFn: () => submitPurchaseOrderForApproval(id!),
    onSuccess: () => {
      toast.success('Purchase order submitted for approval.');
      void queryClient.invalidateQueries({ queryKey: ['purchase-order', id] });
      void queryClient.invalidateQueries({ queryKey: ['purchase-order-approval', id] });
    },
    onError: (error) => toast.error(problemMessage(error)),
  });
  const approveApproval = useMutation({
    mutationFn: () => approvePurchaseOrder(id!),
    onSuccess: () => {
      toast.success('Purchase order approved.');
      void queryClient.invalidateQueries({ queryKey: ['purchase-order', id] });
      void queryClient.invalidateQueries({ queryKey: ['purchase-order-approval', id] });
    },
    onError: (error) => toast.error(problemMessage(error)),
  });
  const decideApproval = useMutation({
    mutationFn: () =>
      approvalAction === 'reject'
        ? rejectPurchaseOrder(id!, approvalComment)
        : requestPurchaseOrderChanges(id!, approvalComment),
    onSuccess: () => {
      toast.success(
        approvalAction === 'reject' ? 'Purchase order rejected.' : 'Changes requested.',
      );
      setApprovalAction(null);
      setApprovalComment('');
      void queryClient.invalidateQueries({ queryKey: ['purchase-order', id] });
      void queryClient.invalidateQueries({ queryKey: ['purchase-order-approval', id] });
    },
    onError: (error) => toast.error(problemMessage(error)),
  });

  if (order.isLoading) return <Loading />;
  if (order.error || !order.data) return <Alert severity="error">Purchase order not found.</Alert>;

  const data = order.data;
  const allowed = new Set(actionsQuery.data?.actions ?? []);
  const vendorName =
    vendors.data?.content.find((vendor) => vendor.id === data.vendorId)?.name ?? data.vendorId;

  return (
    <>
      <PageHeader
        title={data.poNumber ?? 'Purchase order'}
        actions={
          <Stack direction="row" spacing={1}>
            <Button component={RouterLink} to="/purchase-orders" variant="outlined">
              Back
            </Button>
            {allowed.has('EDIT') && (
              <Button
                onClick={() => {
                  setPaymentTerms(data.paymentTerms ?? '');
                  setDeliveryDate(data.deliveryDate ?? '');
                  setEditOpen(true);
                }}
              >
                Edit
              </Button>
            )}
            <Button
              onClick={async () => {
                const blob = await purchaseOrderPdf(data.id!);
                downloadBlob(blob, `${data.poNumber}.pdf`);
              }}
            >
              PDF
            </Button>
            {allowed.has('RECEIVE') && (
              <Button variant="contained" onClick={openReceipt}>
                Record receipt
              </Button>
            )}
            {allowed.has('ISSUE') && (
              <Button variant="contained" onClick={() => issue.mutate()} disabled={issue.isPending}>
                Issue
              </Button>
            )}
            {allowed.has('CANCEL') && (
              <Button
                color="error"
                onClick={() => setConfirmCancel(true)}
                disabled={cancel.isPending}
              >
                Cancel
              </Button>
            )}
          </Stack>
        }
      />

      {approval.data && (
        <Card sx={{ mb: 2 }}>
          <CardContent>
            <Stack
              direction={{ xs: 'column', sm: 'row' }}
              justifyContent="space-between"
              alignItems={{ xs: 'flex-start', sm: 'center' }}
              spacing={2}
              sx={{ mb: 2 }}
            >
              <Box>
                <Stack direction="row" spacing={1} alignItems="center">
                  <Typography variant="h6">Purchase-order approval</Typography>
                  <Chip size="small" label={approval.data.status} variant="outlined" />
                </Stack>
                <Typography variant="body2" color="text.secondary">
                  Current stage {approval.data.currentSequence}
                </Typography>
              </Box>
              <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                {allowed.has('SUBMIT_APPROVAL') && approval.data.status === 'CHANGES_REQUESTED' && (
                  <Button
                    variant="contained"
                    disabled={submitApproval.isPending}
                    onClick={() => submitApproval.mutate()}
                  >
                    Resubmit for approval
                  </Button>
                )}
                {allowed.has('APPROVE') && (
                  <Button
                    variant="contained"
                    disabled={approveApproval.isPending}
                    onClick={() => approveApproval.mutate()}
                  >
                    Approve
                  </Button>
                )}
                {allowed.has('REJECT') && (
                  <Button color="error" onClick={() => setApprovalAction('reject')}>
                    Reject
                  </Button>
                )}
                {allowed.has('REQUEST_CHANGES') && (
                  <Button onClick={() => setApprovalAction('changes')}>Request changes</Button>
                )}
              </Stack>
            </Stack>
            <Stack spacing={1}>
              {(approval.data.steps ?? []).map((step) => (
                <Stack
                  key={step.id}
                  direction={{ xs: 'column', sm: 'row' }}
                  justifyContent="space-between"
                  spacing={1}
                  sx={{
                    p: 1.25,
                    borderRadius: 1,
                    bgcolor: step.status === 'PENDING' ? 'action.hover' : 'transparent',
                    border: '1px solid',
                    borderColor: 'divider',
                  }}
                >
                  <Box>
                    <Typography variant="body2" fontWeight={600}>
                      Stage {step.sequence} · {step.name}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      {step.approverRole ?? step.assigneeUserId ?? 'Assigned approver'}
                    </Typography>
                  </Box>
                  <Chip
                    size="small"
                    label={step.status}
                    color={
                      step.status === 'APPROVED'
                        ? 'success'
                        : step.status === 'PENDING'
                          ? 'info'
                          : 'default'
                    }
                    variant="outlined"
                  />
                </Stack>
              ))}
            </Stack>
          </CardContent>
        </Card>
      )}

      <Grid container spacing={2}>
        <Grid size={{ xs: 12 }}>
          <Card>
            <CardContent>
              <Stack direction={{ xs: 'column', sm: 'row' }} spacing={3}>
                <Stack>
                  <Typography color="text.secondary">Status</Typography>
                  <Typography variant="h6">{data.status}</Typography>
                </Stack>
                <Stack>
                  <Typography color="text.secondary">Vendor</Typography>
                  <Typography>{vendorName}</Typography>
                </Stack>
                <Stack>
                  <Typography color="text.secondary">Total</Typography>
                  <Typography variant="h6">{money(data.total)}</Typography>
                </Stack>
                <Stack>
                  <Typography color="text.secondary">Issued</Typography>
                  <Typography>{dateTime(data.issuedAt)}</Typography>
                </Stack>
              </Stack>
              <Stack direction={{ xs: 'column', sm: 'row' }} spacing={3} sx={{ mt: 2 }}>
                <Typography variant="body2" color="text.secondary">
                  Payment terms: {data.paymentTerms ?? '—'}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  Delivery: {dateOnly(data.deliveryDate)}
                </Typography>
                <Typography
                  variant="body2"
                  component={RouterLink}
                  to={`/requests/${data.requestId}`}
                  color="primary"
                >
                  View source request
                </Typography>
              </Stack>
            </CardContent>
          </Card>

          <Card sx={{ mt: 2 }}>
            <CardContent>
              <Typography variant="h6" sx={{ mb: 2 }}>
                Line items
              </Typography>
              <DataTable
                ariaLabel="Purchase order lines"
                rows={data.lines ?? []}
                getRowId={(line) => line.id ?? `${line.sequence}-${line.description}`}
                columns={[
                  { key: 'description', label: 'Description', render: (line) => line.description },
                  { key: 'qty', label: 'Ordered', render: (line) => line.quantity },
                  {
                    key: 'received',
                    label: 'Received',
                    render: (line) => line.receivedQuantity ?? '0',
                  },
                  {
                    key: 'remaining',
                    label: 'Remaining',
                    render: (line) => line.remainingQuantity ?? line.quantity,
                  },
                  { key: 'unit', label: 'Unit', render: (line) => line.unit },
                  { key: 'price', label: 'Unit price', render: (line) => money(line.unitPrice) },
                  { key: 'total', label: 'Line total', render: (line) => money(line.lineTotal) },
                ]}
              />
            </CardContent>
          </Card>

          {canReadInvoices && (
            <Card sx={{ mt: 2 }}>
              <CardContent>
                <Stack
                  direction="row"
                  justifyContent="space-between"
                  alignItems="center"
                  sx={{ mb: 2 }}
                >
                  <Typography variant="h6">Invoices</Typography>
                  {allowed.has('CREATE_INVOICE') && (
                    <Button variant="outlined" size="small" onClick={openInvoice}>
                      Record invoice
                    </Button>
                  )}
                </Stack>
                <DataTable
                  ariaLabel="Purchase order invoices"
                  rows={invoices.data ?? []}
                  getRowId={(invoice) => invoice.id ?? ''}
                  emptyMessage="No invoices recorded yet."
                  columns={[
                    {
                      key: 'number',
                      label: 'Invoice',
                      render: (invoice) => (
                        <RouterLink to={`/invoices/${invoice.id}`}>
                          {invoice.invoiceNumber}
                        </RouterLink>
                      ),
                    },
                    {
                      key: 'status',
                      label: 'Match status',
                      render: (invoice) => invoice.status,
                    },
                    {
                      key: 'date',
                      label: 'Invoice date',
                      render: (invoice) => dateOnly(invoice.invoiceDate),
                    },
                    {
                      key: 'total',
                      label: 'Total',
                      align: 'right',
                      render: (invoice) => money(invoice.total),
                    },
                  ]}
                />
              </CardContent>
            </Card>
          )}

          <Card sx={{ mt: 2 }}>
            <CardContent>
              <Typography variant="h6" sx={{ mb: 2 }}>
                Goods receipts
              </Typography>
              {receipts.isLoading ? (
                <Loading rows={2} />
              ) : (
                <DataTable
                  ariaLabel="Goods receipts"
                  rows={receipts.data ?? []}
                  getRowId={(receipt) => receipt.id ?? ''}
                  emptyMessage="No goods received yet."
                  columns={[
                    {
                      key: 'receiptNumber',
                      label: 'Reference',
                      render: (receipt) => receipt.receiptNumber ?? '—',
                    },
                    {
                      key: 'date',
                      label: 'Received',
                      render: (receipt) => dateOnly(receipt.receivedDate),
                    },
                    {
                      key: 'reference',
                      label: 'Delivery note',
                      render: (receipt) => receipt.deliveryNoteReference ?? '—',
                    },
                    {
                      key: 'lines',
                      label: 'Items',
                      render: (receipt) =>
                        receipt.lines
                          ?.map((line) => `${line.quantity} ${line.unit} ${line.description}`)
                          .join(', ') ?? '—',
                    },
                    {
                      key: 'by',
                      label: 'Recorded by',
                      render: (receipt) => receipt.recordedByName ?? receipt.createdBy ?? '—',
                    },
                    {
                      key: 'created',
                      label: 'Recorded',
                      render: (receipt) => dateTime(receipt.createdAt),
                    },
                  ]}
                />
              )}
            </CardContent>
          </Card>
        </Grid>
      </Grid>
      <Dialog
        open={Boolean(approvalAction)}
        onClose={() => setApprovalAction(null)}
        fullWidth
        maxWidth="sm"
      >
        <DialogTitle>
          {approvalAction === 'reject' ? 'Reject purchase order' : 'Request changes'}
        </DialogTitle>
        <DialogContent>
          <TextField
            fullWidth
            multiline
            minRows={3}
            label="Comment"
            value={approvalComment}
            onChange={(event) => setApprovalComment(event.target.value)}
            sx={{ mt: 1 }}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setApprovalAction(null)}>Cancel</Button>
          <Button
            variant="contained"
            color={approvalAction === 'reject' ? 'error' : 'primary'}
            disabled={!approvalComment.trim() || decideApproval.isPending}
            onClick={() => decideApproval.mutate()}
          >
            {approvalAction === 'reject' ? 'Reject' : 'Request changes'}
          </Button>
        </DialogActions>
      </Dialog>

      <ConfirmDialog
        open={confirmCancel}
        title="Cancel purchase order"
        message="Cancel this purchase order? Any committed budget will be released."
        confirmLabel="Cancel purchase order"
        color="warning"
        busy={cancel.isPending}
        onCancel={() => setConfirmCancel(false)}
        onConfirm={() => cancel.mutate(undefined, { onSuccess: () => setConfirmCancel(false) })}
      />

      <Dialog open={invoiceOpen} onClose={() => setInvoiceOpen(false)} fullWidth maxWidth="md">
        <DialogTitle>Record vendor invoice</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            {invoiceError && <Alert severity="error">{invoiceError}</Alert>}
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
              <TextField
                label="Vendor invoice number"
                value={invoiceNumber}
                onChange={(event) => setInvoiceNumber(event.target.value)}
                helperText="Use the number printed on the vendor's invoice. PAMS generates its own internal reference."
                fullWidth
                required
              />
              <TextField
                label="Invoice date"
                type="date"
                value={invoiceDate}
                onChange={(event) => setInvoiceDate(event.target.value)}
                slotProps={{ inputLabel: { shrink: true } }}
                fullWidth
              />
              <TextField
                label="Due date"
                type="date"
                value={invoiceDueDate}
                onChange={(event) => setInvoiceDueDate(event.target.value)}
                slotProps={{ inputLabel: { shrink: true } }}
                fullWidth
              />
            </Stack>
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
              <TextField
                label="Currency"
                value={invoiceCurrency}
                onChange={(event) => setInvoiceCurrency(event.target.value.toUpperCase())}
                slotProps={{ htmlInput: { maxLength: 3 } }}
                fullWidth
              />
              <TextField
                label="Tax amount"
                type="number"
                value={invoiceTax}
                onChange={(event) => setInvoiceTax(event.target.value)}
                slotProps={{ htmlInput: { min: 0, step: '0.01' } }}
                fullWidth
              />
            </Stack>
            <TextField
              label="Notes"
              value={invoiceNotes}
              onChange={(event) => setInvoiceNotes(event.target.value)}
              multiline
              minRows={2}
            />
            <Divider />
            <Typography variant="subtitle2" color="text.secondary">
              Invoice lines
            </Typography>
            {(data.lines ?? []).map((line) => {
              const entry = invoiceFormLines[line.id ?? ''] ?? {
                quantity: '',
                unitPrice: line.unitPrice?.amount ?? '0',
              };
              return (
                <Stack
                  key={line.id}
                  direction={{ xs: 'column', sm: 'row' }}
                  spacing={2}
                  alignItems={{ xs: 'stretch', sm: 'center' }}
                >
                  <Box sx={{ flex: 1 }}>
                    <Typography>{line.description}</Typography>
                    <Typography variant="body2" color="text.secondary">
                      Received {line.receivedQuantity ?? '0'} of {line.quantity} {line.unit}; PO
                      price {money(line.unitPrice)}
                    </Typography>
                  </Box>
                  <TextField
                    label="Invoiced qty"
                    type="number"
                    size="small"
                    value={entry.quantity}
                    onChange={(event) =>
                      setInvoiceFormLines((current) => ({
                        ...current,
                        [line.id ?? '']: { ...entry, quantity: event.target.value },
                      }))
                    }
                    sx={{ width: { xs: '100%', sm: 150 } }}
                  />
                  <TextField
                    label="Unit price"
                    type="number"
                    size="small"
                    value={entry.unitPrice}
                    onChange={(event) =>
                      setInvoiceFormLines((current) => ({
                        ...current,
                        [line.id ?? '']: { ...entry, unitPrice: event.target.value },
                      }))
                    }
                    sx={{ width: { xs: '100%', sm: 150 } }}
                  />
                </Stack>
              );
            })}
            <Typography variant="body2" color="text.secondary">
              Invoices above received quantity or outside the organization tolerance are flagged for
              Finance.
            </Typography>
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setInvoiceOpen(false)}>Cancel</Button>
          <Button variant="contained" disabled={recordInvoice.isPending} onClick={submitInvoice}>
            Record invoice
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={receiptOpen} onClose={() => setReceiptOpen(false)} fullWidth maxWidth="md">
        <DialogTitle>Record goods receipt</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            {receiptError && <Alert severity="error">{receiptError}</Alert>}
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
              <TextField
                label="Received date"
                type="date"
                value={receivedDate}
                onChange={(event) => setReceivedDate(event.target.value)}
                slotProps={{ inputLabel: { shrink: true } }}
                fullWidth
              />
              <TextField
                label="Delivery note reference"
                value={deliveryNote}
                onChange={(event) => setDeliveryNote(event.target.value)}
                fullWidth
              />
            </Stack>
            <TextField
              label="Notes"
              value={receiptNotes}
              onChange={(event) => setReceiptNotes(event.target.value)}
              multiline
              minRows={2}
            />
            <Divider />
            <Typography variant="subtitle2" color="text.secondary">
              Received quantities
            </Typography>
            {(data.lines ?? []).map((line) => {
              const remaining = Number(line.remainingQuantity ?? line.quantity ?? 0);
              return (
                <Stack
                  key={line.id}
                  direction={{ xs: 'column', sm: 'row' }}
                  spacing={2}
                  alignItems={{ xs: 'stretch', sm: 'center' }}
                >
                  <Box sx={{ flex: 1 }}>
                    <Typography>{line.description}</Typography>
                    <Typography variant="body2" color="text.secondary">
                      Ordered {line.quantity} {line.unit}; received {line.receivedQuantity ?? '0'};
                      remaining {line.remainingQuantity ?? line.quantity}
                    </Typography>
                  </Box>
                  <TextField
                    label="Receive now"
                    type="number"
                    size="small"
                    disabled={remaining <= 0}
                    value={receiptQuantities[line.id ?? ''] ?? ''}
                    onChange={(event) =>
                      setReceiptQuantities((current) => ({
                        ...current,
                        [line.id ?? '']: event.target.value,
                      }))
                    }
                    sx={{ width: { xs: '100%', sm: 180 } }}
                  />
                </Stack>
              );
            })}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setReceiptOpen(false)}>Cancel</Button>
          <Button variant="contained" disabled={receive.isPending} onClick={submitReceipt}>
            Record receipt
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={editOpen} onClose={() => setEditOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>Edit draft purchase order</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField
              label="Payment terms"
              value={paymentTerms}
              onChange={(event) => setPaymentTerms(event.target.value)}
            />
            <TextField
              label="Delivery date"
              type="date"
              value={deliveryDate}
              onChange={(event) => setDeliveryDate(event.target.value)}
              slotProps={{ inputLabel: { shrink: true } }}
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setEditOpen(false)}>Cancel</Button>
          <Button variant="contained" disabled={update.isPending} onClick={() => update.mutate()}>
            Save
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
}
