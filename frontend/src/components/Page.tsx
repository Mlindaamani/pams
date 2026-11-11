import { Box, Card, CardContent, Grid, Skeleton, Stack, Typography } from '@mui/material';
import { type ReactNode } from 'react';

export function PageHeader({ title, actions }: { title: string; actions?: ReactNode }) {
  return (
    <Stack
      direction={{ xs: 'column', sm: 'row' }}
      justifyContent="space-between"
      alignItems={{ xs: 'stretch', sm: 'center' }}
      spacing={1}
      sx={{ mb: 2 }}
    >
      <Typography variant="h5" component="h1">
        {title}
      </Typography>
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
