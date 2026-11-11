import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  MenuItem,
  Stack,
  TextField,
} from '@mui/material';
import { Can } from '@/auth/AbilityProvider';
import { useBranding } from '@/branding/BrandingProvider';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { DataTable } from '@/components/DataTable';
import { EmptyState, Loading, PageHeader } from '@/components/Page';
import { toast } from '@/components/Toast';
import { money } from '@/lib/format';
import type { CatalogItem } from '@/api/types';
import {
  bulkDeleteCatalogItems,
  createCatalogItem,
  deleteCatalogItem,
  listCatalogItems,
  updateCatalogItem,
} from '@/features/reference/api';

interface FormState {
  sku: string;
  name: string;
  unit: string;
  amount: string;
  currency: string;
  category: string;
  active: boolean;
}

const emptyForm: FormState = {
  sku: '',
  name: '',
  unit: 'EA',
  amount: '0.0000',
  currency: 'TZS',
  category: '',
  active: true,
};

export function CatalogPage() {
  const { branding } = useBranding();
  const queryClient = useQueryClient();
  const items = useQuery({ queryKey: ['catalog-items'], queryFn: listCatalogItems });
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<CatalogItem | null>(null);
  const [form, setForm] = useState<FormState>(emptyForm);
  const [deleting, setDeleting] = useState<CatalogItem | null>(null);
  const [bulkIds, setBulkIds] = useState<string[] | null>(null);

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
          active: form.active,
        });
      }
      return createCatalogItem({
        sku: form.sku,
        name: form.name,
        unit: form.unit,
        defaultPrice: price,
        category: form.category || undefined,
      });
    },
    onSuccess: () => {
      toast.success(editing ? 'Catalog item updated.' : 'Catalog item created.');
      setOpen(false);
      setEditing(null);
      setForm(emptyForm);
      invalidate();
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
      {rows.length === 0 ? (
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
