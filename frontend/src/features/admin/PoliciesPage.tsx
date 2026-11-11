import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  IconButton,
  MenuItem,
  Stack,
  TextField,
} from '@mui/material';
import { DataTable } from '@/components/DataTable';
import { toast } from '@/components/Toast';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import DeleteIcon from '@mui/icons-material/Delete';
import AddIcon from '@mui/icons-material/Add';
import { EmptyState, Loading, PageHeader } from '@/components/Page';
import { createPolicy, deletePolicy, listPolicies, updatePolicy } from './api';

interface StepForm {
  name: string;
  approverRole: string;
  slaHours: number;
}

export function PoliciesPage() {
  const queryClient = useQueryClient();
  const policies = useQuery({ queryKey: ['policies'], queryFn: listPolicies });
  const [open, setOpen] = useState(false);
  const [entityType, setEntityType] = useState('PURCHASE_REQUEST');
  const [name, setName] = useState('');
  const [amountMin, setAmountMin] = useState('0');
  const [amountMax, setAmountMax] = useState('');
  const [editingId, setEditingId] = useState<string | null>(null);
  const [deleting, setDeleting] = useState<{ id: string; name: string } | null>(null);
  const [steps, setSteps] = useState<StepForm[]>([
    { name: 'Approver', approverRole: 'APPROVER', slaHours: 24 },
  ]);

  const remove = useMutation({
    mutationFn: (id: string) => deletePolicy(id),
    onSuccess: () => {
      setDeleting(null);
      toast.success('Approval policy deleted.');
      void queryClient.invalidateQueries({ queryKey: ['policies'] });
    },
  });

  const create = useMutation({
    mutationFn: () => {
      const body = {
        name,
        entityType,
        amountMin: Number(amountMin),
        amountMax: amountMax ? Number(amountMax) : undefined,
        steps: steps.map((step) => ({
          name: step.name,
          approverType: 'ROLE' as const,
          approverRole: step.approverRole,
          slaHours: step.slaHours,
        })),
      };
      return editingId ? updatePolicy(editingId, body) : createPolicy(body);
    },
    onSuccess: () => {
      setOpen(false);
      setName('');
      setEditingId(null);
      toast.success(editingId ? 'New policy version created.' : 'Approval policy created.');
      void queryClient.invalidateQueries({ queryKey: ['policies'] });
    },
  });

  if (policies.isLoading) return <Loading />;
  const rows = policies.data?.content ?? [];

  return (
    <>
      <PageHeader
        title="Approval policies"
        actions={
          <Button variant="contained" onClick={() => setOpen(true)}>
            New policy version
          </Button>
        }
      />
      {rows.length === 0 ? (
        <EmptyState message="No approval policies configured." />
      ) : (
        <DataTable
          ariaLabel="Approval policies"
          rows={rows}
          getRowId={(policy) => policy.id!}
          columns={[
            {
              key: 'name',
              label: 'Name',
              render: (policy) => policy.name,
              sortValue: (policy) => policy.name ?? '',
            },
            {
              key: 'entity',
              label: 'Applies to',
              render: (policy) =>
                policy.entityType === 'PURCHASE_ORDER' ? 'Purchase orders' : 'Purchase requests',
              sortValue: (policy) => policy.entityType ?? '',
            },
            {
              key: 'band',
              label: 'Amount band',
              render: (policy) => `${policy.amountMin} – ${policy.amountMax ?? '∞'}`,
            },
            {
              key: 'version',
              label: 'Version',
              render: (policy) => policy.policyVersion,
              sortValue: (policy) => policy.policyVersion ?? 0,
            },
            { key: 'active', label: 'Active', render: (policy) => (policy.active ? 'Yes' : 'No') },
            {
              key: 'steps',
              label: 'Steps',
              render: (policy) =>
                (policy.steps ?? [])
                  .map((step) => `${step.approverRole ?? step.approverUserId}`)
                  .join(' → '),
            },
            {
              key: 'actions',
              label: 'Actions',
              align: 'right',
              render: (policy) => (
                <Stack direction="row" spacing={0.5} justifyContent="flex-end">
                  <Button
                    size="small"
                    onClick={() => {
                      setEditingId(policy.id!);
                      setEntityType(policy.entityType ?? 'PURCHASE_REQUEST');
                      setName(policy.name ?? '');
                      setAmountMin(String(policy.amountMin ?? 0));
                      setAmountMax(policy.amountMax == null ? '' : String(policy.amountMax));
                      setSteps(
                        (policy.steps ?? []).map((step) => ({
                          name: step.name ?? 'Approver',
                          approverRole: step.approverRole ?? 'APPROVER',
                          slaHours: step.slaHours ?? 24,
                        })),
                      );
                      setOpen(true);
                    }}
                  >
                    New version
                  </Button>
                  <Button
                    size="small"
                    color="error"
                    onClick={() => setDeleting({ id: policy.id!, name: policy.name ?? 'policy' })}
                  >
                    Delete
                  </Button>
                </Stack>
              ),
            },
          ]}
        />
      )}

      <ConfirmDialog
        open={Boolean(deleting)}
        title="Delete approval policy"
        message={`Delete "${deleting?.name ?? 'this policy'}"? Existing approval history is preserved, but future requests will no longer use this policy version.`}
        confirmLabel="Delete policy"
        busy={remove.isPending}
        onCancel={() => setDeleting(null)}
        onConfirm={() => deleting && remove.mutate(deleting.id)}
      />

      <Dialog open={open} onClose={() => setOpen(false)} fullWidth maxWidth="md">
        <DialogTitle>{editingId ? 'New policy version' : 'New approval policy'}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField
              select
              label="Applies to"
              value={entityType}
              onChange={(event) => setEntityType(event.target.value)}
            >
              <MenuItem value="PURCHASE_REQUEST">Purchase requests</MenuItem>
              <MenuItem value="PURCHASE_ORDER">Purchase orders</MenuItem>
            </TextField>
            <TextField
              label="Name"
              value={name}
              onChange={(event) => setName(event.target.value)}
            />
            <Stack direction="row" spacing={2}>
              <TextField
                label="Amount min"
                type="number"
                value={amountMin}
                onChange={(event) => setAmountMin(event.target.value)}
              />
              <TextField
                label="Amount max (optional)"
                type="number"
                value={amountMax}
                onChange={(event) => setAmountMax(event.target.value)}
              />
            </Stack>
            <Stack direction="row" justifyContent="space-between" alignItems="center">
              <strong>Steps</strong>
              <Button
                startIcon={<AddIcon />}
                onClick={() =>
                  setSteps([...steps, { name: 'Approver', approverRole: 'APPROVER', slaHours: 24 }])
                }
              >
                Add step
              </Button>
            </Stack>
            {steps.map((step, index) => (
              <Stack direction="row" spacing={1} key={`${index}-${step.approverRole}`}>
                <TextField
                  label="Step name"
                  value={step.name}
                  onChange={(event) => {
                    const next = [...steps];
                    next[index] = { ...step, name: event.target.value };
                    setSteps(next);
                  }}
                />
                <TextField
                  select
                  label="Role"
                  value={step.approverRole}
                  onChange={(event) => {
                    const next = [...steps];
                    next[index] = { ...step, approverRole: event.target.value };
                    setSteps(next);
                  }}
                >
                  {['APPROVER', 'FINANCE', 'ADMIN'].map((role) => (
                    <MenuItem key={role} value={role}>
                      {role}
                    </MenuItem>
                  ))}
                </TextField>
                <TextField
                  label="SLA hours"
                  type="number"
                  value={step.slaHours}
                  onChange={(event) => {
                    const next = [...steps];
                    next[index] = { ...step, slaHours: Number(event.target.value) };
                    setSteps(next);
                  }}
                />
                <IconButton
                  disabled={steps.length === 1}
                  onClick={() => setSteps(steps.filter((_, i) => i !== index))}
                >
                  <DeleteIcon />
                </IconButton>
              </Stack>
            ))}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setOpen(false)}>Cancel</Button>
          <Button
            variant="contained"
            disabled={!name || create.isPending}
            onClick={() => create.mutate()}
          >
            Create version
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
}
