import { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Grid,
  LinearProgress,
  MenuItem,
  Stack,
  Tab,
  Tabs,
  TextField,
  Typography,
} from '@mui/material';
import AssessmentIcon from '@mui/icons-material/Assessment';
import PaymentsIcon from '@mui/icons-material/Payments';
import ShoppingCartIcon from '@mui/icons-material/ShoppingCart';
import CategoryIcon from '@mui/icons-material/Category';
import ScheduleIcon from '@mui/icons-material/Schedule';
import DoneAllIcon from '@mui/icons-material/DoneAll';
import TimerIcon from '@mui/icons-material/Timer';
import WarningAmberIcon from '@mui/icons-material/WarningAmber';
import AccountBalanceIcon from '@mui/icons-material/AccountBalance';
import LockClockIcon from '@mui/icons-material/LockClock';
import SavingsIcon from '@mui/icons-material/Savings';
import DownloadIcon from '@mui/icons-material/Download';
import { DataTable } from '@/components/DataTable';
import { Can } from '@/auth/AbilityProvider';
import { Loading, PageHeader } from '@/components/Page';
import { AreaChart, DonutChart } from '@/components/charts/Charts';
import { downloadBlob, money } from '@/lib/format';
import type { CommitmentRow, SpendRow } from '@/api/types';
import { commitmentsReport, cycleTimeReport, spendCsv, spendReport } from './api';

const SPEND_GROUPS = ['department', 'vendor', 'category', 'month'];
const CYCLE_GROUPS = ['policy', 'approver'];

export function ReportsPage() {
  const [tab, setTab] = useState(0);
  const [spendGroup, setSpendGroup] = useState('department');
  const [cycleGroup, setCycleGroup] = useState('policy');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');

  const fromIso = from ? new Date(from).toISOString() : undefined;
  const toIso = to ? new Date(to).toISOString() : undefined;

  const spend = useQuery({
    queryKey: ['reports', 'spend', spendGroup, from, to],
    queryFn: () => spendReport(spendGroup, fromIso, toIso),
    enabled: tab === 0,
  });
  const cycle = useQuery({
    queryKey: ['reports', 'cycle', cycleGroup, from, to],
    queryFn: () => cycleTimeReport(cycleGroup, fromIso, toIso),
    enabled: tab === 1,
  });
  const commitments = useQuery({
    queryKey: ['reports', 'commitments'],
    queryFn: commitmentsReport,
    enabled: tab === 2,
  });

  const spendRows = useMemo(() => spend.data?.rows ?? [], [spend.data?.rows]);
  const spendTotals = useMemo(() => moneyByCurrency(spendRows), [spendRows]);
  const spendOrders = spendRows.reduce((sum, row) => sum + (row.orderCount ?? 0), 0);
  const spendChart = useMemo(() => {
    const points = spendRows.map((row) => ({
      label: row.label ?? '',
      value: Number(row.total ?? 0),
    }));
    if (spendGroup === 'month') return points.sort((a, b) => a.label.localeCompare(b.label));
    const top = [...points].sort((a, b) => b.value - a.value);
    const head = top.slice(0, 6);
    const other = top.slice(6).reduce((sum, point) => sum + point.value, 0);
    return other > 0 ? [...head, { label: 'Other', value: other }] : head;
  }, [spendGroup, spendRows]);

  const cycleRows = cycle.data?.rows ?? [];
  const cycleCount = cycleRows.reduce((sum, row) => sum + (row.count ?? 0), 0);
  const weightedAverage =
    cycleCount === 0
      ? 0
      : cycleRows.reduce((sum, row) => sum + (row.averageMinutes ?? 0) * (row.count ?? 0), 0) /
        cycleCount;
  const worstP90 = cycleRows.reduce((max, row) => Math.max(max, row.p90Minutes ?? 0), 0);

  const commitmentRows = commitments.data?.rows ?? [];
  const allocated = moneyByCurrency(commitmentRows, (row) => row.allocated?.amount);
  const committed = moneyByCurrency(commitmentRows, (row) => row.committed?.amount);
  const remaining = moneyByCurrency(commitmentRows, (row) => row.remaining?.amount);

  const applyPreset = (days: string) => {
    if (!days) {
      setFrom('');
      setTo('');
      return;
    }
    const now = new Date();
    const start = new Date(now.getTime() - Number(days) * 24 * 60 * 60 * 1000);
    setFrom(toLocalInput(start));
    setTo(toLocalInput(now));
  };

  return (
    <>
      <PageHeader title="Reports" />
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        Spend, approval performance and budget commitments across the current organization.
      </Typography>

      <Card sx={{ mb: 2 }}>
        <CardContent>
          <Tabs
            value={tab}
            onChange={(_event, value: number) => setTab(value)}
            variant="scrollable"
            allowScrollButtonsMobile
            sx={{ borderBottom: 1, borderColor: 'divider', mb: 2.5 }}
          >
            <Tab icon={<AssessmentIcon />} iconPosition="start" label="Spend" />
            <Tab icon={<ScheduleIcon />} iconPosition="start" label="Cycle time" />
            <Tab icon={<AccountBalanceIcon />} iconPosition="start" label="Commitments" />
          </Tabs>

          <Grid container spacing={2} alignItems="center">
            <Grid size={{ xs: 12, sm: 6, md: 3 }}>
              <TextField
                select
                size="small"
                fullWidth
                label="Period"
                defaultValue=""
                onChange={(event) => applyPreset(event.target.value)}
              >
                <MenuItem value="">Custom</MenuItem>
                <MenuItem value="30">Last 30 days</MenuItem>
                <MenuItem value="90">Last 90 days</MenuItem>
                <MenuItem value="365">Last 365 days</MenuItem>
              </TextField>
            </Grid>
            <Grid size={{ xs: 12, sm: 6, md: 3.5 }}>
              <TextField
                size="small"
                fullWidth
                label="From"
                type="datetime-local"
                value={from}
                onChange={(event) => setFrom(event.target.value)}
                slotProps={{ inputLabel: { shrink: true } }}
              />
            </Grid>
            <Grid size={{ xs: 12, sm: 6, md: 3.5 }}>
              <TextField
                size="small"
                fullWidth
                label="To"
                type="datetime-local"
                value={to}
                onChange={(event) => setTo(event.target.value)}
                slotProps={{ inputLabel: { shrink: true } }}
              />
            </Grid>
            <Grid size={{ xs: 12, md: 2 }}>
              {tab === 0 && (
                <Can I="export" a="Report">
                  <Button
                    fullWidth
                    variant="outlined"
                    startIcon={<DownloadIcon />}
                    onClick={async () =>
                      downloadBlob(
                        await spendCsv(spendGroup, fromIso, toIso),
                        `spend-${spendGroup}.csv`,
                      )
                    }
                  >
                    Export CSV
                  </Button>
                </Can>
              )}
            </Grid>
          </Grid>
        </CardContent>
      </Card>

      {tab === 0 && (
        <>
          <Grid container spacing={2} sx={{ mb: 2 }}>
            <Grid size={{ xs: 12, sm: 4 }}>
              <SummaryCard
                title="Total spend"
                value={formatMoneyTotals(spendTotals)}
                detail="Issued purchase orders"
                icon={<PaymentsIcon color="primary" />}
              />
            </Grid>
            <Grid size={{ xs: 12, sm: 4 }}>
              <SummaryCard
                title="Purchase orders"
                value={spendOrders.toLocaleString()}
                detail="In the selected period"
                icon={<ShoppingCartIcon color="secondary" />}
              />
            </Grid>
            <Grid size={{ xs: 12, sm: 4 }}>
              <SummaryCard
                title="Breakdown"
                value={`${spendRows.length} ${spendGroup}${spendRows.length === 1 ? '' : 's'}`}
                detail={`Grouped by ${spendGroup}`}
                icon={<CategoryIcon color="warning" />}
              />
            </Grid>
          </Grid>

          <Card sx={{ mb: 2 }}>
            <CardContent>
              <Stack
                direction={{ xs: 'column', sm: 'row' }}
                justifyContent="space-between"
                spacing={2}
              >
                <Box>
                  <Typography variant="h6">
                    {spendGroup === 'month' ? 'Spend trend' : 'Spend distribution'}
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    {spendGroup === 'month'
                      ? 'Issued spend by month'
                      : `Top ${spendGroup} groups by issued spend`}
                  </Typography>
                </Box>
                <TextField
                  select
                  size="small"
                  label="Group by"
                  value={spendGroup}
                  onChange={(event) => setSpendGroup(event.target.value)}
                  sx={{ minWidth: 180 }}
                >
                  {SPEND_GROUPS.map((value) => (
                    <MenuItem key={value} value={value}>
                      {value.replaceAll('_', ' ')}
                    </MenuItem>
                  ))}
                </TextField>
              </Stack>
              <Box sx={{ mt: 2 }}>
                {spend.isLoading ? (
                  <Loading rows={3} />
                ) : spend.error ? (
                  <Alert severity="error">Spend report could not be loaded.</Alert>
                ) : spendGroup === 'month' ? (
                  <AreaChart data={spendChart} ariaLabel="Spend trend" height={250} />
                ) : (
                  <DonutChart
                    data={spendChart}
                    ariaLabel="Spend distribution"
                    size={230}
                    valueFormatter={(value) => value.toLocaleString()}
                  />
                )}
              </Box>
            </CardContent>
          </Card>

          <Card>
            <CardContent>
              <Typography variant="h6" sx={{ mb: 2 }}>
                Spend details
              </Typography>
              <DataTable
                ariaLabel="Spend report"
                rows={spendRows}
                getRowId={(row) => `${row.key}-${row.currency}`}
                emptyMessage="No spend recorded in this period."
                columns={[
                  {
                    key: 'group',
                    label: titleCase(spendGroup),
                    render: (row) => row.label,
                    sortValue: (row) => row.label ?? '',
                  },
                  {
                    key: 'total',
                    label: 'Total',
                    width: 300,
                    render: (row) => <SpendBar row={row} rows={spendRows} />,
                    sortValue: (row) => Number(row.total ?? 0),
                  },
                  {
                    key: 'orders',
                    label: 'Orders',
                    align: 'right',
                    render: (row) => row.orderCount,
                    sortValue: (row) => row.orderCount ?? 0,
                  },
                ]}
              />
            </CardContent>
          </Card>
        </>
      )}

      {tab === 1 && (
        <>
          <Grid container spacing={2} sx={{ mb: 2 }}>
            <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
              <SummaryCard
                title="Approvals measured"
                value={cycleCount.toLocaleString()}
                detail="Completed approval steps"
                icon={<DoneAllIcon color="success" />}
              />
            </Grid>
            <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
              <SummaryCard
                title="Weighted average"
                value={formatMinutes(weightedAverage)}
                detail="Average approval time"
                icon={<TimerIcon color="primary" />}
              />
            </Grid>
            <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
              <SummaryCard
                title="Worst p90"
                value={formatMinutes(worstP90)}
                detail="Slowest group's p90"
                icon={<WarningAmberIcon color="warning" />}
              />
            </Grid>
            <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
              <SummaryCard
                title="Groups"
                value={cycleRows.length.toLocaleString()}
                detail={cycleGroup === 'policy' ? 'Policies measured' : 'Approvers measured'}
                icon={<AssessmentIcon color="secondary" />}
              />
            </Grid>
          </Grid>

          <Card>
            <CardContent>
              <Stack
                direction="row"
                justifyContent="space-between"
                alignItems="center"
                sx={{ mb: 2 }}
              >
                <Box>
                  <Typography variant="h6">Approval cycle time</Typography>
                  <Typography variant="body2" color="text.secondary">
                    Average, p50 and p90 completion time in minutes
                  </Typography>
                </Box>
                <TextField
                  select
                  size="small"
                  label="Group by"
                  value={cycleGroup}
                  onChange={(event) => setCycleGroup(event.target.value)}
                  sx={{ minWidth: 180 }}
                >
                  {CYCLE_GROUPS.map((value) => (
                    <MenuItem key={value} value={value}>
                      {titleCase(value)}
                    </MenuItem>
                  ))}
                </TextField>
              </Stack>
              {cycle.isLoading ? (
                <Loading rows={4} />
              ) : cycle.error ? (
                <Alert severity="error">Cycle-time report could not be loaded.</Alert>
              ) : (
                <DataTable
                  ariaLabel="Approval cycle time report"
                  rows={cycleRows}
                  getRowId={(row) => row.key ?? row.label ?? 'row'}
                  emptyMessage="No completed approvals in this period."
                  columns={[
                    {
                      key: 'group',
                      label: titleCase(cycleGroup),
                      render: (row) => row.label,
                      sortValue: (row) => row.label ?? '',
                    },
                    {
                      key: 'count',
                      label: 'Count',
                      align: 'right',
                      render: (row) => row.count ?? 0,
                      sortValue: (row) => row.count ?? 0,
                    },
                    {
                      key: 'avg',
                      label: 'Average',
                      align: 'right',
                      render: (row) => formatMinutes(row.averageMinutes ?? 0),
                      sortValue: (row) => row.averageMinutes ?? 0,
                    },
                    {
                      key: 'p50',
                      label: 'p50',
                      align: 'right',
                      render: (row) => formatMinutes(row.p50Minutes ?? 0),
                      sortValue: (row) => row.p50Minutes ?? 0,
                    },
                    {
                      key: 'p90',
                      label: 'p90',
                      align: 'right',
                      render: (row) => (
                        <Chip
                          size="small"
                          label={formatMinutes(row.p90Minutes ?? 0)}
                          color={(row.p90Minutes ?? 0) > 1440 ? 'warning' : 'default'}
                          variant="outlined"
                        />
                      ),
                      sortValue: (row) => row.p90Minutes ?? 0,
                    },
                  ]}
                />
              )}
            </CardContent>
          </Card>
        </>
      )}

      {tab === 2 && (
        <>
          <Grid container spacing={2} sx={{ mb: 2 }}>
            <Grid size={{ xs: 12, sm: 4 }}>
              <SummaryCard
                title="Allocated"
                value={formatMoneyTotals(allocated)}
                detail="Budget allocation"
                icon={<AccountBalanceIcon color="primary" />}
              />
            </Grid>
            <Grid size={{ xs: 12, sm: 4 }}>
              <SummaryCard
                title="Committed"
                value={formatMoneyTotals(committed)}
                detail="Issued purchase orders"
                icon={<LockClockIcon color="secondary" />}
              />
            </Grid>
            <Grid size={{ xs: 12, sm: 4 }}>
              <SummaryCard
                title="Available"
                value={formatMoneyTotals(remaining)}
                detail="Allocated minus reserved, committed and actual"
                icon={<SavingsIcon color="success" />}
              />
            </Grid>
          </Grid>

          <Card>
            <CardContent>
              <Typography variant="h6" sx={{ mb: 2 }}>
                Budget commitments
              </Typography>
              {commitments.isLoading ? (
                <Loading rows={4} />
              ) : commitments.error ? (
                <Alert severity="error">Commitments report could not be loaded.</Alert>
              ) : (
                <DataTable
                  ariaLabel="Budget commitments report"
                  rows={commitmentRows}
                  getRowId={(row) => row.budgetId ?? `${row.departmentName}-${row.fiscalYear}`}
                  emptyMessage="No budgets configured."
                  columns={[
                    {
                      key: 'department',
                      label: 'Department',
                      render: (row) => row.departmentName,
                      sortValue: (row) => row.departmentName ?? '',
                    },
                    { key: 'year', label: 'Year', render: (row) => row.fiscalYear },
                    { key: 'allocated', label: 'Allocated', render: (row) => money(row.allocated) },
                    { key: 'reserved', label: 'Reserved', render: (row) => money(row.reserved) },
                    { key: 'committed', label: 'Committed', render: (row) => money(row.committed) },
                    { key: 'actual', label: 'Actual', render: (row) => money(row.actual) },
                    {
                      key: 'remaining',
                      label: 'Available',
                      render: (row) => (
                        <Typography
                          variant="body2"
                          fontWeight={600}
                          color={Number(row.remaining?.amount ?? 0) < 0 ? 'error' : 'text.primary'}
                        >
                          {money(row.remaining)}
                        </Typography>
                      ),
                    },
                  ]}
                />
              )}
              <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
                Open commitments equal reserved + committed amounts.
              </Typography>
            </CardContent>
          </Card>
        </>
      )}
    </>
  );
}

function SummaryCard({
  title,
  value,
  detail,
  icon,
}: {
  title: string;
  value: string;
  detail: string;
  icon: React.ReactNode;
}) {
  return (
    <Card sx={{ height: '100%' }}>
      <CardContent>
        <Stack direction="row" spacing={1} alignItems="center">
          {icon}
          <Typography color="text.secondary">{title}</Typography>
        </Stack>
        <Typography variant="h5" sx={{ mt: 1 }}>
          {value}
        </Typography>
        <Typography variant="caption" color="text.secondary">
          {detail}
        </Typography>
      </CardContent>
    </Card>
  );
}

function SpendBar({ row, rows }: { row: SpendRow; rows: SpendRow[] }) {
  const max = Math.max(...rows.map((item) => Number(item.total ?? 0)), 1);
  return (
    <Stack spacing={0.5}>
      <Typography variant="body2">
        {Number(row.total ?? 0).toLocaleString()} {row.currency}
      </Typography>
      <LinearProgress
        variant="determinate"
        value={(Number(row.total ?? 0) / max) * 100}
        aria-label={`${row.label} spend`}
        sx={{ height: 6, borderRadius: 1 }}
      />
    </Stack>
  );
}

function moneyByCurrency(
  rows: (SpendRow | CommitmentRow)[],
  amount: (row: CommitmentRow) => string | undefined = (row) => (row as SpendRow).total?.toString(),
): Map<string, number> {
  const totals = new Map<string, number>();
  rows.forEach((row) => {
    const currency = row.currency ?? '—';
    const value =
      'allocated' in row
        ? Number(amount(row as CommitmentRow) ?? 0)
        : Number((row as SpendRow).total ?? 0);
    totals.set(currency, (totals.get(currency) ?? 0) + value);
  });
  return totals;
}

function formatMoneyTotals(totals: Map<string, number>): string {
  if (totals.size === 0) return '—';
  return [...totals.entries()]
    .map(
      ([currency, total]) =>
        `${total.toLocaleString(undefined, { maximumFractionDigits: 2 })} ${currency}`,
    )
    .join(' · ');
}

function formatMinutes(minutes: number): string {
  if (!Number.isFinite(minutes) || minutes <= 0) return '—';
  if (minutes < 60) return `${minutes.toFixed(1)} min`;
  if (minutes < 1440) return `${(minutes / 60).toFixed(1)} h`;
  return `${(minutes / 1440).toFixed(1)} d`;
}

function titleCase(value: string): string {
  return value.replaceAll('_', ' ').replace(/\b\w/g, (character) => character.toUpperCase());
}

function toLocalInput(date: Date): string {
  const offset = date.getTimezoneOffset();
  return new Date(date.getTime() - offset * 60_000).toISOString().slice(0, 16);
}
