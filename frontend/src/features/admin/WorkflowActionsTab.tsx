import { useEffect, useState, type Dispatch, type SetStateAction } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  Button,
  Card,
  CardContent,
  Grid,
  IconButton,
  MenuItem,
  Stack,
  TextField,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import DeleteIcon from '@mui/icons-material/Delete';
import { Loading } from '@/components/Page';
import { SearchableSelect } from '@/components/SearchableSelect';
import { toast } from '@/components/Toast';
import { problemMessage } from '@/api/client';
import {
  getAccessConfig,
  getWorkflowRules,
  getWorkflowStatuses,
  saveWorkflowRules,
  type WorkflowRule,
} from './accessConfigApi';

const WORKFLOW_ACTIONS = [
  'EDIT',
  'DELETE',
  'SUBMIT',
  'CANCEL',
  'APPROVE',
  'REJECT',
  'REQUEST_CHANGES',
  'CREATE_PO',
];

export function WorkflowActionsTab() {
  const queryClient = useQueryClient();
  const [rules, setRules] = useState<WorkflowRule[]>([]);
  const access = useQuery({ queryKey: ['access-config'], queryFn: getAccessConfig });
  const workflow = useQuery({
    queryKey: ['workflow-rules', 'purchase_request'],
    queryFn: () => getWorkflowRules('purchase_request'),
  });
  const statuses = useQuery({
    queryKey: ['workflow-statuses', 'purchase_request'],
    queryFn: () => getWorkflowStatuses('purchase_request'),
  });

  useEffect(() => {
    setRules(workflow.data ?? []);
  }, [workflow.data]);

  const save = useMutation({
    mutationFn: () => saveWorkflowRules('purchase_request', rules),
    onSuccess: () => {
      toast.success('Workflow actions updated.');
      void queryClient.invalidateQueries({ queryKey: ['workflow-rules'] });
    },
    onError: (error) => toast.error(problemMessage(error)),
  });

  if (access.isLoading || workflow.isLoading || statuses.isLoading) return <Loading />;

  return (
    <Card>
      <CardContent>
        <Stack spacing={2}>
          <Alert severity="info">
            These rules decide which actions are available for each request status and which
            permission is required. Approval chains are configured in the Workflow policies tab.
          </Alert>
          <Stack direction="row" justifyContent="flex-end">
            <Button
              startIcon={<AddIcon />}
              onClick={() =>
                setRules((current) => [
                  ...current,
                  {
                    entityType: 'purchase_request',
                    fromStatus: statuses.data?.[0]?.statusCode ?? 'DRAFT',
                    actionCode: 'EDIT',
                    requiredPermission: 'purchase_request:update',
                  },
                ])
              }
            >
              Add rule
            </Button>
          </Stack>
          <Stack spacing={1.5}>
            {rules.map((rule, index) => (
              <Grid container spacing={1.5} key={`${rule.fromStatus}-${rule.actionCode}-${index}`}>
                <Grid size={{ xs: 12, sm: 2.5 }}>
                  <TextField
                    select
                    size="small"
                    fullWidth
                    label="From status"
                    value={rule.fromStatus}
                    onChange={(event) =>
                      updateRule(setRules, index, { fromStatus: event.target.value })
                    }
                  >
                    {(statuses.data ?? []).map((status) => (
                      <MenuItem key={status.statusCode} value={status.statusCode}>
                        {status.displayName}
                      </MenuItem>
                    ))}
                  </TextField>
                </Grid>
                <Grid size={{ xs: 12, sm: 2.5 }}>
                  <TextField
                    select
                    size="small"
                    fullWidth
                    label="Action"
                    value={rule.actionCode}
                    onChange={(event) =>
                      updateRule(setRules, index, { actionCode: event.target.value })
                    }
                  >
                    {WORKFLOW_ACTIONS.map((action) => (
                      <MenuItem key={action} value={action}>
                        {action.replaceAll('_', ' ')}
                      </MenuItem>
                    ))}
                  </TextField>
                </Grid>
                <Grid size={{ xs: 12, sm: 6 }}>
                  <SearchableSelect
                    label="Required permission"
                    value={rule.requiredPermission}
                    options={(access.data?.permissions ?? []).map((permission) => ({
                      value: permission.code,
                      label: permission.code,
                      description: permission.description,
                    }))}
                    onChange={(value) => updateRule(setRules, index, { requiredPermission: value })}
                  />
                </Grid>
                <Grid size={{ xs: 12, sm: 1 }} sx={{ display: 'flex', alignItems: 'center' }}>
                  <IconButton
                    aria-label="Remove rule"
                    color="error"
                    onClick={() => setRules((current) => current.filter((_item, i) => i !== index))}
                  >
                    <DeleteIcon />
                  </IconButton>
                </Grid>
              </Grid>
            ))}
          </Stack>
          <Button
            variant="contained"
            disabled={rules.length === 0 || save.isPending}
            onClick={() => save.mutate()}
            sx={{ alignSelf: 'flex-start' }}
          >
            Save workflow actions
          </Button>
        </Stack>
      </CardContent>
    </Card>
  );
}

function updateRule(
  setRules: Dispatch<SetStateAction<WorkflowRule[]>>,
  index: number,
  patch: Partial<WorkflowRule>,
) {
  setRules((current) => current.map((rule, i) => (i === index ? { ...rule, ...patch } : rule)));
}
