import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link as RouterLink } from 'react-router-dom';
import { Button, Link, MenuItem, Stack, TablePagination, TextField } from '@mui/material';
import { DataTable } from '@/components/DataTable';
import { Can } from '@/auth/AbilityProvider';
import { useBranding } from '@/branding/BrandingProvider';
import { EmptyState, Loading, PageHeader } from '@/components/Page';
import { listRequests, type RequestFilters } from './api';
import { money, dateTime } from '@/lib/format';
import type { PurchaseRequest } from '@/api/types';

const statuses = [
  'DRAFT',
  'SUBMITTED',
  'IN_APPROVAL',
  'APPROVED',
  'REJECTED',
  'CHANGES_REQUESTED',
  'CANCELLED',
  'CONVERTED_TO_PO',
];

export function RequestListPage() {
  const { branding } = useBranding();
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [status, setStatus] = useState('');
  const [q, setQ] = useState('');

  const query = useQuery({
    queryKey: ['requests', { page, size, status, q }],
    queryFn: () =>
      listRequests({
        page,
        size,
        status: status ? (status as RequestFilters['status']) : undefined,
        q: q || undefined,
      }),
  });

  const rows = (query.data?.content ?? []) as PurchaseRequest[];

  return (
    <>
      <PageHeader
        title={branding.requestLabel}
        actions={
          <Can I="create" a="PurchaseRequest">
            <Button component={RouterLink} to="/requests/new" variant="contained">
              New request
            </Button>
          </Can>
        }
      />
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ mb: 2 }}>
        <TextField
          select
          label="Status"
          value={status}
          onChange={(event) => {
            setStatus(event.target.value);
            setPage(0);
          }}
          sx={{ minWidth: 200 }}
        >
          <MenuItem value="">All</MenuItem>
          {statuses.map((value) => (
            <MenuItem key={value} value={value}>
              {value}
            </MenuItem>
          ))}
        </TextField>
        <TextField
          label="Search reference or title"
          value={q}
          onChange={(event) => setQ(event.target.value)}
          fullWidth
        />
      </Stack>

      {query.isLoading ? (
        <Loading />
      ) : rows.length === 0 ? (
        <EmptyState message="No requests match the current filters." />
      ) : (
        <>
          <DataTable
            ariaLabel="Purchase requests"
            rows={rows}
            getRowId={(request) => request.id!}
            columns={[
              {
                key: 'requestNumber',
                label: 'Reference',
                render: (request) => (
                  <Link
                    component={RouterLink}
                    to={`/requests/${request.id}`}
                    color="primary.main"
                    underline="hover"
                    sx={{ fontWeight: 600 }}
                  >
                    {request.requestNumber}
                  </Link>
                ),
                sortValue: (request) => request.requestNumber ?? '',
              },
              {
                key: 'title',
                label: 'Title',
                render: (request) => (
                  <Link
                    component={RouterLink}
                    to={`/requests/${request.id}`}
                    color="primary.main"
                    underline="hover"
                    sx={{ fontWeight: 600 }}
                  >
                    {request.title}
                  </Link>
                ),
              },
              {
                key: 'status',
                label: 'Status',
                render: (request) => request.status,
                sortValue: (request) => request.status ?? '',
              },
              {
                key: 'total',
                label: 'Total',
                render: (request) => money(request.total),
                sortValue: (request) => Number(request.total?.amount ?? 0),
              },
              {
                key: 'created',
                label: 'Created',
                render: (request) => dateTime(request.createdAt),
              },
            ]}
          />
          <TablePagination
            component="div"
            count={query.data?.totalElements ?? 0}
            page={page}
            onPageChange={(_, value) => setPage(value)}
            rowsPerPage={size}
            onRowsPerPageChange={(event) => {
              setSize(Number(event.target.value));
              setPage(0);
            }}
          />
        </>
      )}
    </>
  );
}
