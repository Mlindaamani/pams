import { useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link as RouterLink, useNavigate, useParams } from 'react-router-dom';
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
  Grid,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import { DataTable } from '@/components/DataTable';
import { toast } from '@/components/Toast';
import { useAbility } from '@/auth/AbilityProvider';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { Loading } from '@/components/Page';
import { money, dateOnly, downloadBlob } from '@/lib/format';
import {
  cancelRequest,
  cloneRequest,
  deleteRequest,
  downloadAttachment,
  deleteAttachment,
  getRequest,
  getRequestActions,
  getTimeline,
  listAttachments,
  submitRequest,
  uploadAttachment,
} from './api';
import { createPurchaseOrder } from '@/features/purchaseorders/api';
import { listVendors } from '@/features/vendors/api';
import { auditPage } from '@/features/audit/api';
import { ApprovalProgress } from './ApprovalProgress';
import { TransitionLog } from './TransitionLog';
import {
  approve as approveStep,
  reject as rejectStep,
  requestChanges as requestChangesStep,
} from '@/features/approvals/api';

export function RequestDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const ability = useAbility();
  const fileInput = useRef<HTMLInputElement>(null);
  const [poOpen, setPoOpen] = useState(false);
  const [confirm, setConfirm] = useState<{
    title: string;
    message: string;
    label: string;
    color: 'error' | 'warning';
    onConfirm: () => void;
  } | null>(null);
  const [vendorId, setVendorId] = useState('');
  const [paymentTerms, setPaymentTerms] = useState('Net 30');
  const [deliveryDate, setDeliveryDate] = useState('');
  const [approvalDialog, setApprovalDialog] = useState<
    'approve' | 'reject' | 'request-changes' | null
  >(null);
  const [approvalComment, setApprovalComment] = useState('');

  const request = useQuery({ queryKey: ['request', id], queryFn: () => getRequest(id!) });
  const timeline = useQuery({
    queryKey: ['request', id, 'approval'],
    queryFn: () => getTimeline(id!),
    enabled: Boolean(request.data && request.data.status !== 'DRAFT'),
    retry: false,
  });
  const actionsQuery = useQuery({
    queryKey: ['request', id, 'actions'],
    queryFn: () => getRequestActions(id!),
    enabled: Boolean(id),
  });
  const attachments = useQuery({
    queryKey: ['request', id, 'attachments'],
    queryFn: () => listAttachments(id!),
  });
  const audit = useQuery({
    queryKey: ['request', id, 'audit'],
    queryFn: () => auditPage({ aggregateId: id, limit: 25 }),
    enabled: ability.can('read', 'Audit'),
  });
  const vendors = useQuery({
    queryKey: ['vendors', 'po-dialog'],
    queryFn: () => listVendors({ size: 100 }),
    enabled: poOpen,
  });

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ['request', id] });
    void queryClient.invalidateQueries({ queryKey: ['requests'] });
    void queryClient.invalidateQueries({ queryKey: ['request', id, 'approval'] });
    void queryClient.invalidateQueries({ queryKey: ['request', id, 'actions'] });
    void queryClient.invalidateQueries({ queryKey: ['request', id, 'attachments'] });
  };

  const submit = useMutation({
    mutationFn: () => submitRequest(id!, request.data?.version ?? 0),
    onSuccess: () => {
      toast.success('Request submitted for approval.');
      invalidate();
    },
  });
  const cancel = useMutation({
    mutationFn: () => cancelRequest(id!, request.data?.version ?? 0),
    onSuccess: () => {
      toast.success('Request cancelled.');
      invalidate();
    },
  });
  const reorder = useMutation({
    mutationFn: () => cloneRequest(id!),
    onSuccess: (created) => {
      toast.success('Draft request created from this request.');
      navigate(`/requests/${created.id}`);
    },
  });
  const remove = useMutation({
    mutationFn: () => deleteRequest(id!, request.data?.version ?? 0),
    onSuccess: () => {
      toast.success('Draft deleted.');
      void queryClient.invalidateQueries({ queryKey: ['requests'] });
      navigate('/requests');
    },
  });
  const upload = useMutation({
    mutationFn: (file: File) => uploadAttachment(id!, file),
    onSuccess: () => {
      toast.success('Attachment uploaded.');
      invalidate();
    },
  });
  const removeAttachment = useMutation({
    mutationFn: (attachmentId: string) => deleteAttachment(id!, attachmentId),
    onSuccess: invalidate,
  });
  const approval = useMutation({
    mutationFn: async (action: 'approve' | 'reject' | 'request-changes') => {
      const stepId = actionsQuery.data?.approvalStepId;
      if (!stepId) throw new Error('No active approval step');
      if (action === 'approve') return approveStep(stepId, approvalComment || undefined);
      if (action === 'reject') return rejectStep(stepId, approvalComment);
      return requestChangesStep(stepId, approvalComment);
    },
    onSuccess: (_result, action) => {
      toast.success(
        action === 'approve'
          ? 'Approval recorded.'
          : action === 'reject'
            ? 'Request rejected.'
            : 'Changes requested.',
      );
      setApprovalDialog(null);
      setApprovalComment('');
      invalidate();
      void queryClient.invalidateQueries({ queryKey: ['approvals'] });
      void queryClient.invalidateQueries({ queryKey: ['notifications'] });
    },
  });

  const convert = useMutation({
    mutationFn: () =>
      createPurchaseOrder(id!, {
        vendorId,
        paymentTerms,
        deliveryDate: deliveryDate || undefined,
      }),
    onSuccess: () => {
      toast.success('Purchase order created.');
      setPoOpen(false);
      invalidate();
      void queryClient.invalidateQueries({ queryKey: ['purchase-orders'] });
      navigate('/purchase-orders');
    },
  });

  if (request.isLoading) return <Loading />;
  if (request.error || !request.data) {
    return <Alert severity="error">This request could not be loaded.</Alert>;
  }

  const data = request.data;
  const allowed = new Set(actionsQuery.data?.actions ?? []);
  const editable = allowed.has('EDIT');
  const canSubmit = allowed.has('SUBMIT');

  return (
    <>
      <Box
        component="section"
        aria-label="Request header"
        sx={{
          mb: 2,
          p: { xs: 2, md: 2.5 },
          border: 1,
          borderColor: 'divider',
          borderRadius: 2,
          bgcolor: 'background.paper',
        }}
      >
        <Stack
          direction={{ xs: 'column', lg: 'row' }}
          spacing={2}
          alignItems={{ xs: 'stretch', lg: 'center' }}
        >
          <Box sx={{ minWidth: { lg: 240 }, maxWidth: { lg: 380 } }}>
            <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
              <Typography variant="h5" component="h1">
                {data.title ?? 'Purchase request'}
              </Typography>
              <Chip
                size="small"
                label={data.status?.replaceAll('_', ' ') ?? 'DRAFT'}
                color={requestStatusColor(data.status)}
                variant={data.status === 'DRAFT' ? 'outlined' : 'filled'}
              />
            </Stack>
            <Typography variant="caption" color="text.secondary">
              {data.requestNumber} · {data.requesterName}
            </Typography>
          </Box>

          <Box sx={{ flex: '1 1 260px', minWidth: 0 }}>
            {timeline.data ? (
              <ApprovalProgress timeline={timeline.data} />
            ) : (
              <Typography variant="body2" color="text.secondary">
                Approval starts after submission.
              </Typography>
            )}
          </Box>

          <Stack
            direction="row"
            spacing={1}
            flexWrap="wrap"
            useFlexGap
            justifyContent={{ lg: 'flex-end' }}
            sx={{ flexShrink: 0, maxWidth: { lg: 560 } }}
          >
            {editable && (
              <Button
                component={RouterLink}
                to={`/requests/${data.id}/edit`}
                variant="outlined"
                size="small"
              >
                Edit
              </Button>
            )}
            {canSubmit && (
              <Button
                variant="contained"
                size="small"
                onClick={() => submit.mutate()}
                disabled={submit.isPending}
              >
                Submit
              </Button>
            )}
            {allowed.has('CANCEL') && (
              <Button
                color="warning"
                size="small"
                onClick={() =>
                  setConfirm({
                    title: 'Cancel request',
                    message: 'Cancel this request and release any budget reservation?',
                    label: 'Cancel request',
                    color: 'warning',
                    onConfirm: () => cancel.mutate(),
                  })
                }
                disabled={cancel.isPending}
              >
                Cancel
              </Button>
            )}
            {allowed.has('DELETE') && (
              <Button
                color="error"
                size="small"
                onClick={() =>
                  setConfirm({
                    title: 'Delete draft',
                    message: 'Delete this draft? This cannot be undone.',
                    label: 'Delete draft',
                    color: 'error',
                    onConfirm: () => remove.mutate(),
                  })
                }
                disabled={remove.isPending}
              >
                Delete
              </Button>
            )}
            {ability.can('create', 'PurchaseRequest') &&
              data.status !== 'DRAFT' &&
              data.status !== 'CANCELLED' && (
                <Button
                  size="small"
                  variant="outlined"
                  onClick={() => reorder.mutate()}
                  disabled={reorder.isPending}
                >
                  Reorder
                </Button>
              )}
            {allowed.has('CREATE_PO') && (
              <Button
                variant="contained"
                color="secondary"
                size="small"
                onClick={() => setPoOpen(true)}
              >
                Create PO
              </Button>
            )}
            {allowed.has('APPROVE') && (
              <Button
                size="small"
                variant="contained"
                onClick={() => {
                  setApprovalComment('');
                  setApprovalDialog('approve');
                }}
              >
                Approve
              </Button>
            )}
            {allowed.has('REQUEST_CHANGES') && (
              <Button
                size="small"
                color="warning"
                variant="outlined"
                onClick={() => {
                  setApprovalComment('');
                  setApprovalDialog('request-changes');
                }}
              >
                Request changes
              </Button>
            )}
            {allowed.has('REJECT') && (
              <Button
                size="small"
                color="error"
                variant="outlined"
                onClick={() => {
                  setApprovalComment('');
                  setApprovalDialog('reject');
                }}
              >
                Reject
              </Button>
            )}
          </Stack>
        </Stack>
      </Box>

      <Grid container spacing={2}>
        <Grid size={{ xs: 12, md: ability.can('read', 'Audit') ? 8 : 12 }}>
          <Card>
            <CardContent>
              <Stack
                direction={{ xs: 'column', sm: 'row' }}
                justifyContent="space-between"
                spacing={2}
              >
                <Stack>
                  <Typography color="text.secondary">Status</Typography>
                  <Typography variant="h6">{data.status?.replaceAll('_', ' ')}</Typography>
                </Stack>
                <Stack>
                  <Typography color="text.secondary">Total</Typography>
                  <Typography variant="h6">{money(data.total)}</Typography>
                </Stack>
                <Stack>
                  <Typography color="text.secondary">Needed by</Typography>
                  <Typography>{dateOnly(data.neededBy)}</Typography>
                </Stack>
                <Stack>
                  <Typography color="text.secondary">Requester</Typography>
                  <Typography>{data.requesterName}</Typography>
                </Stack>
              </Stack>
              <Typography sx={{ mt: 2 }} color="text.secondary">
                Justification
              </Typography>
              <Typography>{data.justification}</Typography>
              <Typography sx={{ mt: 2 }} variant="body2" color="text.secondary">
                Policy version: {data.approvalPolicyVersion ?? '—'} · Budget reserved:{' '}
                {data.budgetReserved ? 'yes' : 'no'}
                {data.budgetException ? ' · Budget exception' : ''}
              </Typography>
            </CardContent>
          </Card>

          <Card sx={{ mt: 2 }}>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Lines
              </Typography>
              <DataTable
                ariaLabel="Purchase request lines"
                rows={data.lines ?? []}
                getRowId={(line) => line.id ?? `${line.description}-${line.quantity}`}
                columns={[
                  { key: 'description', label: 'Description', render: (line) => line.description },
                  { key: 'qty', label: 'Qty', render: (line) => line.quantity },
                  { key: 'unit', label: 'Unit', render: (line) => line.unit },
                  { key: 'price', label: 'Unit price', render: (line) => money(line.unitPrice) },
                  { key: 'total', label: 'Line total', render: (line) => money(line.lineTotal) },
                ]}
              />
            </CardContent>
          </Card>

          <Card sx={{ mt: 2 }}>
            <CardContent>
              <Stack direction="row" justifyContent="space-between" alignItems="center">
                <Typography variant="h6">Attachments</Typography>
                {editable && (
                  <>
                    <input
                      ref={fileInput}
                      type="file"
                      hidden
                      onChange={(event) => {
                        const file = event.target.files?.[0];
                        if (file) upload.mutate(file);
                        event.target.value = '';
                      }}
                    />
                    <Button size="small" onClick={() => fileInput.current?.click()}>
                      Upload
                    </Button>
                  </>
                )}
              </Stack>
              <Stack spacing={1} sx={{ mt: 1 }}>
                {(attachments.data ?? []).length === 0 && (
                  <Typography color="text.secondary">No attachments.</Typography>
                )}
                {(attachments.data ?? []).map((attachment) => (
                  <Stack
                    key={attachment.id}
                    direction="row"
                    justifyContent="space-between"
                    alignItems="center"
                  >
                    <Button
                      size="small"
                      onClick={async () => {
                        const blob = await downloadAttachment(id!, attachment.id!);
                        downloadBlob(blob, attachment.fileName ?? 'attachment');
                      }}
                    >
                      {attachment.fileName}
                    </Button>
                    {editable && (
                      <Button
                        size="small"
                        color="error"
                        onClick={() => removeAttachment.mutate(attachment.id!)}
                      >
                        Delete
                      </Button>
                    )}
                  </Stack>
                ))}
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid size={{ xs: 12, md: 4 }}>
          {ability.can('read', 'Audit') ? (
            <TransitionLog events={audit.data?.content ?? []} loading={audit.isLoading} />
          ) : null}
        </Grid>
      </Grid>

      <ConfirmDialog
        open={Boolean(confirm)}
        title={confirm?.title ?? ''}
        message={confirm?.message ?? ''}
        confirmLabel={confirm?.label}
        color={confirm?.color}
        onCancel={() => setConfirm(null)}
        onConfirm={() => {
          confirm?.onConfirm();
          setConfirm(null);
        }}
      />

      <Dialog
        open={Boolean(approvalDialog)}
        onClose={() => setApprovalDialog(null)}
        fullWidth
        maxWidth="sm"
      >
        <DialogTitle>
          {approvalDialog === 'approve'
            ? 'Approve request'
            : approvalDialog === 'reject'
              ? 'Reject request'
              : 'Request changes'}
        </DialogTitle>
        <DialogContent>
          <TextField
            autoFocus
            fullWidth
            multiline
            minRows={3}
            label={approvalDialog === 'approve' ? 'Comment (optional)' : 'Comment (required)'}
            value={approvalComment}
            onChange={(event) => setApprovalComment(event.target.value)}
            sx={{ mt: 1 }}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setApprovalDialog(null)}>Cancel</Button>
          <Button
            variant="contained"
            disabled={
              approval.isPending ||
              (approvalDialog !== 'approve' && approvalComment.trim().length === 0)
            }
            onClick={() => approvalDialog && approval.mutate(approvalDialog)}
          >
            Confirm
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={poOpen} onClose={() => setPoOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>Create purchase order</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField
              select
              label="Vendor"
              value={vendorId}
              onChange={(event) => setVendorId(event.target.value)}
            >
              {(vendors.data?.content ?? [])
                .filter((vendor) => vendor.status === 'ACTIVE')
                .map((vendor) => (
                  <MenuItem key={vendor.id} value={vendor.id}>
                    {vendor.name}
                  </MenuItem>
                ))}
            </TextField>
            <TextField
              label="Payment terms"
              value={paymentTerms}
              onChange={(event) => setPaymentTerms(event.target.value)}
            />
            <TextField
              label="Delivery date"
              type="date"
              slotProps={{ inputLabel: { shrink: true } }}
              value={deliveryDate}
              onChange={(event) => setDeliveryDate(event.target.value)}
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setPoOpen(false)}>Cancel</Button>
          <Button
            variant="contained"
            disabled={!vendorId || convert.isPending}
            onClick={() => convert.mutate()}
          >
            Create
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
}

function requestStatusColor(status?: string): 'default' | 'info' | 'success' | 'warning' | 'error' {
  switch (status) {
    case 'APPROVED':
    case 'CONVERTED_TO_PO':
      return 'success';
    case 'SUBMITTED':
    case 'IN_APPROVAL':
      return 'info';
    case 'CHANGES_REQUESTED':
      return 'warning';
    case 'REJECTED':
    case 'CANCELLED':
      return 'error';
    default:
      return 'default';
  }
}
