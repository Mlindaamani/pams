import { useMemo, useState, type ReactNode } from 'react';
import {
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
  onRowClick,
}: Props<T>) {
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
  return (
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
              <TableCell colSpan={columns.length}>
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
                  onClick={onRowClick ? () => onRowClick(row) : undefined}
                  sx={onRowClick ? { cursor: 'pointer' } : undefined}
                >
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
  );
}
