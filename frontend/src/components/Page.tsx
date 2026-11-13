import {
  Box,
  Card,
  CardContent,
  Grid,
  IconButton,
  Skeleton,
  Stack,
  Tooltip,
  Typography,
} from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import { Link as RouterLink } from 'react-router-dom';
import { type ReactNode } from 'react';

interface PageHeaderProps {
  title: string;
  actions?: ReactNode;
  backTo?: string;
  backLabel?: string;
}

export function PageHeader({ title, actions, backTo, backLabel = 'Back' }: PageHeaderProps) {
  return (
    <Stack
      direction={{ xs: 'column', sm: 'row' }}
      justifyContent="space-between"
      alignItems={{ xs: 'stretch', sm: 'center' }}
      spacing={1}
      sx={{ mb: 2 }}
    >
      <Stack direction="row" alignItems="center" spacing={1} sx={{ minWidth: 0 }}>
        {backTo && (
          <Tooltip title={backLabel}>
            <IconButton
              component={RouterLink}
              to={backTo}
              aria-label={backLabel}
              size="small"
              edge="start"
              sx={{ color: 'primary.main' }}
            >
              <ArrowBackIcon />
            </IconButton>
          </Tooltip>
        )}
        <Typography variant="h5" component="h1" sx={{ minWidth: 0 }}>
          {title}
        </Typography>
      </Stack>
      {actions}
    </Stack>
  );
}

export function Loading({ rows = 6 }: { rows?: number }) {
  return (
    <Box aria-busy="true" aria-label="Loading content">
      <Skeleton variant="text" width="38%" height={44} sx={{ mb: 2 }} />
      <Grid container spacing={2} sx={{ mb: 2 }}>
        {[0, 1, 2].map((item) => (
          <Grid key={item} size={{ xs: 12, sm: 4 }}>
            <Skeleton variant="rounded" height={92} />
          </Grid>
        ))}
      </Grid>
      <Card>
        <CardContent>
          <Stack spacing={1.5}>
            <Skeleton variant="text" width="30%" height={28} />
            {Array.from({ length: rows }).map((_, index) => (
              <Skeleton key={index} variant="rounded" height={34} />
            ))}
          </Stack>
        </CardContent>
      </Card>
    </Box>
  );
}

export function EmptyState({ message }: { message: string }) {
  return (
    <Box sx={{ py: 6, textAlign: 'center', color: 'text.secondary' }}>
      <Typography>{message}</Typography>
    </Box>
  );
}
