import { Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Box, Button, Card, CardContent, Chip, Stack, Typography } from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import { Can } from '@/auth/AbilityProvider';
import { DataTable } from '@/components/DataTable';
import { Loading, PageHeader } from '@/components/Page';
import { toast } from '@/components/Toast';
import { problemMessage } from '@/api/client';
import { dateOnly, money } from '@/lib/format';
import { listVendors } from '@/features/vendors/api';
import { approvePayment, cancelPayment, listPayments, payPayment } from './api';

export function PaymentsPage() {
  const queryClient = useQueryClient();
  const payments = useQuery({ queryKey: ['payments'], queryFn: listPayments });
  const vendors = useQuery({ queryKey: ['vendors'], queryFn: () => listVendors({ size: 100 }) });
  const vendorNames = new Map(
    (vendors.data?.content ?? []).map((vendor) => [vendor.id, vendor.name]),
  );

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: ['payments'] });
    void queryClient.invalidateQueries({ queryKey: ['vendor-invoices'] });
    void queryClient.invalidateQueries({ queryKey: ['vendor-invoice'] });
  };
  const approve = useMutation({
    mutationFn: approvePayment,
    onSuccess: () => {
      toast.success('Payment approved.');
      refresh();
    },
    onError: (error) => toast.error(problemMessage(error)),
  });
  const pay = useMutation({
    mutationFn: payPayment,
    onSuccess: () => {
      toast.success('Payment recorded.');
      refresh();
    },
    onError: (error) => toast.error(problemMessage(error)),
  });
  const cancel = useMutation({
    mutationFn: cancelPayment,
    onSuccess: () => {
      toast.success('Payment canceled.');
      refresh();
    },
    onError: (error) => toast.error(problemMessage(error)),
  });

  if (payments.isLoading) return <Loading />;

  return (
    <>
      <PageHeader
        title="Payments"
        actions={
          <Stack direction="row" spacing={1}>
            <Button component={Link} to="/payments/aging" variant="outlined">
              AP aging
            </Button>
            <Can I="manage" a="Payment">
              <Button
                component={Link}
                to="/payments/new"
                variant="contained"
                startIcon={<AddIcon />}
              >
                Schedule payment
              </Button>
            </Can>
          </Stack>
        }
      />
      <Card>
        <CardContent>
          <DataTable
            ariaLabel="Vendor payments"
            rows={payments.data ?? []}
            getRowId={(payment) => payment.id!}
            emptyMessage="No vendor payments scheduled."
            columns={[
              {
                key: 'paymentNumber',
                label: 'Reference',
                render: (payment) => payment.paymentNumber ?? '—',
                sortValue: (payment) => payment.paymentNumber ?? '',
              },
              {
                key: 'payment',
                label: 'Vendor',
                render: (payment) => (
                  <Box>
                    <Typography variant="body2" fontWeight={600}>
                      {vendorNames.get(payment.vendorId ?? '') ?? payment.vendorId}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      {payment.reference || payment.method}
                    </Typography>
                  </Box>
                ),
                sortValue: (payment) => vendorNames.get(payment.vendorId ?? '') ?? '',
              },
              {
                key: 'date',
                label: 'Payment date',
                render: (payment) => dateOnly(payment.paymentDate),
                sortValue: (payment) => payment.paymentDate ?? '',
              },
              {
                key: 'amount',
                label: 'Amount',
                align: 'right',
                render: (payment) =>
                  money({
                    amount: String(payment.totalAmount ?? 0),
                    currency: payment.currency ?? 'TZS',
                  }),
                sortValue: (payment) => Number(payment.totalAmount ?? 0),
              },
              {
                key: 'status',
                label: 'Status',
                render: (payment) => (
                  <Chip
                    size="small"
                    label={payment.status}
                    color={
                      payment.status === 'PAID'
                        ? 'success'
                        : payment.status === 'APPROVED'
                          ? 'info'
                          : payment.status === 'CANCELED'
                            ? 'default'
                            : 'warning'
                    }
                    variant="outlined"
                  />
                ),
                sortValue: (payment) => payment.status ?? '',
              },
              {
                key: 'invoices',
                label: 'Invoices',
                render: (payment) =>
                  (payment.allocations ?? [])
                    .map((allocation) => allocation.invoiceNumber)
                    .join(', '),
              },
              {
                key: 'actions',
                label: 'Actions',
                align: 'right',
                render: (payment) => (
                  <Can I="manage" a="Payment">
                    <Stack direction="row" spacing={0.5} justifyContent="flex-end">
                      {payment.status === 'SCHEDULED' && (
                        <Button size="small" onClick={() => approve.mutate(payment.id!)}>
                          Approve
                        </Button>
                      )}
                      {payment.status === 'APPROVED' && (
                        <Button
                          size="small"
                          variant="contained"
                          onClick={() => pay.mutate(payment.id!)}
                        >
                          Mark paid
                        </Button>
                      )}
                      {['SCHEDULED', 'APPROVED'].includes(payment.status ?? '') && (
                        <Button
                          size="small"
                          color="error"
                          onClick={() => cancel.mutate(payment.id!)}
                        >
                          Cancel
                        </Button>
                      )}
                    </Stack>
                  </Can>
                ),
              },
            ]}
          />
        </CardContent>
      </Card>
    </>
  );
}
