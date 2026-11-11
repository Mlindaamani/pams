import { Fragment, useMemo, useState, type ReactNode } from 'react';
import {
  Box,
  Checkbox,
  Stack,
  Table,
  TableBody,
  TableContainer,
  TableCell,
  TableHead,
  TableRow,
  TableSortLabel,
  Typography,
} from '@mui/material';

export interface DataColumn<T> {
  key: string;
  label: string;
  align?: 'left' | 'right' | 'center';
  render: (row: T) => ReactNode;
  width?: number | string;
  sortValue?: (row: T) => string | number;
}

interface Props<T> {
  columns: DataColumn<T>[];
  rows: T[];
  getRowId: (row: T) => string;
  emptyMessage?: string;
  ariaLabel?: string;
  bulkActions?: (selectedIds: string[], clear: () => void) => ReactNode;
  persistentSelectionToolbar?: boolean;
  selectable?: boolean;
  onRowClick?: (row: T) => void;
}

export function DataTable<T>({
  columns,
  rows,
  getRowId,
  emptyMessage = 'No data.',
  ariaLabel = 'Data table',
  bulkActions,
  persistentSelectionToolbar = false,
  selectable = Boolean(bulkActions),
  onRowClick,
}: Props<T>) {
  const [selected, setSelected] = useState<Set<string>>(() => new Set());
  const [sortKey, setSortKey] = useState<string | null>(null);
  const [sortDirection, setSortDirection] = useState<'asc' | 'desc'>('asc');
  const sortedRows = useMemo(() => {
    const column = columns.find((candidate) => candidate.key === sortKey);
    if (!column?.sortValue) return rows;
    return [...rows].sort((left, right) => {
      const leftValue = column.sortValue!(left);
      const rightValue = column.sortValue!(right);
      const comparison =
        typeof leftValue === 'number' && typeof rightValue === 'number'
          ? leftValue - rightValue
          : String(leftValue).localeCompare(String(rightValue));
      return sortDirection === 'asc' ? comparison : -comparison;
    });
  }, [rows, columns, sortKey, sortDirection]);
  const ids = useMemo(() => sortedRows.map(getRowId), [sortedRows, getRowId]);
  const selectedOnPage = ids.filter((id) => selected.has(id));
  const allSelected = ids.length > 0 && selectedOnPage.length === ids.length;
  const someSelected = selectedOnPage.length > 0 && !allSelected;

  const toggleAll = () => {
    setSelected((current) => {
      const next = new Set(current);
      if (allSelected) {
        ids.forEach((id) => next.delete(id));
      } else {
        ids.forEach((id) => next.add(id));
      }
      return next;
    });
  };

  const toggleOne = (id: string) => {
    setSelected((current) => {
      const next = new Set(current);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  };

  const clear = () => setSelected(new Set());

  return (
    <Fragment>
      {selectable && bulkActions && (selected.size > 0 || persistentSelectionToolbar) && (
        <Box
          sx={{
            px: 2,
            py: 1,
            mb: 1,
            borderRadius: 1,
            bgcolor: 'rgba(11, 92, 173, 0.08)',
            border: 1,
            borderColor: 'primary.100',
          }}
        >
          <Stack
            direction="row"
            spacing={1.5}
            alignItems="center"
            justifyContent="space-between"
            flexWrap="wrap"
            sx={{ width: '100%' }}
          >
            <Typography variant="body2" fontWeight={600}>
              {selected.size} selected
            </Typography>
            <Stack direction="row" spacing={1} flexWrap="wrap">
              {bulkActions([...selected], clear)}
            </Stack>
          </Stack>
        </Box>
      )}
      <TableContainer
        sx={{
          border: 1,
          borderColor: 'divider',
          borderRadius: 0,
          overflow: 'hidden',
        }}
      >
        <Table
          size="small"
          aria-label={ariaLabel}
          sx={{
            '& .MuiTableCell-root': {
              borderRight: '1px solid',
              borderBottom: '1px solid',
              borderColor: 'divider',
            },
            '& .MuiTableCell-root:last-child': { borderRight: 'none' },
            '& .MuiTableBody-root .MuiTableRow-root:last-child .MuiTableCell-root': {
              borderBottom: 'none',
            },
          }}
        >
          <TableHead>
            <TableRow>
              {selectable && (
                <TableCell padding="checkbox">
                  <Checkbox
                    size="small"
                    checked={allSelected}
                    indeterminate={someSelected}
                    onChange={toggleAll}
                    slotProps={{ input: { 'aria-label': 'Select all rows' } }}
                  />
                </TableCell>
              )}
              {columns.map((column) => (
                <TableCell key={column.key} align={column.align} sx={{ width: column.width }}>
                  {column.sortValue ? (
                    <TableSortLabel
                      active={sortKey === column.key}
                      direction={sortKey === column.key ? sortDirection : 'asc'}
                      onClick={() => {
                        if (sortKey === column.key) {
                          setSortDirection((current) => (current === 'asc' ? 'desc' : 'asc'));
                        } else {
                          setSortKey(column.key);
                          setSortDirection('asc');
                        }
                      }}
                    >
                      {column.label}
                    </TableSortLabel>
                  ) : (
                    column.label
                  )}
                </TableCell>
              ))}
            </TableRow>
          </TableHead>
          <TableBody>
            {sortedRows.length === 0 ? (
              <TableRow>
                <TableCell colSpan={columns.length + (selectable ? 1 : 0)}>
                  <Typography color="text.secondary" sx={{ py: 3, textAlign: 'center' }}>
                    {emptyMessage}
                  </Typography>
                </TableCell>
              </TableRow>
            ) : (
              sortedRows.map((row) => {
                const id = getRowId(row);
                return (
                  <TableRow
                    key={id}
                    hover
                    selected={selected.has(id)}
                    onClick={onRowClick ? () => onRowClick(row) : undefined}
                    sx={onRowClick ? { cursor: 'pointer' } : undefined}
                  >
                    {selectable && (
                      <TableCell padding="checkbox">
                        <Checkbox
                          size="small"
                          checked={selected.has(id)}
                          onChange={() => toggleOne(id)}
                          slotProps={{ input: { 'aria-label': `Select row ${id}` } }}
                        />
                      </TableCell>
                    )}
                    {columns.map((column) => (
                      <TableCell key={column.key} align={column.align}>
                        {column.render(row)}
                      </TableCell>
                    ))}
                  </TableRow>
                );
              })
            )}
          </TableBody>
        </Table>
      </TableContainer>
    </Fragment>
  );
}
