import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  TextField,
} from '@mui/material';
import { Can } from '@/auth/AbilityProvider';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { DataTable } from '@/components/DataTable';
import { toast } from '@/components/Toast';
import { EmptyState, Loading, PageHeader } from '@/components/Page';
import {
  bulkDeleteDepartments,
  createDepartment,
  deleteDepartment,
  listDepartments,
} from '@/features/reference/api';

export function DepartmentsPage() {
  const queryClient = useQueryClient();
  const departments = useQuery({ queryKey: ['departments'], queryFn: listDepartments });
  const [open, setOpen] = useState(false);
  const [name, setName] = useState('');
  const [deleting, setDeleting] = useState<import('@/api/types').Department | null>(null);
  const [bulkIds, setBulkIds] = useState<string[] | null>(null);

  const create = useMutation({
    mutationFn: () => createDepartment({ name }),
    onSuccess: () => {
      setOpen(false);
      setName('');
      toast.success('Department created.');
      void queryClient.invalidateQueries({ queryKey: ['departments'] });
    },
  });
  const remove = useMutation({
    mutationFn: (id: string) => deleteDepartment(id),
    onSuccess: () => {
      toast.success('Department deleted.');
      void queryClient.invalidateQueries({ queryKey: ['departments'] });
    },
  });
  const bulkRemove = useMutation({
    mutationFn: (ids: string[]) => bulkDeleteDepartments(ids),
    onSuccess: (count) => {
      toast.success(`${count} department(s) deleted.`);
      void queryClient.invalidateQueries({ queryKey: ['departments'] });
    },
  });

  if (departments.isLoading) return <Loading />;
  const rows = departments.data?.content ?? [];

  return (
    <>
      <PageHeader
        title="Departments"
        actions={
          <Can I="create" a="Department">
            <Button variant="contained" onClick={() => setOpen(true)}>
              New department
            </Button>
          </Can>
        }
      />
      {rows.length === 0 ? (
        <EmptyState message="No departments yet." />
      ) : (
        <DataTable
          ariaLabel="Departments"
          rows={rows}
          getRowId={(department) => department.id!}
          bulkActions={(ids, clear) => (
            <Can I="delete" a="Department">
              <Button
                size="small"
                color="error"
                variant="contained"
                onClick={() => {
                  setBulkIds(ids);
                  clear();
                }}
              >
                Delete selected
              </Button>
            </Can>
          )}
          columns={[
            {
              key: 'name',
              label: 'Name',
              render: (department) => department.name,
              sortValue: (department) => department.name ?? '',
            },
            {
              key: 'actions',
              label: 'Actions',
              align: 'right',
              render: (department) => (
                <Button size="small" color="error" onClick={() => remove.mutate(department.id!)}>
                  Delete
                </Button>
              ),
            },
          ]}
        />
      )}

      <ConfirmDialog
        open={Boolean(deleting)}
        title="Delete department"
        message={`Delete ${deleting?.name ?? 'this department'}? Existing users will keep a reference to it.`}
        busy={remove.isPending}
        onCancel={() => setDeleting(null)}
        onConfirm={() => {
          if (deleting) remove.mutate(deleting.id!, { onSuccess: () => setDeleting(null) });
        }}
      />
      <ConfirmDialog
        open={Boolean(bulkIds)}
        title="Delete selected departments"
        message={`Delete ${bulkIds?.length ?? 0} department(s)?`}
        busy={bulkRemove.isPending}
        onCancel={() => setBulkIds(null)}
        onConfirm={() => {
          if (bulkIds) bulkRemove.mutate(bulkIds, { onSuccess: () => setBulkIds(null) });
        }}
      />

      <Dialog open={open} onClose={() => setOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>New department</DialogTitle>
        <DialogContent>
          <TextField
            autoFocus
            fullWidth
            label="Name"
            value={name}
            onChange={(event) => setName(event.target.value)}
            sx={{ mt: 1 }}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setOpen(false)}>Cancel</Button>
          <Button
            variant="contained"
            disabled={!name || create.isPending}
            onClick={() => create.mutate()}
          >
            Create
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
}
