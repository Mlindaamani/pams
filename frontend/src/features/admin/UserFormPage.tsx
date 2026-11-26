import { useEffect, useState } from 'react';
import { Link as RouterLink, useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Checkbox,
  Divider,
  FormControl,
  FormControlLabel,
  FormGroup,
  FormHelperText,
  Grid,
  MenuItem,
  Stack,
  Switch,
  TextField,
  Typography,
} from '@mui/material';
import { problemMessage } from '@/api/client';
import { Loading, PageHeader } from '@/components/Page';
import { toast } from '@/components/Toast';
import { VirtualizedCombobox } from '@/components/VirtualizedCombobox';
import { listDepartments } from '@/features/reference/api';
import { APP_ROLES, roleLabel } from './userRoles';
import { getUser, inviteUser, listUsers, updateUser } from './usersApi';

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

export function UserFormPage() {
  const { id } = useParams();
  const editing = Boolean(id);
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const detail = useQuery({
    queryKey: ['user', id],
    queryFn: () => getUser(id!),
    enabled: editing,
  });
  const departments = useQuery({ queryKey: ['departments'], queryFn: listDepartments });
  const users = useQuery({ queryKey: ['users'], queryFn: () => listUsers({ size: 100 }) });
  const [form, setForm] = useState<UserFormState>(emptyForm);
  const [loaded, setLoaded] = useState(!editing);

  useEffect(() => {
    if (!detail.data || loaded) return;
    setForm({
      email: detail.data.email ?? '',
      displayName: detail.data.displayName ?? '',
      departmentId: detail.data.departmentId ?? '',
      managerId: detail.data.managerId ?? '',
      limitAmount: detail.data.approvalLimit?.amount ?? '',
      limitCurrency: detail.data.approvalLimit?.currency ?? 'TZS',
      active: detail.data.active ?? true,
      roles: [...(detail.data.roles ?? [])].filter((role): role is string => Boolean(role)),
    });
    setLoaded(true);
  }, [detail.data, loaded]);

  const update = <K extends keyof UserFormState>(key: K, value: UserFormState[K]) =>
    setForm((current) => ({ ...current, [key]: value }));

  const save = useMutation({
    mutationFn: async () => {
      const approvalLimit = form.limitAmount
        ? { amount: form.limitAmount, currency: form.limitCurrency.toUpperCase() }
        : undefined;
      if (editing) {
        return updateUser(id!, {
          displayName: form.displayName.trim(),
          departmentId: form.departmentId,
          managerId: form.managerId || undefined,
          approvalLimit,
          active: form.active,
        });
      }
      return inviteUser({
        email: form.email.trim(),
        displayName: form.displayName.trim(),
        departmentId: form.departmentId,
        managerId: form.managerId || undefined,
        roles: form.roles,
        approvalLimit,
      });
    },
    onSuccess: () => {
      toast.success(editing ? 'User updated.' : 'Invitation sent.');
      void queryClient.invalidateQueries({ queryKey: ['users'] });
      void queryClient.invalidateQueries({ queryKey: ['user', id] });
      navigate('/admin/users');
    },
    onError: (error) => toast.error(problemMessage(error)),
  });

  if (editing && detail.isLoading) return <Loading />;
  if (editing && (detail.error || !detail.data)) {
    return <Alert severity="error">This user profile could not be loaded.</Alert>;
  }

  const valid =
    Boolean(form.displayName.trim()) &&
    Boolean(form.departmentId) &&
    (editing || (Boolean(form.email.trim()) && form.roles.length > 0)) &&
    !save.isPending;

  return (
    <>
      <PageHeader
        title={editing ? 'Edit user' : 'Invite user'}
        backTo="/admin/users"
        backLabel="Back to users"
      />
      <Card>
        <CardContent>
          <Stack spacing={3}>
            {!editing && (
              <Alert severity="info">
                The invited user receives a temporary password by email and can sign in immediately.
              </Alert>
            )}

            <Box>
              <Typography variant="h6">User details</Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                Basic identity and sign-in information.
              </Typography>
              <Grid container spacing={2}>
                <Grid size={{ xs: 12, sm: 6 }}>
                  <TextField
                    fullWidth
                    label="Email"
                    type="email"
                    value={form.email}
                    onChange={(event) => update('email', event.target.value)}
                    disabled={editing}
                    required={!editing}
                  />
                </Grid>
                <Grid size={{ xs: 12, sm: 6 }}>
                  <TextField
                    fullWidth
                    label="Display name"
                    value={form.displayName}
                    onChange={(event) => update('displayName', event.target.value)}
                    required
                  />
                </Grid>
              </Grid>
            </Box>

            <Divider />

            <Box>
              <Typography variant="h6">Organization</Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                Department, reporting line and approval authority.
              </Typography>
              <Grid container spacing={2}>
                <Grid size={{ xs: 12, sm: 6 }}>
                  <VirtualizedCombobox
                    fullWidth
                    label="Department"
                    value={form.departmentId}
                    onChange={(event) => update('departmentId', event.target.value)}
                    required
                  >
                    {(departments.data?.content ?? []).map((department) => (
                      <MenuItem key={department.id} value={department.id}>
                        {department.name}
                      </MenuItem>
                    ))}
                  </VirtualizedCombobox>
                </Grid>
                <Grid size={{ xs: 12, sm: 6 }}>
                  <VirtualizedCombobox
                    fullWidth
                    label="Manager"
                    value={form.managerId}
                    onChange={(event) => update('managerId', event.target.value)}
                  >
                    <MenuItem value="">No manager</MenuItem>
                    {(users.data?.content ?? [])
                      .filter((user) => user.id !== id)
                      .map((user) => (
                        <MenuItem key={user.id} value={user.id}>
                          {user.displayName}
                        </MenuItem>
                      ))}
                  </VirtualizedCombobox>
                </Grid>
                <Grid size={{ xs: 12, sm: 6 }}>
                  <TextField
                    fullWidth
                    label="Approval limit"
                    type="number"
                    value={form.limitAmount}
                    onChange={(event) => update('limitAmount', event.target.value)}
                  />
                </Grid>
                <Grid size={{ xs: 12, sm: 6 }}>
                  <TextField
                    fullWidth
                    label="Currency"
                    value={form.limitCurrency}
                    onChange={(event) => update('limitCurrency', event.target.value.toUpperCase())}
                    slotProps={{ htmlInput: { maxLength: 3 } }}
                  />
                </Grid>
                {editing && (
                  <Grid size={{ xs: 12 }}>
                    <FormControlLabel
                      control={
                        <Switch
                          checked={form.active}
                          onChange={(event) => update('active', event.target.checked)}
                        />
                      }
                      label={form.active ? 'Active' : 'Inactive'}
                    />
                  </Grid>
                )}
              </Grid>
            </Box>

            {!editing && (
              <>
                <Divider />
                <Box>
                  <Typography variant="h6">Roles</Typography>
                  <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
                    Grant only the permissions this user needs.
                  </Typography>
                  <FormControl component="fieldset" required error={form.roles.length === 0}>
                    <FormGroup>
                      <Grid container spacing={1}>
                        {APP_ROLES.map((role) => (
                          <Grid key={role} size={{ xs: 12, sm: 6, md: 4 }}>
                            <FormControlLabel
                              control={
                                <Checkbox
                                  checked={form.roles.includes(role)}
                                  onChange={(event) =>
                                    update(
                                      'roles',
                                      event.target.checked
                                        ? [...form.roles, role]
                                        : form.roles.filter((value) => value !== role),
                                    )
                                  }
                                />
                              }
                              label={roleLabel(role)}
                            />
                          </Grid>
                        ))}
                      </Grid>
                    </FormGroup>
                    {form.roles.length === 0 && (
                      <FormHelperText>Select at least one role.</FormHelperText>
                    )}
                  </FormControl>
                </Box>
              </>
            )}

            <Divider />
            <Stack direction="row" spacing={1}>
              <Button variant="contained" disabled={!valid} onClick={() => save.mutate()}>
                {save.isPending ? 'Saving…' : editing ? 'Save changes' : 'Send invitation'}
              </Button>
              <Button component={RouterLink} to="/admin/users" disabled={save.isPending}>
                Cancel
              </Button>
            </Stack>
          </Stack>
        </CardContent>
      </Card>
    </>
  );
}
