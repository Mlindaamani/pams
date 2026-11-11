import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button, Grid, MenuItem, TextField, Typography } from '@mui/material';
import ClearIcon from '@mui/icons-material/Clear';
import { DataTable } from '@/components/DataTable';
import { Loading, PageHeader } from '@/components/Page';
import { toast } from '@/components/Toast';
import { problemMessage } from '@/api/client';
import { getAccessConfig, getRolePermissions, setRolePermission } from './accessConfigApi';

type AssignmentFilter = 'all' | 'assigned' | 'available';

export function RolesPermissionsPage() {
  const queryClient = useQueryClient();
  const [selectedRole, setSelectedRole] = useState('');
  const [search, setSearch] = useState('');
  const [assignment, setAssignment] = useState<AssignmentFilter>('all');
  const [module, setModule] = useState('all');
  const access = useQuery({ queryKey: ['access-config'], queryFn: getAccessConfig });

  useEffect(() => {
    if (!selectedRole && access.data?.roles.length) {
      const firstEditable = access.data.roles.find((role) => role.roleName !== 'ADMIN');
      setSelectedRole(firstEditable?.roleName ?? access.data.roles[0].roleName);
    }
  }, [access.data, selectedRole]);

  const permissions = useQuery({
    queryKey: ['role-permissions', selectedRole, search, assignment, module],
    queryFn: () =>
      getRolePermissions(selectedRole, {
        q: search || undefined,
        assignment,
        module: module === 'all' ? undefined : module,
      }),
    enabled: Boolean(selectedRole),
  });

  const toggle = useMutation({
    mutationFn: (input: { code: string; assigned: boolean }) =>
      setRolePermission(selectedRole, input.code, input.assigned),
    onSuccess: (_result, input) => {
      toast.success(input.assigned ? 'Permission assigned.' : 'Permission revoked.');
      void queryClient.invalidateQueries({ queryKey: ['role-permissions'] });
    },
    onError: (error) => toast.error(problemMessage(error)),
  });

  if (access.isLoading) return <Loading />;

  const isAdmin = selectedRole === 'ADMIN';
  const rows = permissions.data?.permissions ?? [];
  const filtersActive = Boolean(search) || assignment !== 'all' || module !== 'all';

  const clearFilters = () => {
    setSearch('');
    setAssignment('all');
    setModule('all');
  };

  return (
    <>
      <PageHeader title="Roles & permissions" />
      <Grid container spacing={1.5} sx={{ mb: 2 }}>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <TextField
            size="small"
            fullWidth
            label="Search permissions"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
          />
        </Grid>
        <Grid size={{ xs: 12, sm: 6, md: 2 }}>
          <TextField
            select
            size="small"
            fullWidth
            label="Assignment"
            value={assignment}
            onChange={(event) => setAssignment(event.target.value as AssignmentFilter)}
          >
            <MenuItem value="all">All permissions</MenuItem>
            <MenuItem value="assigned">Assigned</MenuItem>
            <MenuItem value="available">Available</MenuItem>
          </TextField>
        </Grid>
        <Grid size={{ xs: 12, sm: 6, md: 2 }}>
          <TextField
            select
            size="small"
            fullWidth
            label="Module"
            value={module}
            onChange={(event) => setModule(event.target.value)}
          >
            <MenuItem value="all">All modules</MenuItem>
            {(permissions.data?.modules ?? []).map((value) => (
              <MenuItem key={value} value={value}>
                {value.replaceAll('_', ' ')}
              </MenuItem>
            ))}
          </TextField>
        </Grid>
        <Grid size={{ xs: 12, sm: 6, md: 1.5 }} sx={{ display: 'flex', alignItems: 'center' }}>
          <Button
            size="small"
            startIcon={<ClearIcon />}
            disabled={!filtersActive}
            onClick={clearFilters}
          >
            Clear
          </Button>
        </Grid>
        <Grid size={{ xs: 12, sm: 6, md: 3.5 }}>
          <TextField
            select
            size="small"
            fullWidth
            label="Role"
            value={selectedRole}
            onChange={(event) => setSelectedRole(event.target.value)}
          >
            {(access.data?.roles ?? []).map((role) => (
              <MenuItem key={role.roleName} value={role.roleName}>
                {role.displayName}
              </MenuItem>
            ))}
          </TextField>
        </Grid>
      </Grid>

      {permissions.isLoading ? (
        <Loading rows={5} />
      ) : permissions.error ? (
        <Typography color="error">Permissions could not be loaded.</Typography>
      ) : (
        <DataTable
          ariaLabel="Role permissions"
          rows={rows}
          key={selectedRole}
          getRowId={(row) => row.code}
          selectable={false}
          emptyMessage="No permissions match these filters."
          columns={[
            {
              key: 'status',
              label: 'Status',
              render: (row) => (
                <Typography
                  variant="body2"
                  fontWeight={600}
                  color={row.assigned ? 'success.main' : 'text.secondary'}
                >
                  {row.assigned ? 'Assigned' : 'Available'}
                </Typography>
              ),
              sortValue: (row) => (row.assigned ? 'Assigned' : 'Available'),
            },
            {
              key: 'module',
              label: 'Module',
              render: (row) => (
                <Typography variant="body2" sx={{ textTransform: 'capitalize' }}>
                  {row.module.replaceAll('_', ' ')}
                </Typography>
              ),
              sortValue: (row) => row.module,
            },
            {
              key: 'permission',
              label: 'Permission',
              render: (row) => (
                <Typography variant="body2" fontWeight={600}>
                  {row.code}
                </Typography>
              ),
              sortValue: (row) => row.code,
            },
            {
              key: 'description',
              label: 'Description',
              render: (row) => (
                <Typography variant="body2" color="text.secondary">
                  {row.description}
                </Typography>
              ),
              sortValue: (row) => row.description,
            },
            {
              key: 'action',
              label: 'Action',
              align: 'right',
              render: (row) =>
                row.assigned ? (
                  <Button
                    size="small"
                    color="error"
                    disabled={isAdmin || toggle.isPending}
                    onClick={() => toggle.mutate({ code: row.code, assigned: false })}
                  >
                    Revoke
                  </Button>
                ) : (
                  <Button
                    size="small"
                    variant="outlined"
                    disabled={isAdmin || toggle.isPending}
                    onClick={() => toggle.mutate({ code: row.code, assigned: true })}
                  >
                    Assign
                  </Button>
                ),
            },
          ]}
        />
      )}
    </>
  );
}
