import { useState } from 'react';
import { useInfiniteQuery } from '@tanstack/react-query';
import { Button, Divider, Drawer, Stack, TextField, Typography } from '@mui/material';
import { DataTable } from '@/components/DataTable';
import { Loading, PageHeader } from '@/components/Page';
import { dateTime } from '@/lib/format';
import type { AuditEvent } from '@/api/types';
import { auditPage } from './api';

export function AuditPage() {
  const [action, setAction] = useState('');
  const [aggregateType, setAggregateType] = useState('');
  const [aggregateId, setAggregateId] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [selected, setSelected] = useState<AuditEvent | null>(null);

  const audit = useInfiniteQuery({
    queryKey: ['audit', action, aggregateType, aggregateId, from, to],
    initialPageParam: undefined as string | undefined,
    queryFn: ({ pageParam }) =>
      auditPage({
        cursor: pageParam,
        limit: 25,
        action: action || undefined,
        aggregateType: aggregateType || undefined,
        aggregateId: aggregateId || undefined,
        from: from ? new Date(from).toISOString() : undefined,
        to: to ? new Date(to).toISOString() : undefined,
      }),
    getNextPageParam: (last) => last.nextCursor ?? undefined,
  });

  const events = (audit.data?.pages.flatMap((page) => page.content) ?? []).filter(
    (event): event is AuditEvent => Boolean(event),
  );

  return (
    <>
      <PageHeader title="Audit log" />
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ mb: 2 }}>
        <TextField
          label="Action"
          value={action}
          onChange={(event) => setAction(event.target.value)}
        />
        <TextField
          label="Aggregate type"
          value={aggregateType}
          onChange={(event) => setAggregateType(event.target.value)}
        />
        <TextField
          label="Aggregate ID"
          value={aggregateId}
          onChange={(event) => setAggregateId(event.target.value)}
        />
        <TextField
          label="From"
          type="datetime-local"
          value={from}
          onChange={(event) => setFrom(event.target.value)}
          slotProps={{ inputLabel: { shrink: true } }}
        />
        <TextField
          label="To"
          type="datetime-local"
          value={to}
          onChange={(event) => setTo(event.target.value)}
          slotProps={{ inputLabel: { shrink: true } }}
        />
      </Stack>
      {audit.isLoading ? (
        <Loading />
      ) : (
        <>
          <DataTable
            ariaLabel="Audit events"
            rows={events}
            getRowId={(event) => event.id ?? `${event.occurredAt}-${event.action}`}
            onRowClick={setSelected}
            columns={[
              {
                key: 'when',
                label: 'When',
                render: (event) => dateTime(event.occurredAt),
                sortValue: (event) => event.occurredAt ?? '',
              },
              {
                key: 'action',
                label: 'Action',
                render: (event) => event.action,
                sortValue: (event) => event.action ?? '',
              },
              {
                key: 'aggregate',
                label: 'Aggregate',
                render: (event) => `${event.aggregateType} · ${event.aggregateId}`,
              },
              { key: 'actor', label: 'Actor', render: (event) => event.actorName },
              { key: 'eventId', label: 'Event ID', render: (event) => event.id },
            ]}
          />
          {audit.hasNextPage && (
            <Button
              sx={{ mt: 2 }}
              onClick={() => void audit.fetchNextPage()}
              disabled={audit.isFetchingNextPage}
            >
              Load more
            </Button>
          )}
        </>
      )}
      <Drawer
        anchor="right"
        open={Boolean(selected)}
        onClose={() => setSelected(null)}
        slotProps={{ paper: { sx: { width: { xs: '100%', sm: 480 }, p: 3 } } }}
      >
        {selected && (
          <Stack spacing={2}>
            <Typography variant="h6">Audit event</Typography>
            <Typography variant="body2" color="text.secondary">
              {selected.action} · {dateTime(selected.occurredAt)}
            </Typography>
            <Divider />
            <Typography variant="body2">
              Actor: {selected.actorName} ({selected.actorSubject})
            </Typography>
            <Typography variant="body2">
              Aggregate: {selected.aggregateType} · {selected.aggregateId}
            </Typography>
            <Typography variant="body2">Event ID: {selected.id}</Typography>
            <Divider />
            <Typography variant="subtitle2">Payload</Typography>
            <Typography
              component="pre"
              variant="body2"
              sx={{
                whiteSpace: 'pre-wrap',
                wordBreak: 'break-word',
                bgcolor: 'grey.50',
                p: 2,
                borderRadius: 1,
              }}
            >
              {JSON.stringify(selected.payload, null, 2)}
            </Typography>
          </Stack>
        )}
      </Drawer>
    </>
  );
}
