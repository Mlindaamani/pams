import { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Divider,
  IconButton,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
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

const ENTITIES = [
  {
    value: 'purchase_request',
    label: 'Purchase requests',
    singular: 'request',
  },
  {
    value: 'vendor_payment',
    label: 'Vendor payments',
    singular: 'payment',
  },
] as const;

type EntityType = (typeof ENTITIES)[number]['value'];

interface ActionMeta {
  label: string;
  description: string;
  permission: string;
}

const ACTIONS: Record<string, ActionMeta> = {
  EDIT: {
    label: 'Edit',
    description: 'Change the draft or a request returned for changes.',
    permission: 'purchase_request:update',
  },
  DELETE: {
    label: 'Delete draft',
    description: 'Permanently remove a draft before it enters approval.',
    permission: 'purchase_request:delete',
  },
  SUBMIT: {
    label: 'Submit for approval',
    description: 'Send the request into its approval chain.',
    permission: 'purchase_request:submit',
  },
  CANCEL: {
    label: 'Cancel',
    description: 'Stop the request and release any budget reservation.',
    permission: 'purchase_request:cancel',
  },
  APPROVE: {
    label: 'Approve',
    description: 'Approve the current approval stage.',
    permission: 'approval:act',
  },
  REJECT: {
    label: 'Reject',
    description: 'Reject the request and stop the workflow.',
    permission: 'approval:act',
  },
  REQUEST_CHANGES: {
    label: 'Request changes',
    description: 'Send the request back to the requester for changes.',
    permission: 'approval:act',
  },
  CREATE_PO: {
    label: 'Create purchase order',
    description: 'Convert an approved request into a purchase order.',
    permission: 'purchase_order:create',
  },
  MARK_PAID: {
    label: 'Mark payment paid',
    description: 'Record that the scheduled payment has been completed.',
    permission: 'payment:manage',
  },
};

const ENTITY_ACTIONS: Record<EntityType, string[]> = {
  purchase_request: [
    'EDIT',
    'DELETE',
    'SUBMIT',
    'CANCEL',
    'APPROVE',
    'REJECT',
    'REQUEST_CHANGES',
    'CREATE_PO',
  ],
  vendor_payment: ['APPROVE', 'CANCEL', 'MARK_PAID'],
};

const STATUS_HELP: Record<EntityType, Record<string, string>> = {
  purchase_request: {
    DRAFT: 'The requester can still prepare and change the request.',
    SUBMITTED: 'The request has been sent in and is waiting to enter approval.',
    IN_APPROVAL: 'Approvers act on the current approval stage.',
    CHANGES_REQUESTED: 'The requester must update the request before resubmitting.',
    APPROVED: 'The request is approved and ready to become a purchase order.',
    REJECTED: 'The request was rejected. No further actions are expected.',
    CANCELLED: 'The request was cancelled. No further actions are expected.',
    CONVERTED_TO_PO: 'The request has become a purchase order.',
  },
  vendor_payment: {
    SCHEDULED: 'The payment is scheduled and waiting for approval.',
    APPROVED: 'The payment is approved and ready to be marked paid.',
    PAID: 'The payment is complete.',
    FAILED: 'The payment failed. No further actions are expected.',
    CANCELED: 'The payment was cancelled. No further actions are expected.',
  },
};

const PERMISSION_LABELS: Record<string, string> = {
  'purchase_request:update': 'Edit purchase requests',
  'purchase_request:delete': 'Delete purchase requests',
  'purchase_request:submit': 'Submit purchase requests',
  'purchase_request:cancel': 'Cancel purchase requests',
  'approval:act': 'Act on approvals',
  'purchase_order:create': 'Create purchase orders',
  'payment:manage': 'Manage vendor payments',
};

const PERMISSION_FALLBACK: Record<string, string> = {
  'purchase_request:update': 'Edit purchase requests',
  'purchase_request:delete': 'Delete purchase requests',
  'purchase_request:submit': 'Submit purchase requests',
  'purchase_request:cancel': 'Cancel purchase requests',
  'approval:act': 'Act on approvals',
  'purchase_order:create': 'Create purchase orders',
  'payment:manage': 'Manage vendor payments',
};

export function WorkflowActionsTab() {
  const queryClient = useQueryClient();
  const [entityType, setEntityType] = useState<EntityType>('purchase_request');
  const [rules, setRules] = useState<WorkflowRule[]>([]);
  const [previewRole, setPreviewRole] = useState('');
  const access = useQuery({ queryKey: ['access-config'], queryFn: getAccessConfig });
  const workflow = useQuery({
    queryKey: ['workflow-rules', entityType],
    queryFn: () => getWorkflowRules(entityType),
  });
  const statuses = useQuery({
    queryKey: ['workflow-statuses', entityType],
    queryFn: () => getWorkflowStatuses(entityType),
  });

  useEffect(() => {
    setRules(workflow.data ?? []);
  }, [workflow.data]);

  const savedRules = workflow.data ?? [];
  const dirty = JSON.stringify(rules) !== JSON.stringify(savedRules);
  const entity = ENTITIES.find((item) => item.value === entityType) ?? ENTITIES[0];

  const roleNames = useMemo(
    () => new Map((access.data?.roles ?? []).map((role) => [role.roleName, role.displayName])),
    [access.data?.roles],
  );
  const rolesByPermission = useMemo(() => {
    const map = new Map<string, string[]>();
    (access.data?.rolePermissions ?? []).forEach((role) => {
      role.permissions.forEach((permission) => {
        map.set(permission, [...(map.get(permission) ?? []), role.roleName]);
      });
    });
    return map;
  }, [access.data?.rolePermissions]);
  const permissionOptions = useMemo(
    () =>
      (access.data?.permissions ?? []).map((permission) => {
        const roles = rolesByPermission.get(permission.code) ?? [];
        return {
          value: permission.code,
          label:
            PERMISSION_LABELS[permission.code] ||
            permission.description ||
            PERMISSION_FALLBACK[permission.code] ||
            'Permission',
          description:
            roles.length === 0
              ? 'Not granted to any role yet'
              : `Granted to ${roles.map((role) => roleNames.get(role) ?? role).join(', ')}`,
        };
      }),
    [access.data?.permissions, rolesByPermission, roleNames],
  );
  const selectedRolePermissions = useMemo(
    () =>
      new Set(
        access.data?.rolePermissions?.find((role) => role.roleName === previewRole)?.permissions ??
          [],
      ),
    [access.data?.rolePermissions, previewRole],
  );

  const save = useMutation({
    mutationFn: () => saveWorkflowRules(entityType, rules),
    onSuccess: () => {
      toast.success('Workflow actions updated.');
      void queryClient.invalidateQueries({ queryKey: ['workflow-rules', entityType] });
    },
    onError: (error) => toast.error(problemMessage(error)),
  });

  if (access.isLoading || workflow.isLoading || statuses.isLoading) return <Loading />;

  const addAction = (fromStatus: string, actionCode: string) => {
    const meta = ACTIONS[actionCode];
    if (!meta) return;
    setRules((current) => [
      ...current,
      {
        entityType,
        fromStatus,
        actionCode,
        requiredPermission: entityType === 'vendor_payment' ? 'payment:manage' : meta.permission,
      },
    ]);
  };

  const updateRule = (index: number, patch: Partial<WorkflowRule>) => {
    setRules((current) => current.map((rule, i) => (i === index ? { ...rule, ...patch } : rule)));
  };

  const warnings: string[] = [];
  if (entityType === 'purchase_request') {
    if (!rules.some((rule) => rule.fromStatus === 'DRAFT' && rule.actionCode === 'SUBMIT')) {
      warnings.push('Draft requests have no “Submit for approval” action.');
    }
    if (
      !rules.some(
        (rule) =>
          rule.fromStatus === 'IN_APPROVAL' &&
          ['APPROVE', 'REJECT', 'REQUEST_CHANGES'].includes(rule.actionCode),
      )
    ) {
      warnings.push('Requests in approval have no approval actions.');
    }
  }

  return (
    <Stack spacing={2}>
      <Alert severity="info">
        These rules control which buttons appear on a {entity.singular} and who can use them.
        Approval chains are configured separately under Workflow policies.
      </Alert>

      <Card>
        <CardContent>
          <Stack
            direction={{ xs: 'column', sm: 'row' }}
            justifyContent="space-between"
            alignItems={{ xs: 'stretch', sm: 'center' }}
            spacing={2}
          >
            <Box>
              <Typography variant="h6">Configure actions for</Typography>
              <Typography variant="body2" color="text.secondary">
                Choose the record type, then decide what each status allows.
              </Typography>
            </Box>
            <TextField
              select
              size="small"
              label="Record type"
              value={entityType}
              onChange={(event) => {
                setEntityType(event.target.value as EntityType);
                setPreviewRole('');
              }}
              sx={{ minWidth: 240 }}
            >
              {ENTITIES.map((option) => (
                <MenuItem key={option.value} value={option.value}>
                  {option.label}
                </MenuItem>
              ))}
            </TextField>
          </Stack>
        </CardContent>
      </Card>

      {warnings.length > 0 && (
        <Alert severity="warning">
          {warnings.map((warning) => (
            <Typography key={warning} variant="body2">
              {warning}
            </Typography>
          ))}
        </Alert>
      )}

      <Stack spacing={2}>
        {(statuses.data ?? []).map((status) => {
          const statusRules = rules.filter((rule) => rule.fromStatus === status.statusCode);
          const usedActions = new Set(statusRules.map((rule) => rule.actionCode));
          const available = ENTITY_ACTIONS[entityType].filter((action) => !usedActions.has(action));
          return (
            <Card key={status.statusCode}>
              <CardContent>
                <Stack
                  direction={{ xs: 'column', sm: 'row' }}
                  justifyContent="space-between"
                  spacing={1}
                  sx={{ mb: 1 }}
                >
                  <Box>
                    <Typography variant="h6">
                      When {entity.singular} is {status.displayName}
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      {STATUS_HELP[entityType][status.statusCode] ??
                        'Configure actions for this status.'}
                    </Typography>
                  </Box>
                  <Chip
                    size="small"
                    label={`${statusRules.length} action${statusRules.length === 1 ? '' : 's'}`}
                    variant="outlined"
                  />
                </Stack>

                <Stack spacing={1}>
                  {statusRules.length === 0 && (
                    <Typography color="text.secondary" sx={{ py: 1 }}>
                      No actions are available in this status.
                    </Typography>
                  )}
                  {statusRules.map((rule) => {
                    const index = rules.indexOf(rule);
                    const meta = ACTIONS[rule.actionCode];
                    return (
                      <Box
                        key={`${rule.fromStatus}-${rule.actionCode}`}
                        sx={{
                          p: 1.5,
                          border: 1,
                          borderColor: 'divider',
                          borderRadius: 2,
                          bgcolor: 'grey.50',
                        }}
                      >
                        <Stack
                          direction={{ xs: 'column', md: 'row' }}
                          spacing={1.5}
                          alignItems={{ xs: 'stretch', md: 'center' }}
                        >
                          <Box sx={{ flexGrow: 1 }}>
                            <Typography fontWeight={600}>
                              {meta?.label ?? rule.actionCode}
                            </Typography>
                            <Typography variant="body2" color="text.secondary">
                              {actionDescription(entityType, rule.actionCode)}
                            </Typography>
                            <Stack
                              direction="row"
                              spacing={0.5}
                              flexWrap="wrap"
                              useFlexGap
                              sx={{ mt: 0.75 }}
                            >
                              {(rolesByPermission.get(rule.requiredPermission) ?? []).map(
                                (role) => (
                                  <Chip
                                    key={role}
                                    size="small"
                                    label={roleNames.get(role) ?? role}
                                    variant="outlined"
                                  />
                                ),
                              )}
                              {(rolesByPermission.get(rule.requiredPermission) ?? []).length ===
                                0 && (
                                <Chip
                                  size="small"
                                  label="No role has this permission yet"
                                  color="warning"
                                  variant="outlined"
                                />
                              )}
                            </Stack>
                          </Box>
                          <Box sx={{ width: { xs: '100%', md: 300 } }}>
                            <SearchableSelect
                              label="Who can use it"
                              value={rule.requiredPermission}
                              options={permissionOptions}
                              onChange={(value) => updateRule(index, { requiredPermission: value })}
                            />
                          </Box>
                          <IconButton
                            aria-label={`Remove ${meta?.label ?? rule.actionCode}`}
                            color="error"
                            onClick={() =>
                              setRules((current) => current.filter((_item, i) => i !== index))
                            }
                          >
                            <DeleteIcon />
                          </IconButton>
                        </Stack>
                      </Box>
                    );
                  })}
                </Stack>

                {available.length > 0 && (
                  <TextField
                    select
                    size="small"
                    label="Add action"
                    value=""
                    onChange={(event) => addAction(status.statusCode, event.target.value)}
                    sx={{ mt: 1.5, minWidth: 240 }}
                  >
                    <MenuItem value="" disabled>
                      Choose an action…
                    </MenuItem>
                    {available.map((action) => (
                      <MenuItem key={action} value={action}>
                        {ACTIONS[action]?.label ?? action}
                      </MenuItem>
                    ))}
                  </TextField>
                )}
              </CardContent>
            </Card>
          );
        })}
      </Stack>

      <Card>
        <CardContent>
          <Stack direction={{ xs: 'column', md: 'row' }} justifyContent="space-between" spacing={2}>
            <Box>
              <Typography variant="h6">Preview actions by role</Typography>
              <Typography variant="body2" color="text.secondary">
                See which buttons a selected role would see with these rules.
              </Typography>
            </Box>
            <TextField
              select
              size="small"
              label="Role"
              value={previewRole}
              onChange={(event) => setPreviewRole(event.target.value)}
              sx={{ minWidth: 240 }}
            >
              <MenuItem value="">Select a role</MenuItem>
              {(access.data?.roles ?? []).map((role) => (
                <MenuItem key={role.roleName} value={role.roleName}>
                  {role.displayName}
                </MenuItem>
              ))}
            </TextField>
          </Stack>
          {previewRole && (
            <Stack spacing={1.5} sx={{ mt: 2 }}>
              {(statuses.data ?? []).map((status) => {
                const actions = rules
                  .filter(
                    (rule) =>
                      rule.fromStatus === status.statusCode &&
                      selectedRolePermissions.has(rule.requiredPermission),
                  )
                  .map((rule) => ACTIONS[rule.actionCode]?.label ?? rule.actionCode);
                return (
                  <Box key={status.statusCode}>
                    <Typography variant="subtitle2">{status.displayName}</Typography>
                    {actions.length === 0 ? (
                      <Typography variant="body2" color="text.secondary">
                        No buttons visible.
                      </Typography>
                    ) : (
                      <Stack
                        direction="row"
                        spacing={1}
                        flexWrap="wrap"
                        useFlexGap
                        sx={{ mt: 0.5 }}
                      >
                        {actions.map((action) => (
                          <Chip key={action} size="small" label={action} color="primary" />
                        ))}
                      </Stack>
                    )}
                  </Box>
                );
              })}
            </Stack>
          )}
        </CardContent>
      </Card>

      <Divider />
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} justifyContent="flex-end">
        <Button disabled={!dirty} onClick={() => setRules(savedRules)}>
          Discard changes
        </Button>
        <Button
          variant="contained"
          disabled={!dirty || rules.length === 0 || save.isPending}
          onClick={() => save.mutate()}
        >
          Save workflow actions
        </Button>
      </Stack>
    </Stack>
  );
}

function actionDescription(entityType: EntityType, actionCode: string): string {
  if (entityType === 'vendor_payment') {
    if (actionCode === 'APPROVE') return 'Approve the scheduled payment.';
    if (actionCode === 'CANCEL') return 'Cancel the scheduled or approved payment.';
    if (actionCode === 'MARK_PAID') return 'Record that the payment has been completed.';
  }
  return ACTIONS[actionCode]?.description ?? 'Configured workflow action.';
}
