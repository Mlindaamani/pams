import { useState } from 'react';
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
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { DataTable } from '@/components/DataTable';
import { toast } from '@/components/Toast';
import { Can } from '@/auth/AbilityProvider';
import { useBranding } from '@/branding/BrandingProvider';
import { EmptyState, Loading, PageHeader } from '@/components/Page';
import { money } from '@/lib/format';
import { listDepartments } from '@/features/reference/api';
import { createBudget, deleteBudget, listBudgets, updateBudget } from './api';

export function BudgetsPage() {
  const { branding } = useBranding();
  const queryClient = useQueryClient();
  const budgets = useQuery({ queryKey: ['budgets'], queryFn: () => listBudgets({ size: 100 }) });
  const departments = useQuery({ queryKey: ['departments'], queryFn: listDepartments });
  const [open, setOpen] = useState(false);
  const [departmentId, setDepartmentId] = useState('');
  const [fiscalYear, setFiscalYear] = useState(new Date().getFullYear());
  const [amount, setAmount] = useState('');
  const [editing, setEditing] = useState<import('@/api/types').Budget | null>(null);
  const [editAmount, setEditAmount] = useState('');
  const [deleting, setDeleting] = useState<import('@/api/types').Budget | null>(null);

  const create = useMutation({
    mutationFn: () =>
      createBudget({
        departmentId,
        fiscalYear,
        allocated: { amount, currency: 'TZS' },
      }),
    onSuccess: () => {
      setOpen(false);
      setAmount('');
      toast.success('Budget created.');
      void queryClient.invalidateQueries({ queryKey: ['budgets'] });
    },
  });

  const update = useMutation({
    mutationFn: () =>
      updateBudget(editing!.id!, {
        amount: editAmount,
        currency: editing!.allocated?.currency ?? 'TZS',
      }),
    onSuccess: () => {
      toast.success('Budget updated.');
      setEditing(null);
      void queryClient.invalidateQueries({ queryKey: ['budgets'] });
    },
  });
  const remove = useMutation({
    mutationFn: (id: string) => deleteBudget(id),
    onSuccess: () => {
      toast.success('Budget deleted.');
      void queryClient.invalidateQueries({ queryKey: ['budgets'] });
    },
  });

  if (budgets.isLoading) return <Loading />;
  const rows = budgets.data?.content ?? [];
  const departmentNames = new Map(
    (departments.data?.content ?? []).map((department) => [department.id!, department.name ?? '']),
  );

  return (
    <>
      <PageHeader
        title={branding.budgetLabel}
        actions={
          <Can I="create" a="Budget">
            <Button variant="contained" onClick={() => setOpen(true)}>
              New budget
            </Button>
          </Can>
        }
      />
      {rows.length === 0 ? (
        <EmptyState message="No budgets defined." />
      ) : (
        <DataTable
          ariaLabel="Budgets"
          rows={rows}
          getRowId={(budget) => budget.id!}
          columns={[
            {
              key: 'department',
              label: 'Department',
              render: (budget) =>
                departmentNames.get(budget.departmentId ?? '') ?? budget.departmentId,
            },
            {
              key: 'year',
              label: 'Year',
              render: (budget) => budget.fiscalYear,
              sortValue: (budget) => budget.fiscalYear ?? 0,
            },
            {
              key: 'allocated',
              label: 'Allocated',
              render: (budget) => money(budget.allocated),
              sortValue: (budget) => Number(budget.allocated?.amount ?? 0),
            },
            { key: 'reserved', label: 'Reserved', render: (budget) => money(budget.reserved) },
            { key: 'committed', label: 'Committed', render: (budget) => money(budget.committed) },
            { key: 'actual', label: 'Actual', render: (budget) => money(budget.actual) },
            {
              key: 'remaining',
              label: 'Remaining',
              render: (budget) => money(budget.remaining),
              sortValue: (budget) => Number(budget.remaining?.amount ?? 0),
            },
            {
              key: 'actions',
              label: 'Actions',
              align: 'right',
              render: (budget) => (
                <Stack direction="row" spacing={1} justifyContent="flex-end">
                  <Button
                    size="small"
                    onClick={() => {
                      setEditing(budget);
                      setEditAmount(budget.allocated?.amount ?? '0');
                    }}
                  >
                    Edit
                  </Button>
                  <Button
                    size="small"
                    color="error"
                    onClick={() => {
                      if (
                        window.confirm(
                          `Delete budget for ${departmentNames.get(budget.departmentId ?? '') ?? 'this department'}?`,
                        )
                      ) {
                        remove.mutate(budget.id!);
                      }
                    }}
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
        title="Delete budget"
        message={`Delete the budget for ${departmentNames.get(deleting?.departmentId ?? '') ?? 'this department'}? This cannot be undone.`}
        busy={remove.isPending}
        onCancel={() => setDeleting(null)}
        onConfirm={() => {
          if (deleting) remove.mutate(deleting.id!, { onSuccess: () => setDeleting(null) });
        }}
      />

      <Dialog open={Boolean(editing)} onClose={() => setEditing(null)} fullWidth maxWidth="sm">
        <DialogTitle>Edit budget</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField
              label="Allocated amount"
              type="number"
              value={editAmount}
              onChange={(event) => setEditAmount(event.target.value)}
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setEditing(null)}>Cancel</Button>
          <Button
            variant="contained"
            disabled={!editAmount || update.isPending}
            onClick={() => update.mutate()}
          >
            Save
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={open} onClose={() => setOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>New budget</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField
              select
              label="Department"
              value={departmentId}
              onChange={(event) => setDepartmentId(event.target.value)}
            >
              {(departments.data?.content ?? []).map((department) => (
                <MenuItem key={department.id} value={department.id}>
                  {department.name}
                </MenuItem>
              ))}
            </TextField>
            <TextField
              label="Fiscal year"
              type="number"
              value={fiscalYear}
              onChange={(event) => setFiscalYear(Number(event.target.value))}
            />
            <TextField
              label="Allocated amount (TZS)"
              type="number"
              value={amount}
              onChange={(event) => setAmount(event.target.value)}
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setOpen(false)}>Cancel</Button>
          <Button
            variant="contained"
            disabled={!departmentId || !amount || create.isPending}
            onClick={() => create.mutate()}
          >
            Create
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
}
