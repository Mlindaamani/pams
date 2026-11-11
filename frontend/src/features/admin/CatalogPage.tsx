import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link as RouterLink } from 'react-router-dom';
import {
  Autocomplete,
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  MenuItem,
  Stack,
  Tab,
  Tabs,
  TextField,
  Typography,
} from '@mui/material';
import { Can } from '@/auth/AbilityProvider';
import { useBranding } from '@/branding/BrandingProvider';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { DataTable } from '@/components/DataTable';
import { EmptyState, Loading, PageHeader } from '@/components/Page';
import { toast } from '@/components/Toast';
import { dateOnly, money } from '@/lib/format';
import { listVendors } from '@/features/vendors/api';
import type { CatalogItem } from '@/api/types';
import {
  bulkDeleteCatalogItems,
  createCatalogItem,
  deleteCatalogItem,
  listCatalogItems,
  listDepartments,
  listNonCatalogReview,
  resolveNonCatalogReview,
  updateCatalogItem,
} from '@/features/reference/api';

interface FormState {
  sku: string;
  name: string;
  unit: string;
  amount: string;
  currency: string;
  category: string;
  defaultVendorId: string;
  departmentScope: 'ALL' | 'SELECTED';
  departmentIds: string[];
  active: boolean;
}

const emptyForm: FormState = {
  sku: '',
  name: '',
  unit: 'EA',
  amount: '0.0000',
  currency: 'TZS',
  category: '',
  defaultVendorId: '',
  departmentScope: 'ALL',
  departmentIds: [],
  active: true,
};

export function CatalogPage() {
  const { branding } = useBranding();
  const queryClient = useQueryClient();
  const items = useQuery({
    queryKey: ['catalog-items'],
    queryFn: () => listCatalogItems(),
  });
  const vendors = useQuery({ queryKey: ['vendors'], queryFn: () => listVendors({ size: 100 }) });
  const departments = useQuery({ queryKey: ['departments'], queryFn: listDepartments });
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<CatalogItem | null>(null);
  const [form, setForm] = useState<FormState>(emptyForm);
  const [deleting, setDeleting] = useState<CatalogItem | null>(null);
  const [bulkIds, setBulkIds] = useState<string[] | null>(null);
  const [tab, setTab] = useState(0);
  const [reviewLineId, setReviewLineId] = useState<string | null>(null);
  const reviews = useQuery({
    queryKey: ['non-catalog-review'],
    queryFn: listNonCatalogReview,
    enabled: tab === 1,
  });

  const invalidate = () => void queryClient.invalidateQueries({ queryKey: ['catalog-items'] });
  const save = useMutation({
    mutationFn: async () => {
      const price = { amount: form.amount, currency: form.currency.toUpperCase() };
      if (editing) {
        return updateCatalogItem(editing.id!, {
          name: form.name,
          unit: form.unit,
          defaultPrice: price,
          category: form.category || undefined,
          defaultVendorId: form.defaultVendorId || undefined,
          departmentScope: form.departmentScope,
          departmentIds: form.departmentScope === 'SELECTED' ? form.departmentIds : undefined,
          active: form.active,
        });
      }
      return createCatalogItem({
        sku: form.sku,
        name: form.name,
        unit: form.unit,
        defaultPrice: price,
        category: form.category || undefined,
        defaultVendorId: form.defaultVendorId || undefined,
        departmentScope: form.departmentScope,
        departmentIds: form.departmentScope === 'SELECTED' ? form.departmentIds : undefined,
      });
    },
    onSuccess: () => {
      toast.success(editing ? 'Catalog item updated.' : 'Catalog item created.');
      if (reviewLineId) {
        resolveReview.mutate({ id: reviewLineId, status: 'ADDED' });
        setReviewLineId(null);
      }
      setOpen(false);
      setEditing(null);
      setForm(emptyForm);
      invalidate();
    },
  });
  const resolveReview = useMutation({
    mutationFn: (input: { id: string; status: 'ADDED' | 'DISMISSED' }) =>
      resolveNonCatalogReview(input.id, input.status),
    onSuccess: () => {
      toast.success('Review item updated.');
      void queryClient.invalidateQueries({ queryKey: ['non-catalog-review'] });
    },
  });
  const remove = useMutation({
    mutationFn: (id: string) => deleteCatalogItem(id),
    onSuccess: () => {
      toast.success('Catalog item deleted.');
      invalidate();
    },
  });
  const bulkRemove = useMutation({
    mutationFn: (ids: string[]) => bulkDeleteCatalogItems(ids),
    onSuccess: (count) => {
      toast.success(`${count} catalog item(s) deleted.`);
      invalidate();
    },
  });

  const openCreate = () => {
    setEditing(null);
    setForm(emptyForm);
    setOpen(true);
  };
  const openEdit = (item: CatalogItem) => {
    setEditing(item);
    setForm({
      sku: item.sku ?? '',
      name: item.name ?? '',
      unit: item.unit ?? 'EA',
      amount: item.defaultPrice?.amount ?? '0.0000',
      currency: item.defaultPrice?.currency ?? 'TZS',
      category: item.category ?? '',
      defaultVendorId: item.defaultVendorId ?? '',
      departmentScope: item.departmentScope ?? 'ALL',
      departmentIds: item.departmentIds ?? [],
      active: item.active ?? true,
    });
    setOpen(true);
  };

  if (items.isLoading) return <Loading />;
  const rows = items.data?.content ?? [];

  return (
    <>
      <PageHeader
        title={branding.catalogLabel}
        actions={
          <Can I="create" a="Catalog">
            <Button variant="contained" onClick={openCreate}>
              New catalog item
            </Button>
          </Can>
        }
      />
      <Tabs value={tab} onChange={(_, value: number) => setTab(value)} sx={{ mb: 2 }}>
        <Tab label="Catalog items" />
        <Tab
          label={`Non-catalog review${reviews.data?.length ? ` (${reviews.data.length})` : ''}`}
        />
      </Tabs>
      {tab === 1 ? (
        <DataTable
          ariaLabel="Non-catalog review"
          rows={reviews.data ?? []}
          getRowId={(line) => line.id}
          emptyMessage="No non-catalog items are waiting for review."
          columns={[
            {
              key: 'request',
              label: 'Request',
              render: (line) => (
                <Button component={RouterLink} to={`/requests/${line.requestId}`} size="small">
                  {line.requestNumber ?? line.requestId.slice(0, 8)}
                </Button>
              ),
            },
            {
              key: 'requester',
              label: 'Requester',
              render: (line) => line.requesterName ?? '—',
            },
            {
              key: 'department',
              label: 'Department',
              render: (line) => line.departmentName ?? '—',
            },
            {
              key: 'item',
              label: 'Item',
              render: (line) => (
                <Box>
                  <Typography variant="body2" fontWeight={600}>
                    {line.description}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    {line.notes ?? 'No note'}
                  </Typography>
                </Box>
              ),
            },
            { key: 'unit', label: 'Unit', render: (line) => line.unit ?? '—' },
            {
              key: 'price',
              label: 'Estimated price',
              align: 'right',
              render: (line) => money({ amount: String(line.unitPrice ?? 0), currency: 'TZS' }),
            },
            {
              key: 'created',
              label: 'Requested',
              render: (line) => dateOnly(line.createdAt),
            },
            {
              key: 'actions',
              label: 'Actions',
              align: 'right',
              render: (line) => (
                <Stack direction="row" spacing={0.5} justifyContent="flex-end">
                  <Button
                    size="small"
                    onClick={() => {
                      setEditing(null);
                      setReviewLineId(line.id);
                      setForm({
                        ...emptyForm,
                        name: line.description ?? '',
                        unit: line.unit ?? 'EA',
                        amount: String(line.unitPrice ?? 0),
                      });
                      setOpen(true);
                    }}
                  >
                    Add to catalog
                  </Button>
                  <Button
                    size="small"
                    color="error"
                    onClick={() => resolveReview.mutate({ id: line.id, status: 'DISMISSED' })}
                  >
                    Dismiss
                  </Button>
                </Stack>
              ),
            },
          ]}
        />
      ) : rows.length === 0 ? (
        <EmptyState message="No catalog items yet." />
      ) : (
        <DataTable
          ariaLabel="Catalog items"
          rows={rows}
          getRowId={(item) => item.id!}
          bulkActions={(ids, clear) => (
            <Can I="delete" a="Catalog">
              <Button
                size="small"
                color="error"
                variant="contained"
                onClick={() => {
                  setBulkIds(ids);
                  clear();
                }}
              >
                Delete selected
              </Button>
            </Can>
          )}
          columns={[
            {
              key: 'sku',
              label: 'SKU',
              render: (item) => item.sku,
              sortValue: (item) => item.sku ?? '',
            },
            {
              key: 'name',
              label: 'Name',
              render: (item) => item.name,
              sortValue: (item) => item.name ?? '',
            },
            { key: 'unit', label: 'Unit', render: (item) => item.unit },
            {
              key: 'price',
              label: 'Default price',
              render: (item) => money(item.defaultPrice),
            },
            { key: 'category', label: 'Category', render: (item) => item.category ?? '—' },
            {
              key: 'audience',
              label: 'Available to',
              render: (item) =>
                item.departmentScope === 'SELECTED'
                  ? (item.departmentIds ?? [])
                      .map(
                        (id) =>
                          (departments.data?.content ?? []).find(
                            (department) => department.id === id,
                          )?.name ?? id,
                      )
                      .join(', ')
                  : 'All departments',
            },
            {
              key: 'vendor',
              label: 'Default vendor',
              render: (item) =>
                (vendors.data?.content ?? []).find((vendor) => vendor.id === item.defaultVendorId)
                  ?.name ?? '—',
            },
            { key: 'active', label: 'Active', render: (item) => (item.active ? 'Yes' : 'No') },
            {
              key: 'actions',
              label: 'Actions',
              align: 'right',
              render: (item) => (
                <Can I="update" a="Catalog">
                  <Stack direction="row" spacing={1} justifyContent="flex-end">
                    <Button size="small" onClick={() => openEdit(item)}>
                      Edit
                    </Button>
                    <Button size="small" color="error" onClick={() => remove.mutate(item.id!)}>
                      Delete
                    </Button>
                  </Stack>
                </Can>
              ),
            },
          ]}
        />
      )}

      <ConfirmDialog
        open={Boolean(deleting)}
        title="Delete catalog item"
        message={`Delete ${deleting?.name ?? 'this catalog item'} (${deleting?.sku ?? ''})?`}
        busy={remove.isPending}
        onCancel={() => setDeleting(null)}
        onConfirm={() => {
          if (deleting) remove.mutate(deleting.id!, { onSuccess: () => setDeleting(null) });
        }}
      />
      <ConfirmDialog
        open={Boolean(bulkIds)}
        title="Delete selected catalog items"
        message={`Delete ${bulkIds?.length ?? 0} catalog item(s)?`}
        busy={bulkRemove.isPending}
        onCancel={() => setBulkIds(null)}
        onConfirm={() => {
          if (bulkIds) bulkRemove.mutate(bulkIds, { onSuccess: () => setBulkIds(null) });
        }}
      />

      <Dialog open={open} onClose={() => setOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>{editing ? 'Edit catalog item' : 'New catalog item'}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField
              label="SKU"
              value={form.sku}
              onChange={(event) => setForm({ ...form, sku: event.target.value })}
              disabled={Boolean(editing)}
              required
            />
            <TextField
              label="Name"
              value={form.name}
              onChange={(event) => setForm({ ...form, name: event.target.value })}
              required
            />
            <TextField
              label="Unit"
              value={form.unit}
              onChange={(event) => setForm({ ...form, unit: event.target.value })}
              required
            />
            <Stack direction="row" spacing={2}>
              <TextField
                label="Default price"
                type="number"
                value={form.amount}
                onChange={(event) => setForm({ ...form, amount: event.target.value })}
                fullWidth
                required
              />
              <TextField
                label="Currency"
                value={form.currency}
                onChange={(event) => setForm({ ...form, currency: event.target.value })}
                sx={{ width: 120 }}
                slotProps={{ htmlInput: { maxLength: 3 } }}
              />
            </Stack>
            <TextField
              label="Category"
              value={form.category}
              onChange={(event) => setForm({ ...form, category: event.target.value })}
            />
            <TextField
              select
              label="Default vendor"
              value={form.defaultVendorId}
              onChange={(event) => setForm({ ...form, defaultVendorId: event.target.value })}
            >
              <MenuItem value="">Vendor to be confirmed</MenuItem>
              {(vendors.data?.content ?? [])
                .filter((vendor) => vendor.status === 'ACTIVE')
                .map((vendor) => (
                  <MenuItem key={vendor.id} value={vendor.id}>
                    {vendor.name}
                  </MenuItem>
                ))}
            </TextField>
            <TextField
              select
              label="Available to"
              value={form.departmentScope}
              onChange={(event) =>
                setForm({
                  ...form,
                  departmentScope: event.target.value as 'ALL' | 'SELECTED',
                  departmentIds: event.target.value === 'ALL' ? [] : form.departmentIds,
                })
              }
            >
              <MenuItem value="ALL">All departments</MenuItem>
              <MenuItem value="SELECTED">Selected departments</MenuItem>
            </TextField>
            {form.departmentScope === 'SELECTED' && (
              <Autocomplete
                multiple
                options={departments.data?.content ?? []}
                getOptionLabel={(department) => department.name ?? ''}
                value={(departments.data?.content ?? []).filter((department) =>
                  form.departmentIds.includes(department.id!),
                )}
                onChange={(_, value) =>
                  setForm({ ...form, departmentIds: value.map((department) => department.id!) })
                }
                renderInput={(params) => <TextField {...params} label="Departments" required />}
              />
            )}
            {editing && (
              <TextField
                select
                label="Active"
                value={form.active ? 'yes' : 'no'}
                onChange={(event) => setForm({ ...form, active: event.target.value === 'yes' })}
              >
                <MenuItem value="yes">Active</MenuItem>
                <MenuItem value="no">Inactive</MenuItem>
              </TextField>
            )}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setOpen(false)}>Cancel</Button>
          <Button
            variant="contained"
            disabled={!form.name || !form.unit || (!editing && !form.sku) || save.isPending}
            onClick={() => save.mutate()}
          >
            {editing ? 'Save' : 'Create'}
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
}
