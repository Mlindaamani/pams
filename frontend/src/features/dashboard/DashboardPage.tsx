import { useQuery } from '@tanstack/react-query';
import { Link as RouterLink } from 'react-router-dom';
import { Button, Card, CardContent, Grid, Stack, Typography } from '@mui/material';
import { useAbility } from '@/auth/AbilityProvider';
import { AreaChart, DonutChart } from '@/components/charts/Charts';
import DescriptionIcon from '@mui/icons-material/Description';
import ApprovalIcon from '@mui/icons-material/Approval';
import NotificationsActiveIcon from '@mui/icons-material/NotificationsActive';
import ShowChartIcon from '@mui/icons-material/ShowChart';
import PieChartIcon from '@mui/icons-material/PieChart';
import InboxIcon from '@mui/icons-material/Inbox';
import { Can } from '@/auth/AbilityProvider';
import { PageHeader, Loading } from '@/components/Page';
import { listRequests } from '@/features/requests/api';
import { approvalInbox } from '@/features/approvals/api';
import { unreadCount } from '@/features/notifications/api';
import { commitmentsReport, spendReport } from '@/features/reports/api';
import { money, dateTime } from '@/lib/format';
import type { PurchaseRequest } from '@/api/types';

export function DashboardPage() {
  const ability = useAbility();
  const canViewReports = ability.can('read', 'Report');
  const monthlySpend = useQuery({
    queryKey: ['reports', 'spend', 'month'],
    queryFn: () => spendReport('month'),
    enabled: canViewReports,
  });
  const departmentSpend = useQuery({
    queryKey: ['reports', 'spend', 'department'],
    queryFn: () => spendReport('department'),
    enabled: canViewReports,
  });
  const commitments = useQuery({
    queryKey: ['reports', 'commitments'],
    queryFn: commitmentsReport,
    enabled: canViewReports,
  });
  const requests = useQuery({
    queryKey: ['requests', 'dashboard'],
    queryFn: () => listRequests({ page: 0, size: 5 }),
  });
  const inbox = useQuery({ queryKey: ['approvals', 'inbox'], queryFn: approvalInbox });
  const unread = useQuery({ queryKey: ['notifications', 'unread-count'], queryFn: unreadCount });

  if (requests.isLoading) return <Loading />;

  const myRequests = (requests.data?.content ?? []) as PurchaseRequest[];
  const monthlyPoints = (monthlySpend.data?.rows ?? [])
    .map((row) => ({ label: row.label ?? '', value: Number(row.total ?? 0) }))
    .sort((left, right) => left.label.localeCompare(right.label));
  const departmentRows = [...(departmentSpend.data?.rows ?? [])].sort(
    (left, right) => Number(right.total ?? 0) - Number(left.total ?? 0),
  );
  const topDepartments = departmentRows.slice(0, 6).map((row) => ({
    label: row.label ?? 'Unknown',
    value: Number(row.total ?? 0),
  }));
  const otherDepartment = departmentRows
    .slice(6)
    .reduce((sum, row) => sum + Number(row.total ?? 0), 0);
  const departmentPoints =
    otherDepartment > 0
      ? [...topDepartments, { label: 'Other', value: otherDepartment }]
      : topDepartments;
  const commitmentRows = [...(commitments.data?.rows ?? [])].sort(
    (left, right) => Number(right.committed?.amount ?? 0) - Number(left.committed?.amount ?? 0),
  );
  const topCommitments = commitmentRows.slice(0, 6).map((row) => ({
    label: `${row.departmentName} ${row.fiscalYear}`,
    value: Number(row.committed?.amount ?? 0),
  }));
  const otherCommitment = commitmentRows
    .slice(6)
    .reduce((sum, row) => sum + Number(row.committed?.amount ?? 0), 0);
  const commitmentPoints =
    otherCommitment > 0
      ? [...topCommitments, { label: 'Other', value: otherCommitment }]
      : topCommitments;

  return (
    <>
      <PageHeader
        title="Dashboard"
        actions={
          <Can I="create" a="PurchaseRequest">
            <Button component={RouterLink} to="/requests/new" variant="contained">
              New request
            </Button>
          </Can>
        }
      />
      <Grid container spacing={2} sx={{ mb: 3 }}>
        <Grid size={{ xs: 12, sm: 4 }}>
          <Card>
            <CardContent>
              <Stack direction="row" spacing={1} alignItems="center">
                <DescriptionIcon color="primary" fontSize="small" />
                <Typography color="text.secondary">Recent requests</Typography>
              </Stack>
              <Typography variant="h4">{requests.data?.totalElements ?? 0}</Typography>
            </CardContent>
          </Card>
        </Grid>
        <Grid size={{ xs: 12, sm: 4 }}>
          <Card>
            <CardContent>
              <Stack direction="row" spacing={1} alignItems="center">
                <ApprovalIcon color="secondary" fontSize="small" />
                <Typography color="text.secondary">Pending my approval</Typography>
              </Stack>
              <Typography variant="h4">{inbox.data?.length ?? 0}</Typography>
            </CardContent>
          </Card>
        </Grid>
        <Grid size={{ xs: 12, sm: 4 }}>
          <Card>
            <CardContent>
              <Stack direction="row" spacing={1} alignItems="center">
                <NotificationsActiveIcon color="warning" fontSize="small" />
                <Typography color="text.secondary">Unread notifications</Typography>
              </Stack>
              <Typography variant="h4">{unread.data?.unread ?? 0}</Typography>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      {canViewReports && (
        <Grid container spacing={2} sx={{ mb: 3 }}>
          <Grid size={{ xs: 12, lg: 8 }}>
            <Card>
              <CardContent>
                <Stack direction="row" spacing={1} alignItems="center">
                  <ShowChartIcon color="primary" fontSize="small" />
                  <Typography variant="h6">Spend by month</Typography>
                </Stack>
                <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
                  Issued purchase orders
                </Typography>
                <AreaChart data={monthlyPoints} ariaLabel="Spend by month area chart" />
              </CardContent>
            </Card>
          </Grid>
          <Grid size={{ xs: 12, lg: 4 }}>
            <Card>
              <CardContent>
                <Stack direction="row" spacing={1} alignItems="center">
                  <PieChartIcon color="secondary" fontSize="small" />
                  <Typography variant="h6">Spend by department</Typography>
                </Stack>
                <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
                  Share of issued spend
                </Typography>
                <DonutChart
                  data={departmentPoints}
                  ariaLabel="Spend by department donut chart"
                  size={190}
                />
              </CardContent>
            </Card>
          </Grid>
          <Grid size={{ xs: 12 }}>
            <Card>
              <CardContent>
                <Stack direction="row" spacing={1} alignItems="center">
                  <PieChartIcon color="primary" fontSize="small" />
                  <Typography variant="h6">Committed spend by department</Typography>
                </Stack>
                <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
                  Purchase orders converted from reservations
                </Typography>
                <DonutChart
                  data={commitmentPoints}
                  ariaLabel="Committed spend by department donut chart"
                  size={210}
                />
              </CardContent>
            </Card>
          </Grid>
        </Grid>
      )}

      <Card>
        <CardContent>
          <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 2 }}>
            <InboxIcon color="primary" fontSize="small" />
            <Typography variant="h6">Latest requests</Typography>
          </Stack>
          <Stack spacing={1}>
            {myRequests.length === 0 && (
              <Typography color="text.secondary">No requests yet.</Typography>
            )}
            {myRequests.map((request) => (
              <Stack
                key={request.id}
                direction={{ xs: 'column', sm: 'row' }}
                justifyContent="space-between"
                sx={{ borderBottom: 1, borderColor: 'divider', py: 1 }}
              >
                <Stack>
                  <Typography component={RouterLink} to={`/requests/${request.id}`} color="primary">
                    {request.title}
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    {request.status} · {dateTime(request.createdAt)}
                  </Typography>
                </Stack>
                <Typography>{money(request.total)}</Typography>
              </Stack>
            ))}
          </Stack>
        </CardContent>
      </Card>
    </>
  );
}
