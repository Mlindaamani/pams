import { useEffect, useState } from 'react';
import { Link as RouterLink, useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Grid,
  IconButton,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import DeleteIcon from '@mui/icons-material/Delete';
import { toast } from '@/components/Toast';
import { problemMessage } from '@/api/client';
import { Loading, PageHeader } from '@/components/Page';
import { listDepartments } from '@/features/reference/api';
import { listUsers } from './usersApi';
import { createPolicy, getPolicy, updatePolicy } from './api';

type ConditionType = 'ALWAYS' | 'BUDGET_EXCEPTION' | 'DEPARTMENT';
type ApproverType = 'ROLE' | 'REQUESTER_MANAGER' | 'USER';

interface StepForm {
  name: string;
  approverType: ApproverType;
  approverRole: string;
  approverUserId: string;
  slaHours: number;
  stage: number;
  minApprovals: string;
  conditionType: ConditionType;
  conditionDepartmentId: string;
}

const initialSteps: StepForm[] = [
  {
    name: 'Approver',
    approverType: 'ROLE',
    approverRole: 'APPROVER',
    approverUserId: '',
    slaHours: 24,
    stage: 1,
    minApprovals: '',
    conditionType: 'ALWAYS',
    conditionDepartmentId: '',
  },
];

export function WorkflowPolicyFormPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const policy = useQuery({
    queryKey: ['policy', id],
    queryFn: () => getPolicy(id!),
    enabled: Boolean(id),
  });
  const departments = useQuery({ queryKey: ['departments'], queryFn: listDepartments });
  const users = useQuery({
    queryKey: ['users', 'workflow-policy'],
    queryFn: () => listUsers({ active: true, size: 100 }),
    enabled: !id || Boolean(policy.data),
  });
  const [entityType, setEntityType] = useState('PURCHASE_REQUEST');
  const [name, setName] = useState('');
  const [amountMin, setAmountMin] = useState('0');
  const [amountMax, setAmountMax] = useState('');
  const [steps, setSteps] = useState<StepForm[]>(initialSteps);
  const [loaded, setLoaded] = useState(!id);

  useEffect(() => {
    if (!policy.data || loaded) return;
    setEntityType(policy.data.entityType ?? 'PURCHASE_REQUEST');
    setName(policy.data.name ?? '');
    setAmountMin(String(policy.data.amountMin ?? 0));
    setAmountMax(policy.data.amountMax == null ? '' : String(policy.data.amountMax));
    setSteps(
      (policy.data.steps ?? []).map((step) => ({
        name: step.name ?? 'Approver',
        approverType: step.approverType ?? 'ROLE',
        approverRole: step.approverRole ?? '',
        approverUserId: step.approverUserId ?? '',
        slaHours: step.slaHours ?? 24,
        stage: step.sequence ?? 1,
        minApprovals: step.minApprovals == null ? '' : String(step.minApprovals),
        conditionType: step.conditionType ?? 'ALWAYS',
        conditionDepartmentId: step.conditionDepartmentId ?? '',
      })),
    );
    setLoaded(true);
  }, [policy.data, loaded]);

  const save = useMutation({
    mutationFn: () => {
      const body = {
        name,
        entityType,
        amountMin: Number(amountMin),
        amountMax: amountMax ? Number(amountMax) : undefined,
        steps: steps.map((step) => ({
          name: step.name,
          approverType: step.approverType,
          approverRole: step.approverType === 'ROLE' ? step.approverRole || undefined : undefined,
          approverUserId:
            step.approverType === 'USER' ? step.approverUserId || undefined : undefined,
          slaHours: step.slaHours,
          stage: step.stage,
          minApprovals: step.minApprovals ? Number(step.minApprovals) : undefined,
          conditionType: step.conditionType,
          conditionDepartmentId:
            step.conditionType === 'DEPARTMENT'
              ? step.conditionDepartmentId || undefined
              : undefined,
        })),
      };
      return id ? updatePolicy(id, body) : createPolicy(body);
    },
    onSuccess: () => {
      toast.success(id ? 'New workflow policy version created.' : 'Workflow policy created.');
      void queryClient.invalidateQueries({ queryKey: ['policies'] });
      void queryClient.invalidateQueries({ queryKey: ['policy', id] });
      navigate('/admin/workflow?tab=policies');
    },
    onError: (error) => toast.error(problemMessage(error)),
  });

  if (id && policy.isLoading) return <Loading />;
  if (id && policy.error) {
    return <Typography color="error">This workflow policy could not be loaded.</Typography>;
  }

  return (
    <Box sx={{ width: '100%' }}>
      <PageHeader
        title={id ? 'New workflow policy version' : 'New workflow policy'}
        actions={
          <Button
            component={RouterLink}
            to="/admin/workflow?tab=policies"
            startIcon={<ArrowBackIcon />}
          >
            Back to workflow
          </Button>
        }
      />
      <Stack spacing={2}>
        <Card>
          <CardContent>
            <Typography variant="h6" sx={{ mb: 2 }}>
              Policy definition
            </Typography>
            <Grid container spacing={2}>
              <Grid size={{ xs: 12, md: 3 }}>
                <TextField
                  select
                  fullWidth
                  label="Applies to"
                  value={entityType}
                  onChange={(event) => setEntityType(event.target.value)}
                >
                  <MenuItem value="PURCHASE_REQUEST">Purchase requests</MenuItem>
                  <MenuItem value="PURCHASE_ORDER">Purchase orders</MenuItem>
                </TextField>
              </Grid>
              <Grid size={{ xs: 12, md: 5 }}>
                <TextField
                  fullWidth
                  label="Policy name"
                  value={name}
                  onChange={(event) => setName(event.target.value)}
                />
              </Grid>
              <Grid size={{ xs: 6, md: 2 }}>
                <TextField
                  fullWidth
                  label="Amount min"
                  type="number"
                  value={amountMin}
                  onChange={(event) => setAmountMin(event.target.value)}
                />
              </Grid>
              <Grid size={{ xs: 6, md: 2 }}>
                <TextField
                  fullWidth
                  label="Amount max"
                  type="number"
                  value={amountMax}
                  onChange={(event) => setAmountMax(event.target.value)}
                />
              </Grid>
            </Grid>
          </CardContent>
        </Card>

        <Card>
          <CardContent>
            <Stack
              direction={{ xs: 'column', sm: 'row' }}
              justifyContent="space-between"
              alignItems={{ xs: 'flex-start', sm: 'center' }}
              spacing={1}
              sx={{ mb: 2 }}
            >
              <Box>
                <Typography variant="h6">Stages and approvers</Typography>
                <Typography variant="caption" color="text.secondary">
                  Approvers in the same stage act in parallel. Later stages start after the current
                  stage is complete.
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
                        approverType: 'ROLE',
                        approverRole: 'APPROVER',
                        approverUserId: '',
                        slaHours: 24,
                        stage,
                        minApprovals:
                          steps.find((item) => item.stage === stage)?.minApprovals ?? '',
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
                        approverType: 'ROLE',
                        approverRole: 'APPROVER',
                        approverUserId: '',
                        slaHours: 24,
                        stage,
                        minApprovals:
                          steps.find((item) => item.stage === stage)?.minApprovals ?? '',
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

            <WorkflowPreview
              steps={steps}
              departments={departments.data?.content ?? []}
              onQuorumChange={(stage, value) =>
                setSteps((current) =>
                  current.map((step) =>
                    step.stage === stage ? { ...step, minApprovals: value } : step,
                  ),
                )
              }
            />

            <Stack spacing={2} sx={{ mt: 2 }}>
              {steps.map((step, index) => (
                <Card key={`${step.stage}-${index}`} variant="outlined">
                  <CardContent>
                    <Stack
                      direction="row"
                      spacing={1}
                      flexWrap="wrap"
                      useFlexGap
                      alignItems="center"
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
                        label="Actor type"
                        value={step.approverType}
                        sx={{ minWidth: 180 }}
                        onChange={(event) => {
                          const next = [...steps];
                          next[index] = {
                            ...step,
                            approverType: event.target.value as ApproverType,
                            approverRole: event.target.value === 'ROLE' ? 'APPROVER' : '',
                            approverUserId: '',
                          };
                          setSteps(next);
                        }}
                      >
                        <MenuItem value="ROLE">Role</MenuItem>
                        <MenuItem value="REQUESTER_MANAGER">Requester&apos;s manager</MenuItem>
                        <MenuItem value="USER">Specific user</MenuItem>
                      </TextField>
                      {step.approverType === 'ROLE' && (
                        <TextField
                          select
                          label="Role"
                          value={step.approverRole}
                          sx={{ minWidth: 180 }}
                          onChange={(event) => {
                            const next = [...steps];
                            next[index] = { ...step, approverRole: event.target.value };
                            setSteps(next);
                          }}
                        >
                          {[
                            'REQUESTER',
                            'APPROVER',
                            'FINANCE',
                            'ADMIN',
                            'PROCUREMENT_OFFICER',
                            'AUDITOR',
                          ].map((role) => (
                            <MenuItem key={role} value={role}>
                              {role}
                            </MenuItem>
                          ))}
                        </TextField>
                      )}
                      {step.approverType === 'USER' && (
                        <TextField
                          select
                          label="User"
                          value={step.approverUserId}
                          sx={{ minWidth: 220 }}
                          onChange={(event) => {
                            const next = [...steps];
                            next[index] = { ...step, approverUserId: event.target.value };
                            setSteps(next);
                          }}
                        >
                          {(users.data?.content ?? []).map((user) => (
                            <MenuItem key={user.id} value={user.id}>
                              {user.displayName} ({user.email})
                            </MenuItem>
                          ))}
                        </TextField>
                      )}
                      <TextField
                        label="SLA hours"
                        type="number"
                        value={step.slaHours}
                        sx={{ width: 120 }}
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
                        sx={{ minWidth: 180 }}
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
                        aria-label="Remove approver"
                        disabled={steps.length === 1}
                        color="error"
                        onClick={() => setSteps(steps.filter((_, i) => i !== index))}
                      >
                        <DeleteIcon />
                      </IconButton>
                    </Stack>
                  </CardContent>
                </Card>
              ))}
            </Stack>
          </CardContent>
        </Card>

        <Stack direction="row" spacing={1} justifyContent="flex-end">
          <Button component={RouterLink} to="/admin/workflow?tab=policies">
            Cancel
          </Button>
          <Button
            variant="contained"
            disabled={!name || save.isPending}
            onClick={() => save.mutate()}
          >
            {id ? 'Create new version' : 'Create workflow policy'}
          </Button>
        </Stack>
      </Stack>
    </Box>
  );
}

function WorkflowPreview({
  steps,
  departments,
  onQuorumChange,
}: {
  steps: StepForm[];
  departments: { id?: string; name?: string }[];
  onQuorumChange: (stage: number, value: string) => void;
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
              <Box sx={{ p: 1.5, minWidth: 200, bgcolor: 'background.paper', borderRadius: 2 }}>
                <Typography variant="overline" color="text.secondary">
                  Stage {stage}
                </Typography>
                <Stack spacing={0.5}>
                  {stageSteps.map((step, stepIndex) => (
                    <Chip
                      key={`${step.name}-${stepIndex}`}
                      size="small"
                      label={`${actorLabel(step)} · ${conditionLabel(step, departments)}`}
                      variant={step.conditionType === 'ALWAYS' ? 'outlined' : 'filled'}
                    />
                  ))}
                </Stack>
                <TextField
                  size="small"
                  label="Quorum"
                  type="number"
                  placeholder="All"
                  value={stageSteps[0]?.minApprovals ?? ''}
                  sx={{ mt: 1, width: 120 }}
                  slotProps={{ htmlInput: { min: 1, max: stageSteps.length } }}
                  onChange={(event) => onQuorumChange(stage, event.target.value)}
                />
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

function actorLabel(step: StepForm): string {
  if (step.approverType === 'REQUESTER_MANAGER') return 'Requester manager';
  if (step.approverType === 'USER') return 'Specific user';
  return step.approverRole;
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
