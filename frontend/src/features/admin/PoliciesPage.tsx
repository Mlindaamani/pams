import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Box,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  IconButton,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import { DataTable } from '@/components/DataTable';
import { toast } from '@/components/Toast';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import DeleteIcon from '@mui/icons-material/Delete';
import AddIcon from '@mui/icons-material/Add';
import { EmptyState, Loading, PageHeader } from '@/components/Page';
import { listDepartments } from '@/features/reference/api';
import { createPolicy, deletePolicy, listPolicies, updatePolicy } from './api';

type ConditionType = 'ALWAYS' | 'BUDGET_EXCEPTION' | 'DEPARTMENT';

interface StepForm {
  name: string;
  approverRole: string;
  slaHours: number;
  stage: number;
  conditionType: ConditionType;
  conditionDepartmentId: string;
}

export function PoliciesPage() {
  const queryClient = useQueryClient();
  const policies = useQuery({ queryKey: ['policies'], queryFn: listPolicies });
  const departments = useQuery({ queryKey: ['departments'], queryFn: listDepartments });
  const [open, setOpen] = useState(false);
  const [entityType, setEntityType] = useState('PURCHASE_REQUEST');
  const [name, setName] = useState('');
  const [amountMin, setAmountMin] = useState('0');
  const [amountMax, setAmountMax] = useState('');
  const [editingId, setEditingId] = useState<string | null>(null);
  const [deleting, setDeleting] = useState<{ id: string; name: string } | null>(null);
  const [steps, setSteps] = useState<StepForm[]>([
    {
      name: 'Approver',
      approverRole: 'APPROVER',
      slaHours: 24,
      stage: 1,
      conditionType: 'ALWAYS',
      conditionDepartmentId: '',
    },
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
          stage: step.stage,
          conditionType: step.conditionType,
          conditionDepartmentId:
            step.conditionType === 'DEPARTMENT'
              ? step.conditionDepartmentId || undefined
              : undefined,
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
              label: 'Stages',
              render: (policy) => {
                const stages = new Map<number, string[]>();
                (policy.steps ?? []).forEach((step) => {
                  const stage = step.sequence ?? 1;
                  stages.set(stage, [
                    ...(stages.get(stage) ?? []),
                    step.approverRole ?? step.approverUserId ?? step.name ?? 'Approver',
                  ]);
                });
                return [...stages.entries()]
                  .sort(([left], [right]) => left - right)
                  .map(([stage, approvers]) => `Stage ${stage}: ${approvers.join(' + ')}`)
                  .join(' → ');
              },
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
                          stage: step.sequence ?? 1,
                          conditionType: step.conditionType ?? 'ALWAYS',
                          conditionDepartmentId: step.conditionDepartmentId ?? '',
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
              <Box>
                <strong>Stages and approvers</strong>
                <Typography variant="caption" color="text.secondary" display="block">
                  Approvers in the same stage act in parallel. Later stages start only after every
                  current stage step is approved.
                </Typography>
              </Box>
              <Stack direction="row" spacing={1}>
                <Button
                  startIcon={<AddIcon />}
                  onClick={() => {
                    const stage = steps.at(-1)?.stage ?? 1;
                    setSteps([
                      ...steps,
                      {
                        name: 'Approver',
                        approverRole: 'APPROVER',
                        slaHours: 24,
                        stage,
                        conditionType: 'ALWAYS',
                        conditionDepartmentId: '',
                      },
                    ]);
                  }}
                >
                  Add approver
                </Button>
                <Button
                  startIcon={<AddIcon />}
                  onClick={() => {
                    const stage = Math.max(0, ...steps.map((step) => step.stage)) + 1;
                    setSteps([
                      ...steps,
                      {
                        name: 'Approver',
                        approverRole: 'APPROVER',
                        slaHours: 24,
                        stage,
                        conditionType: 'ALWAYS',
                        conditionDepartmentId: '',
                      },
                    ]);
                  }}
                >
                  Add stage
                </Button>
              </Stack>
            </Stack>
            <WorkflowPreview steps={steps} departments={departments.data?.content ?? []} />
            {steps.map((step, index) => (
              <Stack
                direction="row"
                spacing={1}
                flexWrap="wrap"
                useFlexGap
                key={`${index}-${step.approverRole}-${index}`}
              >
                <TextField
                  label="Stage"
                  type="number"
                  value={step.stage}
                  sx={{ width: 90 }}
                  slotProps={{ htmlInput: { min: 1 } }}
                  onChange={(event) => {
                    const next = [...steps];
                    next[index] = { ...step, stage: Math.max(1, Number(event.target.value)) };
                    setSteps(next);
                  }}
                />
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
                <TextField
                  select
                  label="Condition"
                  value={step.conditionType}
                  sx={{ minWidth: 170 }}
                  onChange={(event) => {
                    const next = [...steps];
                    next[index] = {
                      ...step,
                      conditionType: event.target.value as ConditionType,
                      conditionDepartmentId:
                        event.target.value === 'DEPARTMENT' ? step.conditionDepartmentId : '',
                    };
                    setSteps(next);
                  }}
                >
                  <MenuItem value="ALWAYS">Always</MenuItem>
                  <MenuItem value="BUDGET_EXCEPTION">Budget exception only</MenuItem>
                  <MenuItem value="DEPARTMENT">Specific department</MenuItem>
                </TextField>
                {step.conditionType === 'DEPARTMENT' && (
                  <TextField
                    select
                    label="Department"
                    value={step.conditionDepartmentId}
                    sx={{ minWidth: 190 }}
                    onChange={(event) => {
                      const next = [...steps];
                      next[index] = { ...step, conditionDepartmentId: event.target.value };
                      setSteps(next);
                    }}
                  >
                    {(departments.data?.content ?? []).map((department) => (
                      <MenuItem key={department.id} value={department.id}>
                        {department.name}
                      </MenuItem>
                    ))}
                  </TextField>
                )}
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

function WorkflowPreview({
  steps,
  departments,
}: {
  steps: StepForm[];
  departments: { id?: string; name?: string }[];
}) {
  const stages = new Map<number, StepForm[]>();
  steps.forEach((step) => stages.set(step.stage, [...(stages.get(step.stage) ?? []), step]));
  return (
    <Box sx={{ p: 2, bgcolor: 'grey.50', border: 1, borderColor: 'divider', borderRadius: 2 }}>
      <Typography variant="subtitle2" sx={{ mb: 1 }}>
        Workflow preview
      </Typography>
      <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap alignItems="center">
        {[...stages.entries()]
          .sort(([left], [right]) => left - right)
          .map(([stage, stageSteps], index) => (
            <Stack key={stage} direction="row" spacing={1} alignItems="center">
              {index > 0 && <Typography color="text.secondary">→</Typography>}
              <Box sx={{ p: 1.5, minWidth: 180, bgcolor: 'background.paper', borderRadius: 2 }}>
                <Typography variant="overline" color="text.secondary">
                  Stage {stage}
                </Typography>
                <Stack spacing={0.5}>
                  {stageSteps.map((step, stepIndex) => (
                    <Chip
                      key={`${step.approverRole}-${stepIndex}`}
                      size="small"
                      label={`${step.approverRole} · ${conditionLabel(step, departments)}`}
                      variant={step.conditionType === 'ALWAYS' ? 'outlined' : 'filled'}
                    />
                  ))}
                </Stack>
                {stageSteps.length > 1 && (
                  <Typography variant="caption" color="text.secondary">
                    Parallel
                  </Typography>
                )}
              </Box>
            </Stack>
          ))}
      </Stack>
    </Box>
  );
}

function conditionLabel(step: StepForm, departments: { id?: string; name?: string }[]): string {
  if (step.conditionType === 'BUDGET_EXCEPTION') return 'budget exception';
  if (step.conditionType === 'DEPARTMENT') {
    return (
      departments.find((department) => department.id === step.conditionDepartmentId)?.name ??
      'department'
    );
  }
  return 'always';
}
