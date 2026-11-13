import { useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Checkbox,
  Grid,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import { Loading, PageHeader } from '@/components/Page';
import { toast } from '@/components/Toast';
import { problemMessage } from '@/api/client';
import { money } from '@/lib/format';
import { listVendors } from '@/features/vendors/api';
import { listVendorInvoices } from '@/features/invoices/api';
import { createPayment } from './api';

const METHODS = ['BANK_TRANSFER', 'MOBILE_MONEY', 'CHEQUE', 'CASH', 'OTHER'];

export function PaymentFormPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const vendors = useQuery({ queryKey: ['vendors'], queryFn: () => listVendors({ size: 100 }) });
  const approved = useQuery({
    queryKey: ['vendor-invoices', 'APPROVED', 'payment'],
    queryFn: () => listVendorInvoices({ status: 'APPROVED', size: 100 }),
  });
  const partial = useQuery({
    queryKey: ['vendor-invoices', 'PARTIALLY_PAID', 'payment'],
    queryFn: () => listVendorInvoices({ status: 'PARTIALLY_PAID', size: 100 }),
  });
  const invoices = useMemo(
    () => [...(approved.data?.content ?? []), ...(partial.data?.content ?? [])],
    [approved.data?.content, partial.data?.content],
  );
  const [vendorId, setVendorId] = useState('');
  const [paymentDate, setPaymentDate] = useState(new Date().toISOString().slice(0, 10));
  const [method, setMethod] = useState('BANK_TRANSFER');
  const [reference, setReference] = useState('');
  const [notes, setNotes] = useState('');
  const [selected, setSelected] = useState<Record<string, string>>({});

  const vendorInvoices = invoices.filter((invoice) => invoice.vendorId === vendorId);
  const outstanding = (invoice: (typeof invoices)[number]) =>
    Number(invoice.total?.amount ?? 0) - Number(invoice.paidAmount?.amount ?? 0);
  const total = Object.values(selected).reduce((sum, value) => sum + Number(value || 0), 0);

  const save = useMutation({
    mutationFn: () =>
      createPayment({
        vendorId,
        paymentDate,
        method,
        reference: reference || undefined,
        notes: notes || undefined,
        allocations: Object.entries(selected).map(([invoiceId, amount]) => ({
          invoiceId,
          amount: Number(amount),
        })),
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['payments'] });
      toast.success('Payment scheduled.');
      navigate('/payments');
    },
    onError: (error) => toast.error(problemMessage(error)),
  });

  if (vendors.isLoading || approved.isLoading || partial.isLoading) return <Loading />;

  return (
    <>
      <PageHeader title="Schedule vendor payment" backTo="/payments" backLabel="Back to payments" />
      <Card>
        <CardContent>
          <Grid container spacing={2}>
            <Grid size={{ xs: 12, md: 6 }}>
              <TextField
                select
                fullWidth
                label="Vendor"
                value={vendorId}
                onChange={(event) => {
                  setVendorId(event.target.value);
                  setSelected({});
                }}
                required
              >
                {(vendors.data?.content ?? [])
                  .filter((vendor) => vendor.status === 'ACTIVE')
                  .map((vendor) => (
                    <MenuItem key={vendor.id} value={vendor.id}>
                      {vendor.name}
                    </MenuItem>
                  ))}
              </TextField>
            </Grid>
            <Grid size={{ xs: 12, md: 3 }}>
              <TextField
                fullWidth
                type="date"
                label="Payment date"
                value={paymentDate}
                onChange={(event) => setPaymentDate(event.target.value)}
                slotProps={{ inputLabel: { shrink: true } }}
              />
            </Grid>
            <Grid size={{ xs: 12, md: 3 }}>
              <TextField
                select
                fullWidth
                label="Method"
                value={method}
                onChange={(event) => setMethod(event.target.value)}
              >
                {METHODS.map((option) => (
                  <MenuItem key={option} value={option}>
                    {option.replaceAll('_', ' ')}
                  </MenuItem>
                ))}
              </TextField>
            </Grid>
            <Grid size={{ xs: 12, md: 6 }}>
              <TextField
                fullWidth
                label="Reference"
                value={reference}
                onChange={(event) => setReference(event.target.value)}
                helperText="Bank reference, cheque number or mobile-money transaction."
              />
            </Grid>
            <Grid size={{ xs: 12, md: 6 }}>
              <TextField
                fullWidth
                label="Notes"
                value={notes}
                onChange={(event) => setNotes(event.target.value)}
              />
            </Grid>
          </Grid>

          <Typography variant="h6" sx={{ mt: 3, mb: 1 }}>
            Approved invoices
          </Typography>
          {!vendorId ? (
            <Alert severity="info">Select a vendor to see approved invoices.</Alert>
          ) : vendorInvoices.length === 0 ? (
            <Alert severity="warning">No approved invoices are available for this vendor.</Alert>
          ) : (
            <Stack spacing={1}>
              {vendorInvoices.map((invoice) => {
                const remaining = outstanding(invoice);
                const checked = invoice.id! in selected;
                return (
                  <Box
                    key={invoice.id}
                    sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 1.5, p: 1.5 }}
                  >
                    <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} alignItems="center">
                      <Checkbox
                        checked={checked}
                        onChange={(event) =>
                          setSelected((current) => {
                            const next = { ...current };
                            if (event.target.checked) next[invoice.id!] = remaining.toFixed(4);
                            else delete next[invoice.id!];
                            return next;
                          })
                        }
                      />
                      <Box sx={{ flexGrow: 1 }}>
                        <Typography variant="body2" fontWeight={600}>
                          {invoice.invoiceNumber} · {invoice.poNumber}
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                          Total {money(invoice.total)} · Paid {money(invoice.paidAmount)} ·
                          Outstanding {remaining.toLocaleString()} {invoice.currency}
                        </Typography>
                      </Box>
                      <TextField
                        size="small"
                        label="Amount"
                        type="number"
                        value={selected[invoice.id!] ?? ''}
                        disabled={!checked}
                        onChange={(event) =>
                          setSelected((current) => ({
                            ...current,
                            [invoice.id!]: event.target.value,
                          }))
                        }
                        slotProps={{ htmlInput: { min: 0.0001, max: remaining, step: 0.0001 } }}
                        sx={{ width: 170 }}
                      />
                    </Stack>
                  </Box>
                );
              })}
            </Stack>
          )}

          <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mt: 3 }}>
            <Typography variant="h6">
              Total {total.toLocaleString()} {vendorInvoices[0]?.currency ?? ''}
            </Typography>
            <Stack direction="row" spacing={1}>
              <Button component={Link} to="/payments">
                Cancel
              </Button>
              <Button
                variant="contained"
                disabled={!vendorId || total <= 0 || save.isPending}
                onClick={() => save.mutate()}
              >
                Schedule payment
              </Button>
            </Stack>
          </Stack>
        </CardContent>
      </Card>
    </>
  );
}
