import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Checkbox,
  FormControlLabel,
  MenuItem,
  Stack,
  TextField,
} from '@mui/material';
import { Can } from '@/auth/AbilityProvider';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { DataTable } from '@/components/DataTable';
import { EmptyState, Loading, PageHeader } from '@/components/Page';
import { toast } from '@/components/Toast';
import { money } from '@/lib/format';
import type { UserProfile } from '@/api/types';
import { listDepartments } from '@/features/reference/api';
import { deleteUser, inviteUser, listUsers, updateUser } from './usersApi';

import type { UserFormState } from './types';

const emptyForm: UserFormState = {
  email: '',
  displayName: '',
  departmentId: '',
  managerId: '',
  limitAmount: '',
  limitCurrency: 'TZS',
  active: true,
  roles: ['REQUESTER'],
};

export function UsersPage() {
  const queryClient = useQueryClient();
  const users = useQuery({ queryKey: ['users'], queryFn: () => listUsers({ size: 100 }) });
  const departments = useQuery({ queryKey: ['departments'], queryFn: listDepartments });
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<UserProfile | null>(null);
  const [form, setForm] = useState<UserFormState>(emptyForm);
  const [deleting, setDeleting] = useState<UserProfile | null>(null);

  const departmentName = useMemo(() => {
    const map = new Map<string, string>();
    (departments.data?.content ?? []).forEach((department) =>
      map.set(department.id!, department.name ?? ''),
    );
    return map;
  }, [departments.data]);

  const managerName = useMemo(() => {
    const map = new Map<string, string>();
    (users.data?.content ?? []).forEach((user) => map.set(user.id!, user.displayName ?? ''));
    return map;
  }, [users.data]);

  const save = useMutation({
    mutationFn: async () => {
      const approvalLimit = form.limitAmount
        ? { amount: form.limitAmount, currency: form.limitCurrency.toUpperCase() }
        : undefined;
      if (editing) {
        return updateUser(editing.id!, {
          displayName: form.displayName,
          departmentId: form.departmentId,
          managerId: form.managerId || undefined,
          approvalLimit,
          active: form.active,
        });
      }
      return inviteUser({
        email: form.email,
        displayName: form.displayName,
        departmentId: form.departmentId,
        managerId: form.managerId || undefined,
        roles: form.roles,
        approvalLimit,
      });
    },
    onSuccess: () => {
      toast.success(editing ? 'User updated.' : 'Invitation sent.');
      setOpen(false);
      setEditing(null);
      setForm(emptyForm);
      void queryClient.invalidateQueries({ queryKey: ['users'] });
    },
  });
  const remove = useMutation({
    mutationFn: (id: string) => deleteUser(id),
    onSuccess: () => {
      toast.success('User removed.');
      void queryClient.invalidateQueries({ queryKey: ['users'] });
    },
  });

  const openCreate = () => {
    setEditing(null);
    setForm(emptyForm);
    setOpen(true);
  };
  const openEdit = (user: UserProfile) => {
    setEditing(user);
    setForm({
      email: user.email ?? '',
      displayName: user.displayName ?? '',
      departmentId: user.departmentId ?? '',
      managerId: user.managerId ?? '',
      limitAmount: user.approvalLimit?.amount ?? '',
      limitCurrency: user.approvalLimit?.currency ?? 'TZS',
      active: user.active ?? true,
      roles: ['REQUESTER'],
    });
    setOpen(true);
  };

  if (users.isLoading) return <Loading />;
  const rows = users.data?.content ?? [];

  return (
    <>
      <PageHeader
        title="Users"
        actions={
          <Can I="manage" a="User">
            <Button variant="contained" onClick={openCreate}>
              Invite user
            </Button>
          </Can>
        }
      />
      {rows.length === 0 ? (
        <EmptyState message="No user profiles yet." />
      ) : (
        <DataTable
          ariaLabel="User profiles"
          rows={rows}
          getRowId={(user) => user.id!}
          bulkActions={undefined}
          columns={[
            {
              key: 'name',
              label: 'Name',
              render: (user) => user.displayName,
              sortValue: (user) => user.displayName ?? '',
            },
            {
              key: 'email',
              label: 'Email',
              render: (user) => user.email,
              sortValue: (user) => user.email ?? '',
            },
            {
              key: 'department',
              label: 'Department',
              render: (user) => departmentName.get(user.departmentId ?? '') ?? '—',
            },
            {
              key: 'manager',
              label: 'Manager',
              render: (user) => managerName.get(user.managerId ?? '') ?? '—',
            },
            {
              key: 'limit',
              label: 'Approval limit',
              render: (user) => (user.approvalLimit ? money(user.approvalLimit) : '—'),
            },
            {
              key: 'active',
              label: 'Status',
              render: (user) => (user.active ? 'Active' : 'Inactive'),
            },
            {
              key: 'actions',
              label: 'Actions',
              align: 'right',
              render: (user) => (
                <Stack direction="row" spacing={1} justifyContent="flex-end">
                  <Button size="small" onClick={() => openEdit(user)}>
                    Edit
                  </Button>
                  <Button size="small" color="error" onClick={() => remove.mutate(user.id!)}>
                    Remove
                  </Button>
                </Stack>
              ),
            },
          ]}
        />
      )}

      <ConfirmDialog
        open={Boolean(deleting)}
        title="Remove user profile"
        message={`Remove ${deleting?.displayName ?? 'this user'}? This soft-deletes the profile.`}
        busy={remove.isPending}
        onCancel={() => setDeleting(null)}
        onConfirm={() => {
          if (deleting) remove.mutate(deleting.id!, { onSuccess: () => setDeleting(null) });
        }}
      />

      <Dialog open={open} onClose={() => setOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>{editing ? 'Edit user profile' : 'Invite user'}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            {!editing && (
              <TextField
                label="Email"
                type="email"
                value={form.email}
                onChange={(event) => setForm({ ...form, email: event.target.value })}
                required
              />
            )}
            <TextField
              label="Display name"
              value={form.displayName}
              onChange={(event) => setForm({ ...form, displayName: event.target.value })}
              required
            />
            <TextField
              select
              label="Department"
              value={form.departmentId}
              onChange={(event) => setForm({ ...form, departmentId: event.target.value })}
              required
            >
              {(departments.data?.content ?? []).map((department) => (
                <MenuItem key={department.id} value={department.id}>
                  {department.name}
                </MenuItem>
              ))}
            </TextField>
            <TextField
              select
              label="Manager"
              value={form.managerId}
              onChange={(event) => setForm({ ...form, managerId: event.target.value })}
            >
              <MenuItem value="">None</MenuItem>
              {(users.data?.content ?? [])
                .filter((user) => user.id !== editing?.id)
                .map((user) => (
                  <MenuItem key={user.id} value={user.id}>
                    {user.displayName}
                  </MenuItem>
                ))}
            </TextField>
            <Stack direction="row" spacing={2}>
              <TextField
                label="Approval limit"
                type="number"
                value={form.limitAmount}
                onChange={(event) => setForm({ ...form, limitAmount: event.target.value })}
                fullWidth
              />
              <TextField
                label="Currency"
                value={form.limitCurrency}
                onChange={(event) => setForm({ ...form, limitCurrency: event.target.value })}
                sx={{ width: 120 }}
                slotProps={{ htmlInput: { maxLength: 3 } }}
              />
            </Stack>
            {!editing && (
              <Stack>
                <TextField label="Roles" size="small" value="" disabled />
                {[
                  'ADMIN',
                  'REQUESTER',
                  'APPROVER',
                  'PROCUREMENT_OFFICER',
                  'FINANCE',
                  'AUDITOR',
                ].map((role) => (
                  <FormControlLabel
                    key={role}
                    control={
                      <Checkbox
                        size="small"
                        checked={form.roles.includes(role)}
                        onChange={(event) =>
                          setForm((current) => ({
                            ...current,
                            roles: event.target.checked
                              ? [...current.roles, role]
                              : current.roles.filter((value) => value !== role),
                          }))
                        }
                      />
                    }
                    label={role}
                  />
                ))}
              </Stack>
            )}
            {editing && (
              <TextField
                select
                label="Active"
                value={form.active ? 'yes' : 'no'}
                onChange={(event) => setForm({ ...form, active: event.target.value === 'yes' })}
              >
                <MenuItem value="yes">Active</MenuItem>
                <MenuItem value="no">Inactive</MenuItem>
              </TextField>
            )}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setOpen(false)}>Cancel</Button>
          <Button
            variant="contained"
            disabled={
              !form.displayName ||
              !form.departmentId ||
              (!editing && (!form.email || form.roles.length === 0)) ||
              save.isPending
            }
            onClick={() => save.mutate()}
          >
            {editing ? 'Save' : 'Send invite'}
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
}
