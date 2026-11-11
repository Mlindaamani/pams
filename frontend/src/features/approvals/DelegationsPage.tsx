import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  MenuItem,
  Stack,
  TextField,
} from '@mui/material';
import { Can } from '@/auth/AbilityProvider';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { DataTable } from '@/components/DataTable';
import { EmptyState, Loading, PageHeader } from '@/components/Page';
import { toast } from '@/components/Toast';
import { dateTime } from '@/lib/format';
import { createDelegation, listMyDelegations, revokeDelegation } from './delegationsApi';
import { delegationCandidates } from '@/features/admin/usersApi';

function toLocalInput(date: Date) {
  const offset = date.getTimezoneOffset();
  return new Date(date.getTime() - offset * 60_000).toISOString().slice(0, 16);
}

export function DelegationsPage() {
  const queryClient = useQueryClient();
  const delegations = useQuery({ queryKey: ['delegations', 'mine'], queryFn: listMyDelegations });
  const candidates = useQuery({
    queryKey: ['users', 'delegation-candidates'],
    queryFn: delegationCandidates,
  });
  const [open, setOpen] = useState(false);
  const [delegateUserId, setDelegateUserId] = useState('');
  const [startsAt, setStartsAt] = useState(toLocalInput(new Date()));
  const [revoking, setRevoking] = useState<import('./delegationsApi').Delegation | null>(null);
  const [endsAt, setEndsAt] = useState(
    toLocalInput(new Date(Date.now() + 7 * 24 * 60 * 60 * 1000)),
  );

  const names = useMemo(() => {
    const map = new Map<string, string>();
    (candidates.data ?? []).forEach((candidate) =>
      map.set(candidate.id!, candidate.displayName ?? ''),
    );
    return map;
  }, [candidates.data]);

  const invalidate = () =>
    void queryClient.invalidateQueries({ queryKey: ['delegations', 'mine'] });
  const create = useMutation({
    mutationFn: () =>
      createDelegation({
        delegateUserId,
        startsAt: new Date(startsAt).toISOString(),
        endsAt: new Date(endsAt).toISOString(),
      }),
    onSuccess: () => {
      toast.success('Delegation created.');
      setOpen(false);
      setDelegateUserId('');
      invalidate();
    },
  });
  const revoke = useMutation({
    mutationFn: (id: string) => revokeDelegation(id),
    onSuccess: () => {
      toast.success('Delegation revoked.');
      invalidate();
    },
  });

  if (delegations.isLoading) return <Loading />;
  const rows = delegations.data ?? [];

  return (
    <>
      <PageHeader
        title="Delegations"
        actions={
          <Can I="create" a="Delegation">
            <Button variant="contained" onClick={() => setOpen(true)}>
              New delegation
            </Button>
          </Can>
        }
      />
      {rows.length === 0 ? (
        <EmptyState message="You have no delegations." />
      ) : (
        <DataTable
          ariaLabel="Delegations"
          rows={rows}
          getRowId={(delegation) => delegation.id!}
          columns={[
            {
              key: 'delegate',
              label: 'Delegate',
              render: (delegation) =>
                names.get(delegation.delegateUserId ?? '') ?? delegation.delegateUserId,
            },
            {
              key: 'start',
              label: 'Starts',
              render: (delegation) => dateTime(delegation.startsAt),
            },
            { key: 'end', label: 'Ends', render: (delegation) => dateTime(delegation.endsAt) },
            {
              key: 'active',
              label: 'Status',
              render: (delegation) => (delegation.active ? 'Active' : 'Inactive'),
            },
            {
              key: 'actions',
              label: 'Actions',
              align: 'right',
              render: (delegation) => (
                <Can I="create" a="Delegation">
                  <Button
                    size="small"
                    color="error"
                    disabled={!delegation.active}
                    onClick={() => setRevoking(delegation)}
                  >
                    Revoke
                  </Button>
                </Can>
              ),
            },
          ]}
        />
      )}

      <ConfirmDialog
        open={Boolean(revoking)}
        title="Revoke delegation"
        message="Revoke this delegation immediately?"
        confirmLabel="Revoke"
        busy={revoke.isPending}
        onCancel={() => setRevoking(null)}
        onConfirm={() => {
          if (revoking) revoke.mutate(revoking.id!, { onSuccess: () => setRevoking(null) });
        }}
      />

      <Dialog open={open} onClose={() => setOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>New delegation</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField
              select
              label="Delegate to"
              value={delegateUserId}
              onChange={(event) => setDelegateUserId(event.target.value)}
              required
            >
              {(candidates.data ?? []).map((candidate) => (
                <MenuItem key={candidate.id} value={candidate.id}>
                  {candidate.displayName}
                </MenuItem>
              ))}
            </TextField>
            <TextField
              label="Starts at"
              type="datetime-local"
              value={startsAt}
              onChange={(event) => setStartsAt(event.target.value)}
              slotProps={{ inputLabel: { shrink: true } }}
            />
            <TextField
              label="Ends at"
              type="datetime-local"
              value={endsAt}
              onChange={(event) => setEndsAt(event.target.value)}
              slotProps={{ inputLabel: { shrink: true } }}
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setOpen(false)}>Cancel</Button>
          <Button
            variant="contained"
            disabled={!delegateUserId || create.isPending}
            onClick={() => create.mutate()}
          >
            Create
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
}
