import { useState } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useAuth } from 'react-oidc-context';
import {
  Box,
  Button,
  Divider,
  ListItemIcon,
  Menu,
  MenuItem,
  Stack,
  Typography,
} from '@mui/material';
import PersonIcon from '@mui/icons-material/Person';
import SecurityIcon from '@mui/icons-material/Security';
import OpenInNewIcon from '@mui/icons-material/OpenInNew';
import LogoutIcon from '@mui/icons-material/Logout';
import { accountSecurityUrl } from '@/auth/accountSecurity';
import { UserAvatar } from '@/components/UserAvatar';
import { getMe } from '@/features/admin/usersApi';

export function UserMenu() {
  const auth = useAuth();
  const [anchor, setAnchor] = useState<HTMLElement | null>(null);
  const me = useQuery({
    queryKey: ['users', 'me'],
    queryFn: getMe,
    retry: false,
    retryOnMount: false,
    staleTime: 30_000,
    refetchOnWindowFocus: true,
  });
  const displayName =
    me.data?.displayName ??
    String(
      auth.user?.profile.name ??
        auth.user?.profile.preferred_username ??
        auth.user?.profile.email ??
        'User',
    );
  const email = me.data?.email ?? String(auth.user?.profile.email ?? '');

  const close = () => setAnchor(null);

  return (
    <>
      <Button
        color="inherit"
        aria-label="Account menu"
        onClick={(event) => setAnchor(event.currentTarget)}
        sx={{ minWidth: 0, px: { xs: 0.5, sm: 1 } }}
      >
        <UserAvatar name={displayName} size={28} sx={{ mr: { xs: 0, sm: 1 } }} />
        <Typography
          component="span"
          noWrap
          sx={{ display: { xs: 'none', sm: 'block' }, maxWidth: 160, fontWeight: 600 }}
        >
          {displayName}
        </Typography>
      </Button>
      <Menu
        anchorEl={anchor}
        open={Boolean(anchor)}
        onClose={close}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
        transformOrigin={{ vertical: 'top', horizontal: 'right' }}
        slotProps={{
          paper: {
            sx: {
              mt: 1,
              width: 288,
              maxWidth: 'calc(100vw - 24px)',
              borderRadius: 2,
              overflow: 'hidden',
            },
          },
        }}
      >
        <Box sx={{ px: 2, py: 1.5 }}>
          <Stack direction="row" spacing={1.5} alignItems="center">
            <UserAvatar name={displayName} size={40} />
            <Box sx={{ minWidth: 0 }}>
              <Typography variant="subtitle2" noWrap fontWeight={700}>
                {displayName}
              </Typography>
              <Typography variant="caption" color="text.secondary" noWrap display="block">
                {email || 'No email'}
              </Typography>
            </Box>
          </Stack>
        </Box>
        <Divider />
        <MenuItem component={RouterLink} to="/profile" onClick={close} sx={{ py: 1.1 }}>
          <ListItemIcon>
            <PersonIcon fontSize="small" />
          </ListItemIcon>
          My profile
        </MenuItem>
        <MenuItem
          component="a"
          href={accountSecurityUrl()}
          target="_blank"
          rel="noopener noreferrer"
          onClick={close}
          sx={{ py: 1.1 }}
        >
          <ListItemIcon>
            <SecurityIcon fontSize="small" />
          </ListItemIcon>
          Account security
          <OpenInNewIcon sx={{ fontSize: 15, ml: 'auto', color: 'text.secondary' }} />
        </MenuItem>
        <Divider />
        <MenuItem
          onClick={() => {
            close();
            void auth.signoutRedirect();
          }}
          sx={{ py: 1.1 }}
        >
          <ListItemIcon>
            <LogoutIcon fontSize="small" />
          </ListItemIcon>
          Sign out
        </MenuItem>
      </Menu>
    </>
  );
}
