import { useEffect, useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Divider,
  Grid,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import SecurityIcon from '@mui/icons-material/Security';
import OpenInNewIcon from '@mui/icons-material/OpenInNew';
import { useAuth } from 'react-oidc-context';
import { accountSecurityUrl } from '@/auth/accountSecurity';
import { Loading, PageHeader } from '@/components/Page';
import { toast } from '@/components/Toast';
import { UserAvatar } from '@/components/UserAvatar';
import { useBranding } from '@/branding/BrandingProvider';
import { money } from '@/lib/format';
import { rolesFromAccessToken } from '@/auth/ability';
import { useAbility } from '@/auth/AbilityProvider';
import {
  delegationCandidates,
  getMe,
  removeMyAvatar,
  saveMyProfile,
  uploadMyAvatar,
} from '@/features/admin/usersApi';
import { listDepartments } from '@/features/reference/api';

export function ProfilePage() {
  const auth = useAuth();
  const ability = useAbility();
  const { branding } = useBranding();
  const platformOnly = ability.can('read', 'Platform') && !ability.can('read', 'PurchaseRequest');
  const queryClient = useQueryClient();
  const fileInput = useRef<HTMLInputElement>(null);
  const me = useQuery({ queryKey: ['users', 'me'], queryFn: getMe, retry: false });
  const departments = useQuery({
    queryKey: ['departments'],
    queryFn: listDepartments,
    enabled: !platformOnly,
  });
  const colleagues = useQuery({
    queryKey: ['users', 'delegation-candidates'],
    queryFn: delegationCandidates,
    enabled: !platformOnly,
  });
  const [displayName, setDisplayName] = useState('');
  const [departmentId, setDepartmentId] = useState('');
  const [managerId, setManagerId] = useState('');

  const initialDisplayName =
    me.data?.displayName ?? String(auth.user?.profile.name ?? auth.user?.profile.email ?? '');
  const initialDepartmentId = me.data?.departmentId ?? '';
  const initialManagerId = me.data?.managerId ?? '';

  useEffect(() => {
    setDisplayName(initialDisplayName);
    setDepartmentId(initialDepartmentId);
    setManagerId(initialManagerId);
  }, [initialDisplayName, initialDepartmentId, initialManagerId]);

  const dirty =
    displayName !== initialDisplayName ||
    (!platformOnly && (departmentId !== initialDepartmentId || managerId !== initialManagerId));
  const canSave =
    Boolean(displayName.trim()) &&
    (platformOnly ? Boolean(me.data?.departmentId) : Boolean(departmentId)) &&
    dirty;

  const save = useMutation({
    mutationFn: () =>
      saveMyProfile({
        displayName: displayName.trim(),
        departmentId: platformOnly ? me.data!.departmentId! : departmentId,
        managerId: managerId || undefined,
      }),
    onSuccess: () => {
      toast.success('Profile saved.');
      void queryClient.invalidateQueries({ queryKey: ['users'] });
      void queryClient.invalidateQueries({ queryKey: ['branding'] });
    },
    onError: () => toast.error('Profile could not be saved.'),
  });
  const upload = useMutation({
    mutationFn: uploadMyAvatar,
    onSuccess: () => {
      toast.success('Profile picture updated.');
      void queryClient.invalidateQueries({ queryKey: ['users'] });
    },
  });
  const removeAvatar = useMutation({
    mutationFn: removeMyAvatar,
    onSuccess: () => {
      toast.success('Profile picture removed.');
      void queryClient.invalidateQueries({ queryKey: ['users'] });
    },
  });

  if (me.isLoading) return <Loading />;

  const roles = rolesFromAccessToken(auth.user?.access_token);
  const departmentName = departments.data?.content.find(
    (department) => department.id === me.data?.departmentId,
  )?.name;
  const managerName = colleagues.data?.find((user) => user.id === me.data?.managerId)?.displayName;

  const reset = () => {
    setDisplayName(initialDisplayName);
    setDepartmentId(initialDepartmentId);
    setManagerId(initialManagerId);
  };

  return (
    <>
      <PageHeader
        title="My profile"
        actions={
          <Button
            component="a"
            href={accountSecurityUrl()}
            target="_blank"
            rel="noopener noreferrer"
            size="small"
            variant="outlined"
            startIcon={<SecurityIcon />}
            endIcon={<OpenInNewIcon fontSize="small" />}
          >
            Account security
          </Button>
        }
      />
      <Grid container spacing={2}>
        <Grid size={{ xs: 12, md: 7 }}>
          <Card>
            <CardContent>
              <Stack spacing={2.5}>
                <Stack direction="row" spacing={2} alignItems="center">
                  <UserAvatar name={displayName || 'U'} size={72} />
                  <Box sx={{ minWidth: 0 }}>
                    <Typography variant="h6" noWrap>
                      {displayName || 'Your profile'}
                    </Typography>
                    <Typography variant="body2" color="text.secondary" noWrap>
                      {me.data?.email ?? auth.user?.profile.email ?? '—'}
                    </Typography>
                    <Stack direction="row" spacing={0.75} sx={{ mt: 0.75 }} flexWrap="wrap">
                      <Chip size="small" variant="outlined" label={branding.displayName} />
                      {platformOnly && (
                        <Chip size="small" color="primary" label="Platform administrator" />
                      )}
                    </Stack>
                  </Box>
                </Stack>

                {me.error && (
                  <Alert severity="info">
                    Complete your profile before delegating approvals or acting on approval steps.
                  </Alert>
                )}

                <Divider />
                <Box>
                  <Typography variant="subtitle2" sx={{ mb: 1 }}>
                    Profile picture
                  </Typography>
                  <Stack direction="row" spacing={1} alignItems="center">
                    <input
                      ref={fileInput}
                      type="file"
                      accept="image/jpeg,image/png,image/webp"
                      hidden
                      onChange={(event) => {
                        const file = event.target.files?.[0];
                        if (file) upload.mutate(file);
                        event.target.value = '';
                      }}
                    />
                    <Button
                      size="small"
                      variant="outlined"
                      disabled={!me.data || upload.isPending}
                      onClick={() => fileInput.current?.click()}
                    >
                      Upload picture
                    </Button>
                    {me.data?.hasAvatar && (
                      <Button
                        size="small"
                        color="error"
                        disabled={removeAvatar.isPending}
                        onClick={() => removeAvatar.mutate()}
                      >
                        Remove
                      </Button>
                    )}
                    <Typography variant="caption" color="text.secondary">
                      JPEG, PNG or WebP, up to 5MB.
                    </Typography>
                  </Stack>
                </Box>

                <Divider />
                <Box>
                  <Typography variant="h6" sx={{ mb: 2 }}>
                    Personal details
                  </Typography>
                  <Grid container spacing={2}>
                    <Grid size={{ xs: 12, sm: 6 }}>
                      <TextField
                        fullWidth
                        size="small"
                        label="Display name"
                        value={displayName}
                        onChange={(event) => setDisplayName(event.target.value)}
                        required
                      />
                    </Grid>
                    <Grid size={{ xs: 12, sm: 6 }}>
                      <TextField
                        fullWidth
                        size="small"
                        label="Email"
                        value={me.data?.email ?? auth.user?.profile.email ?? '—'}
                        disabled
                      />
                    </Grid>
                    {!platformOnly && (
                      <>
                        <Grid size={{ xs: 12, sm: 6 }}>
                          <TextField
                            select
                            fullWidth
                            size="small"
                            label="Department"
                            value={departmentId}
                            onChange={(event) => setDepartmentId(event.target.value)}
                            required
                            helperText="Required for approvals and delegation."
                          >
                            {(departments.data?.content ?? []).map((department) => (
                              <MenuItem key={department.id} value={department.id}>
                                {department.name}
                              </MenuItem>
                            ))}
                          </TextField>
                        </Grid>
                        <Grid size={{ xs: 12, sm: 6 }}>
                          <TextField
                            select
                            fullWidth
                            size="small"
                            label="Manager"
                            value={managerId}
                            onChange={(event) => setManagerId(event.target.value)}
                          >
                            <MenuItem value="">No manager</MenuItem>
                            {(colleagues.data ?? [])
                              .filter((user) => user.id !== me.data?.id)
                              .map((user) => (
                                <MenuItem key={user.id} value={user.id}>
                                  {user.displayName}
                                </MenuItem>
                              ))}
                          </TextField>
                        </Grid>
                      </>
                    )}
                  </Grid>
                </Box>

                <Stack direction="row" spacing={1}>
                  <Button
                    variant="contained"
                    disabled={!canSave || save.isPending}
                    onClick={() => save.mutate()}
                  >
                    Save profile
                  </Button>
                  <Button disabled={!dirty || save.isPending} onClick={reset}>
                    Reset
                  </Button>
                </Stack>
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid size={{ xs: 12, md: 5 }}>
          <Card>
            <CardContent>
              <Typography variant="h6" sx={{ mb: 2 }}>
                Account summary
              </Typography>
              <Stack spacing={1.75}>
                <SummaryRow label="Username" value={auth.user?.profile.preferred_username ?? '—'} />
                <SummaryRow label="Workspace" value={branding.displayName} />
                {!platformOnly && (
                  <>
                    <SummaryRow label="Department" value={departmentName ?? '—'} />
                    <SummaryRow label="Manager" value={managerName ?? '—'} />
                    <SummaryRow
                      label="Approval limit"
                      value={me.data?.approvalLimit ? money(me.data.approvalLimit) : '—'}
                    />
                  </>
                )}
                <Box>
                  <Typography variant="caption" color="text.secondary">
                    Roles
                  </Typography>
                  <Stack
                    direction="row"
                    spacing={0.75}
                    sx={{ mt: 0.75 }}
                    flexWrap="wrap"
                    useFlexGap
                  >
                    {roles.length === 0 ? (
                      <Typography variant="body2">No roles assigned</Typography>
                    ) : (
                      roles.map((role) => (
                        <Chip
                          key={role}
                          size="small"
                          color="primary"
                          variant="outlined"
                          label={role}
                        />
                      ))
                    )}
                  </Stack>
                </Box>
              </Stack>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </>
  );
}

function SummaryRow({ label, value }: { label: string; value: string }) {
  return (
    <Stack direction="row" justifyContent="space-between" spacing={2}>
      <Typography variant="body2" color="text.secondary">
        {label}
      </Typography>
      <Typography variant="body2" sx={{ textAlign: 'right', wordBreak: 'break-word' }}>
        {value}
      </Typography>
    </Stack>
  );
}
