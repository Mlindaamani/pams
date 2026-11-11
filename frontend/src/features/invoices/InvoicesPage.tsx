import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Stack, TextField, MenuItem } from '@mui/material';
import { Link as RouterLink, useNavigate } from 'react-router-dom';
import { DataTable } from '@/components/DataTable';
import { Loading, PageHeader } from '@/components/Page';
import { dateOnly, money } from '@/lib/format';
import { listVendorInvoices } from './api';

export function InvoicesPage() {
  const navigate = useNavigate();
  const [status, setStatus] = useState('');
  const [search, setSearch] = useState('');
  const invoices = useQuery({
    queryKey: ['vendor-invoices', status, search],
    queryFn: () =>
      listVendorInvoices({
        size: 100,
        status: (status || undefined) as never,
        invoiceNumber: search || undefined,
      }),
  });

  if (invoices.isLoading) return <Loading />;
  const rows = invoices.data?.content ?? [];

  return (
    <>
      <PageHeader title="Vendor invoices" />
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ mb: 2 }}>
        <TextField
          size="small"
          label="Search invoice number"
          value={search}
          onChange={(event) => setSearch(event.target.value)}
        />
        <TextField
          select
          size="small"
          label="Status"
          value={status}
          onChange={(event) => setStatus(event.target.value)}
          sx={{ minWidth: 180 }}
        >
          <MenuItem value="">All statuses</MenuItem>
          <MenuItem value="MATCHED">Matched</MenuItem>
          <MenuItem value="MISMATCHED">Mismatched</MenuItem>
          <MenuItem value="APPROVED">Approved</MenuItem>
        </TextField>
      </Stack>
      <DataTable
        ariaLabel="Vendor invoices"
        rows={rows}
        getRowId={(invoice) => invoice.id!}
        onRowClick={(invoice) => navigate(`/invoices/${invoice.id}`)}
        columns={[
          {
            key: 'invoice',
            label: 'Invoice',
            render: (invoice) => (
              <RouterLink
                to={`/invoices/${invoice.id}`}
                onClick={(event) => event.stopPropagation()}
              >
                {invoice.invoiceNumber}
              </RouterLink>
            ),
          },
          {
            key: 'internal',
            label: 'Internal ref',
            render: (invoice) => invoice.internalReference ?? '—',
          },
          {
            key: 'po',
            label: 'Purchase order',
            render: (invoice) => (
              <RouterLink
                to={`/purchase-orders/${invoice.purchaseOrderId}`}
                onClick={(event) => event.stopPropagation()}
              >
                {invoice.poNumber}
              </RouterLink>
            ),
          },
          {
            key: 'status',
            label: 'Match status',
            render: (invoice) => invoice.status,
            sortValue: (invoice) => invoice.status ?? '',
          },
          {
            key: 'date',
            label: 'Invoice date',
            render: (invoice) => dateOnly(invoice.invoiceDate),
            sortValue: (invoice) => invoice.invoiceDate ?? '',
          },
          {
            key: 'total',
            label: 'Total',
            align: 'right',
            render: (invoice) => money(invoice.total),
            sortValue: (invoice) => Number(invoice.total?.amount ?? 0),
          },
        ]}
      />
    </>
  );
}
