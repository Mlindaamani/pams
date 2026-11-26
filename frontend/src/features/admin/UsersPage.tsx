import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button, Chip, Stack } from '@mui/material';
import { useNavigate } from 'react-router-dom';
import { Can } from '@/auth/AbilityProvider';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { DataTable } from '@/components/DataTable';
import { EmptyState, Loading, PageHeader } from '@/components/Page';
import { toast } from '@/components/Toast';
import { money } from '@/lib/format';
import type { UserProfile } from '@/api/types';
import { listDepartments } from '@/features/reference/api';
import { roleLabel } from './userRoles';
import { deleteUser, listUsers } from './usersApi';

export function UsersPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const users = useQuery({ queryKey: ['users'], queryFn: () => listUsers({ size: 100 }) });
  const departments = useQuery({ queryKey: ['departments'], queryFn: listDepartments });
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

  const remove = useMutation({
    mutationFn: (id: string) => deleteUser(id),
    onSuccess: () => {
      toast.success('User removed.');
      setDeleting(null);
      void queryClient.invalidateQueries({ queryKey: ['users'] });
    },
  });

  if (users.isLoading) return <Loading />;
  const rows = users.data?.content ?? [];

  return (
    <>
      <PageHeader
        title="Users"
        actions={
          <Can I="manage" a="User">
            <Button variant="contained" onClick={() => navigate('/admin/users/new')}>
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
              render: (user) => user.displayName || '—',
              sortValue: (user) => user.displayName ?? '',
            },
            {
              key: 'email',
              label: 'Email',
              render: (user) => user.email,
              sortValue: (user) => user.email ?? '',
            },
            {
              key: 'roles',
              label: 'Roles',
              render: (user) =>
                (user.roles ?? []).length === 0 ? (
                  '—'
                ) : (
                  <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
                    {(user.roles ?? []).map((role) => (
                      <Chip key={role} size="small" variant="outlined" label={roleLabel(role)} />
                    ))}
                  </Stack>
                ),
              sortValue: (user) => (user.roles ?? []).join(','),
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
              render: (user) => (
                <Chip
                  size="small"
                  variant="outlined"
                  color={user.active ? 'success' : 'default'}
                  label={user.active ? 'Active' : 'Inactive'}
                />
              ),
            },
            {
              key: 'actions',
              label: 'Actions',
              align: 'right',
              width: 150,
              render: (user) => (
                <Can I="manage" a="User">
                  <Stack direction="row" spacing={1} justifyContent="flex-end">
                    <Button size="small" onClick={() => navigate(`/admin/users/${user.id}/edit`)}>
                      Edit
                    </Button>
                    <Button size="small" color="error" onClick={() => setDeleting(user)}>
                      Remove
                    </Button>
                  </Stack>
                </Can>
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
          if (deleting) remove.mutate(deleting.id!);
        }}
      />
    </>
  );
}
