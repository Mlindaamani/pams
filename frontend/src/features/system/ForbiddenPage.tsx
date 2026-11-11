import { Box, Button, Stack, Typography } from '@mui/material';
import { Link as RouterLink } from 'react-router-dom';

export function ForbiddenPage() {
  return (
    <Stack alignItems="center" justifyContent="center" spacing={2} sx={{ minHeight: '60vh' }}>
      <Box component="img" src="/logo/pams-icon.svg" alt="PAMS" sx={{ width: 72, height: 72 }} />
      <Typography variant="h3" fontWeight={700}>
        403
      </Typography>
      <Typography color="text.secondary">
        You do not have permission to view this page, or your subscription plan does not include
        this feature.
      </Typography>
      <Button component={RouterLink} to="/" variant="contained">
        Back to dashboard
      </Button>
    </Stack>
  );
}
