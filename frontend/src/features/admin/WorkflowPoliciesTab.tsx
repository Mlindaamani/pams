import { useState } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button, Stack } from '@mui/material';
import { Can } from '@/auth/AbilityProvider';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { DataTable } from '@/components/DataTable';
import { EmptyState, Loading } from '@/components/Page';
import { toast } from '@/components/Toast';
import { deletePolicy, listPolicies } from './api';

export function WorkflowPoliciesTab() {
  const queryClient = useQueryClient();
  const policies = useQuery({ queryKey: ['policies'], queryFn: listPolicies });
  const [deleting, setDeleting] = useState<{ id: string; name: string } | null>(null);

  const remove = useMutation({
    mutationFn: (id: string) => deletePolicy(id),
    onSuccess: () => {
      setDeleting(null);
      toast.success('Workflow policy deleted.');
      void queryClient.invalidateQueries({ queryKey: ['policies'] });
    },
  });

  if (policies.isLoading) return <Loading />;
  const rows = policies.data?.content ?? [];

  if (rows.length === 0) {
    return <EmptyState message="No workflow policies configured." />;
  }

  return (
    <>
      <DataTable
        ariaLabel="Workflow policies"
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
                  step.approverType === 'REQUESTER_MANAGER'
                    ? 'Requester manager'
                    : (step.approverRole ?? step.approverUserId ?? step.name ?? 'Approver'),
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
                <Can I="manage" a="Policy">
                  <Button
                    component={RouterLink}
                    to={`/admin/workflow/policies/${policy.id}/new-version`}
                    size="small"
                  >
                    New version
                  </Button>
                </Can>
                <Can I="manage" a="Policy">
                  <Button
                    size="small"
                    color="error"
                    onClick={() => setDeleting({ id: policy.id!, name: policy.name ?? 'policy' })}
                  >
                    Delete
                  </Button>
                </Can>
              </Stack>
            ),
          },
        ]}
      />
      <ConfirmDialog
        open={Boolean(deleting)}
        title="Delete workflow policy"
        message={`Delete "${deleting?.name ?? 'this policy'}"? Existing approval history is preserved, but future requests will no longer use this policy version.`}
        confirmLabel="Delete policy"
        busy={remove.isPending}
        onCancel={() => setDeleting(null)}
        onConfirm={() => deleting && remove.mutate(deleting.id)}
      />
    </>
  );
}
