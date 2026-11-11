import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Box, Button, Card, CardContent, Typography } from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import { DataTable } from '@/components/DataTable';
import { Loading, PageHeader } from '@/components/Page';
import { dateTime, money } from '@/lib/format';
import { getPaymentAging } from './api';

export function PaymentAgingPage() {
  const aging = useQuery({ queryKey: ['payment-aging'], queryFn: getPaymentAging });

  if (aging.isLoading) return <Loading />;

  const rows = aging.data?.rows ?? [];
  const totals = rows.reduce((sum, row) => sum + Number(row.totalOutstanding ?? 0), 0);

  return (
    <>
      <Button
        component={Link}
        to="/payments"
        size="small"
        startIcon={<ArrowBackIcon />}
        sx={{ mb: 1 }}
      >
        Back to payments
      </Button>
      <PageHeader title="AP aging" />
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        Outstanding approved and partially paid invoices by vendor and currency. Generated{' '}
        {dateTime(aging.data?.generatedAt)}.
      </Typography>
      <Card>
        <CardContent>
          <DataTable
            ariaLabel="AP aging"
            rows={rows}
            getRowId={(row) => `${row.vendorId}-${row.currency}`}
            emptyMessage="No outstanding approved invoices."
            columns={[
              {
                key: 'vendor',
                label: 'Vendor',
                render: (row) => (
                  <Box>
                    <Typography variant="body2" fontWeight={600}>
                      {row.vendorName}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      {row.invoiceCount} invoice(s) · {row.currency}
                    </Typography>
                  </Box>
                ),
                sortValue: (row) => row.vendorName ?? '',
              },
              {
                key: 'current',
                label: 'Current',
                align: 'right',
                render: (row) =>
                  money({ amount: String(row.current ?? 0), currency: row.currency }),
                sortValue: (row) => Number(row.current ?? 0),
              },
              {
                key: '1_30',
                label: '1–30 days',
                align: 'right',
                render: (row) =>
                  money({ amount: String(row.days1To30 ?? 0), currency: row.currency }),
                sortValue: (row) => Number(row.days1To30 ?? 0),
              },
              {
                key: '31_60',
                label: '31–60 days',
                align: 'right',
                render: (row) =>
                  money({ amount: String(row.days31To60 ?? 0), currency: row.currency }),
                sortValue: (row) => Number(row.days31To60 ?? 0),
              },
              {
                key: '61_90',
                label: '61–90 days',
                align: 'right',
                render: (row) =>
                  money({ amount: String(row.days61To90 ?? 0), currency: row.currency }),
                sortValue: (row) => Number(row.days61To90 ?? 0),
              },
              {
                key: 'over_90',
                label: 'Over 90 days',
                align: 'right',
                render: (row) =>
                  money({ amount: String(row.daysOver90 ?? 0), currency: row.currency }),
                sortValue: (row) => Number(row.daysOver90 ?? 0),
              },
              {
                key: 'total',
                label: 'Outstanding',
                align: 'right',
                render: (row) =>
                  money({ amount: String(row.totalOutstanding ?? 0), currency: row.currency }),
                sortValue: (row) => Number(row.totalOutstanding ?? 0),
              },
            ]}
          />
          <Typography variant="body2" color="text.secondary" sx={{ mt: 2 }}>
            Total outstanding across all currencies: {totals.toLocaleString()}
          </Typography>
        </CardContent>
      </Card>
    </>
  );
}
