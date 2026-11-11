import { useQuery } from '@tanstack/react-query';
import { Alert, Box, Button, Card, CardContent, Grid, Stack, Typography } from '@mui/material';
import RefreshIcon from '@mui/icons-material/Refresh';
import StorageIcon from '@mui/icons-material/Storage';
import OutboxIcon from '@mui/icons-material/Outbox';
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutline';
import ApprovalIcon from '@mui/icons-material/Approval';
import NotificationsActiveIcon from '@mui/icons-material/NotificationsActive';
import InfoOutlinedIcon from '@mui/icons-material/InfoOutlined';
import { Loading, PageHeader } from '@/components/Page';
import { dateTime } from '@/lib/format';
import { getSystemStatus } from './api';

function MetricCard({
  title,
  value,
  icon,
  color = 'primary.main',
}: {
  title: string;
  value: string | number;
  icon: React.ReactNode;
  color?: string;
}) {
  return (
    <Card>
      <CardContent>
        <Stack direction="row" spacing={1} alignItems="center" sx={{ color }}>
          {icon}
          <Typography color="text.secondary">{title}</Typography>
        </Stack>
        <Typography variant="h4" sx={{ mt: 1 }}>
          {value}
        </Typography>
      </CardContent>
    </Card>
  );
}

export function SystemPage() {
  const status = useQuery({
    queryKey: ['system', 'status'],
    queryFn: getSystemStatus,
    refetchInterval: 30_000,
  });

  return (
    <>
      <PageHeader
        title="System status"
        actions={
          <Stack direction="row" spacing={1}>
            <Button
              variant="outlined"
              startIcon={<RefreshIcon />}
              onClick={() => void status.refetch()}
              disabled={status.isFetching}
            >
              Refresh
            </Button>
          </Stack>
        }
      />
      {status.isLoading ? (
        <Loading rows={3} />
      ) : status.error || !status.data ? (
        <Alert severity="error">System status could not be loaded.</Alert>
      ) : (
        <>
          <Alert severity={status.data.status === 'UP' ? 'success' : 'warning'} sx={{ mb: 2 }}>
            API is {status.data.status}. Database: {status.data.database}. Last checked{' '}
            {dateTime(status.data.timestamp)}.
          </Alert>
          <Grid container spacing={2}>
            <Grid size={{ xs: 12, sm: 6, lg: 4 }}>
              <MetricCard
                title="Database"
                value={status.data.database ?? '—'}
                icon={<StorageIcon />}
              />
            </Grid>
            <Grid size={{ xs: 12, sm: 6, lg: 4 }}>
              <MetricCard
                title="Outbox pending"
                value={status.data.outboxPending ?? 0}
                icon={<OutboxIcon />}
                color="secondary.main"
              />
            </Grid>
            <Grid size={{ xs: 12, sm: 6, lg: 4 }}>
              <MetricCard
                title="Outbox dead letters"
                value={status.data.outboxDeadLettered ?? 0}
                icon={<ErrorOutlineIcon />}
                color="error.main"
              />
            </Grid>
            <Grid size={{ xs: 12, sm: 6, lg: 4 }}>
              <MetricCard
                title="Pending approvals"
                value={status.data.pendingApprovals ?? 0}
                icon={<ApprovalIcon />}
              />
            </Grid>
            <Grid size={{ xs: 12, sm: 6, lg: 4 }}>
              <MetricCard
                title="Unread notifications"
                value={status.data.unreadNotifications ?? 0}
                icon={<NotificationsActiveIcon />}
                color="warning.main"
              />
            </Grid>
            <Grid size={{ xs: 12, sm: 6, lg: 4 }}>
              <MetricCard
                title="Version"
                value={status.data.version ?? 'dev'}
                icon={<InfoOutlinedIcon />}
                color="text.secondary"
              />
            </Grid>
          </Grid>

          <Card sx={{ mt: 2 }}>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Observability
              </Typography>
              <Box>
                <Typography variant="body2" color="text.secondary">
                  Outbox polling, approval escalation and HTTP/JVM metrics are available on the
                  Actuator management port. Traces are exported over OTLP to Tempo.
                </Typography>
              </Box>
            </CardContent>
          </Card>
        </>
      )}
    </>
  );
}
