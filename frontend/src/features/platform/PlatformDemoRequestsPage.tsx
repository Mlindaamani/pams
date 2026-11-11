import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  Box,
  Card,
  CardContent,
  Chip,
  Grid,
  Link,
  MenuItem,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import { DataTable } from '@/components/DataTable';
import { Loading, PageHeader } from '@/components/Page';
import { dateTime } from '@/lib/format';
import { listDemoRequests, type DemoRequestStatus } from './api';

const statuses: DemoRequestStatus[] = ['NEW', 'CONTACTED', 'QUALIFIED', 'CLOSED'];

const statusColor: Record<DemoRequestStatus, 'default' | 'info' | 'warning' | 'success'> = {
  NEW: 'info',
  CONTACTED: 'warning',
  QUALIFIED: 'success',
  CLOSED: 'default',
};

export function PlatformDemoRequestsPage() {
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState('');
  const requests = useQuery({
    queryKey: ['platform-demo-requests', search, status],
    queryFn: () =>
      listDemoRequests({
        q: search || undefined,
        status: (status || undefined) as DemoRequestStatus | undefined,
        size: 100,
      }),
  });

  if (requests.isLoading) return <Loading />;
  if (requests.error) {
    return <Typography color="error">Demo requests could not be loaded.</Typography>;
  }

  return (
    <>
      <PageHeader title="Demo requests" />
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        Inbound demo leads submitted from the public website.
      </Typography>

      <Card sx={{ mb: 2 }}>
        <CardContent>
          <Grid container spacing={2}>
            <Grid size={{ xs: 12, md: 5 }}>
              <TextField
                size="small"
                fullWidth
                label="Search name, email or company"
                value={search}
                onChange={(event) => setSearch(event.target.value)}
              />
            </Grid>
            <Grid size={{ xs: 12, md: 3 }}>
              <TextField
                select
                size="small"
                fullWidth
                label="Status"
                value={status}
                onChange={(event) => setStatus(event.target.value)}
              >
                <MenuItem value="">All statuses</MenuItem>
                {statuses.map((value) => (
                  <MenuItem key={value} value={value}>
                    {labelStatus(value)}
                  </MenuItem>
                ))}
              </TextField>
            </Grid>
            <Grid size={{ xs: 12, md: 4 }} sx={{ display: 'flex', alignItems: 'center' }}>
              <Typography variant="body2" color="text.secondary">
                {requests.data?.totalElements ?? 0} demo request(s)
              </Typography>
            </Grid>
          </Grid>
        </CardContent>
      </Card>

      <DataTable
        ariaLabel="Demo requests"
        selectable={false}
        rows={requests.data?.content ?? []}
        getRowId={(request) => request.id ?? ''}
        emptyMessage="No demo requests match these filters."
        columns={[
          {
            key: 'createdAt',
            label: 'Received',
            render: (request) => dateTime(request.createdAt),
            sortValue: (request) => request.createdAt ?? '',
          },
          {
            key: 'prospect',
            label: 'Prospect',
            render: (request) => (
              <Box>
                <Typography variant="body2" fontWeight={600}>
                  {request.name}
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  {request.company}
                </Typography>
              </Box>
            ),
            sortValue: (request) => request.name ?? '',
          },
          {
            key: 'contact',
            label: 'Contact',
            render: (request) => (
              <Box>
                <Link href={`mailto:${request.email}`} variant="body2">
                  {request.email}
                </Link>
                {request.phone && (
                  <Typography variant="caption" color="text.secondary" display="block">
                    {request.phone}
                  </Typography>
                )}
              </Box>
            ),
          },
          {
            key: 'message',
            label: 'Message',
            render: (request) =>
              request.message ? (
                <Tooltip title={request.message}>
                  <Typography variant="body2" color="text.secondary" noWrap sx={{ maxWidth: 360 }}>
                    {request.message}
                  </Typography>
                </Tooltip>
              ) : (
                <Typography variant="body2" color="text.disabled">
                  No message
                </Typography>
              ),
          },
          {
            key: 'status',
            label: 'Status',
            render: (request) => <StatusChip status={request.status} />,
            sortValue: (request) => request.status ?? '',
          },
        ]}
      />
    </>
  );
}

function StatusChip({ status }: { status?: string }) {
  const value = (status ?? 'NEW') as DemoRequestStatus;
  return (
    <Chip
      size="small"
      label={labelStatus(value)}
      color={statusColor[value] ?? 'default'}
      variant={value === 'NEW' ? 'filled' : 'outlined'}
    />
  );
}

function labelStatus(status: DemoRequestStatus): string {
  return status.charAt(0) + status.slice(1).toLowerCase();
}
