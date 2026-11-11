import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link as RouterLink } from 'react-router-dom';
import {
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import { DataTable } from '@/components/DataTable';
import { toast } from '@/components/Toast';
import { EmptyState, Loading, PageHeader } from '@/components/Page';
import { money } from '@/lib/format';
import { approvalInbox, approve, bulkApprove, reject, requestChanges } from './api';

type Action = 'approve' | 'reject' | 'request-changes';

export function ApprovalInboxPage() {
  const queryClient = useQueryClient();
  const inbox = useQuery({ queryKey: ['approvals', 'inbox'], queryFn: approvalInbox });
  const [selected, setSelected] = useState<{ id: string; action: Action } | null>(null);
  const [comment, setComment] = useState('');
  const [bulkIds, setBulkIds] = useState<string[] | null>(null);
  const [bulkComment, setBulkComment] = useState('');

  const action = useMutation({
    mutationFn: async (input: { id: string; action: Action; comment: string }) => {
      if (input.action === 'approve') return approve(input.id, input.comment || undefined);
      if (input.action === 'reject') return reject(input.id, input.comment);
      return requestChanges(input.id, input.comment);
    },
    onSuccess: (_instance, variables) => {
      toast.success(
        variables.action === 'approve'
          ? 'Approval recorded.'
          : variables.action === 'reject'
            ? 'Request rejected.'
            : 'Changes requested.',
      );
      setSelected(null);
      setComment('');
      void queryClient.invalidateQueries({ queryKey: ['approvals'] });
      void queryClient.invalidateQueries({ queryKey: ['requests'] });
      void queryClient.invalidateQueries({ queryKey: ['notifications'] });
    },
  });

  const bulk = useMutation({
    mutationFn: () => bulkApprove(bulkIds ?? [], bulkComment || undefined),
    onSuccess: (count) => {
      toast.success(`${count} approval(s) recorded.`);
      setBulkIds(null);
      setBulkComment('');
      void queryClient.invalidateQueries({ queryKey: ['approvals'] });
      void queryClient.invalidateQueries({ queryKey: ['requests'] });
    },
  });

  if (inbox.isLoading) return <Loading />;
  const steps = inbox.data ?? [];
  const requiresComment = selected?.action !== 'approve';

  return (
    <>
      <PageHeader title="Approval inbox" />
      {steps.length === 0 ? (
        <EmptyState message="Nothing is waiting for your approval." />
      ) : (
        <DataTable
          ariaLabel="Approval inbox"
          rows={steps}
          getRowId={(step) => step.id!}
          bulkActions={(ids) => (
            <Button size="small" variant="contained" onClick={() => setBulkIds(ids)}>
              Approve selected
            </Button>
          )}
          columns={[
            {
              key: 'request',
              label: 'Request',
              render: (step) => (
                <Typography
                  component={RouterLink}
                  to={`/requests/${step.requestId}`}
                  color="primary"
                >
                  {step.requestId}
                </Typography>
              ),
            },
            {
              key: 'step',
              label: 'Step',
              render: (step) => `${step.sequence}. ${step.name}`,
            },
            { key: 'total', label: 'Total', render: (step) => money(step.total) },
            {
              key: 'due',
              label: 'Due',
              render: (step) => (step.slaDueAt ? new Date(step.slaDueAt).toLocaleString() : '—'),
            },
            {
              key: 'actions',
              label: 'Actions',
              align: 'right',
              render: (step) => (
                <Stack direction="row" spacing={1} justifyContent="flex-end">
                  <Button
                    size="small"
                    variant="contained"
                    onClick={() => setSelected({ id: step.id!, action: 'approve' })}
                  >
                    Approve
                  </Button>
                  <Button
                    size="small"
                    color="warning"
                    onClick={() => setSelected({ id: step.id!, action: 'request-changes' })}
                  >
                    Changes
                  </Button>
                  <Button
                    size="small"
                    color="error"
                    onClick={() => setSelected({ id: step.id!, action: 'reject' })}
                  >
                    Reject
                  </Button>
                </Stack>
              ),
            },
          ]}
        />
      )}

      <Dialog open={Boolean(bulkIds)} onClose={() => setBulkIds(null)} fullWidth maxWidth="sm">
        <DialogTitle>Approve selected steps</DialogTitle>
        <DialogContent>
          <TextField
            autoFocus
            fullWidth
            multiline
            minRows={2}
            label="Comment (optional)"
            value={bulkComment}
            onChange={(event) => setBulkComment(event.target.value)}
            sx={{ mt: 1 }}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setBulkIds(null)}>Cancel</Button>
          <Button variant="contained" disabled={bulk.isPending} onClick={() => bulk.mutate()}>
            Approve {bulkIds?.length ?? 0}
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={Boolean(selected)} onClose={() => setSelected(null)} fullWidth maxWidth="sm">
        <DialogTitle>
          {selected?.action === 'approve'
            ? 'Approve step'
            : selected?.action === 'reject'
              ? 'Reject request'
              : 'Request changes'}
        </DialogTitle>
        <DialogContent>
          <TextField
            autoFocus
            fullWidth
            multiline
            minRows={3}
            label={requiresComment ? 'Comment (required)' : 'Comment (optional)'}
            value={comment}
            onChange={(event) => setComment(event.target.value)}
            sx={{ mt: 1 }}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setSelected(null)}>Cancel</Button>
          <Button
            variant="contained"
            disabled={action.isPending || (requiresComment && comment.trim().length === 0)}
            onClick={() =>
              selected && action.mutate({ id: selected.id, action: selected.action, comment })
            }
          >
            Confirm
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
}
