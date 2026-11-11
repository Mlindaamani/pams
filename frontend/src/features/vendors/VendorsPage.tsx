import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Stack,
  TextField,
} from '@mui/material';
import { Can } from '@/auth/AbilityProvider';
import { useBranding } from '@/branding/BrandingProvider';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { DataTable } from '@/components/DataTable';
import { EmptyState, Loading, PageHeader } from '@/components/Page';
import { toast } from '@/components/Toast';
import type { Vendor } from '@/api/types';
import { bulkDeleteVendors, createVendor, listVendors, setVendorStatus, updateVendor } from './api';

interface FormState {
  name: string;
  taxId: string;
  contactName: string;
  contactEmail: string;
  contactPhone: string;
  addressLine: string;
  bankName: string;
  bankAccountName: string;
  bankAccountNumber: string;
}

const emptyForm: FormState = {
  name: '',
  taxId: '',
  contactName: '',
  contactEmail: '',
  contactPhone: '',
  addressLine: '',
  bankName: '',
  bankAccountName: '',
  bankAccountNumber: '',
};

export function VendorsPage() {
  const { branding } = useBranding();
  const queryClient = useQueryClient();
  const vendors = useQuery({ queryKey: ['vendors'], queryFn: () => listVendors({ size: 100 }) });
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<Vendor | null>(null);
  const [form, setForm] = useState<FormState>(emptyForm);
  const [deleting, setDeleting] = useState<Vendor | null>(null);
  const [bulkIds, setBulkIds] = useState<string[] | null>(null);
  const [blacklisting, setBlacklisting] = useState<Vendor | null>(null);

  const invalidate = () => void queryClient.invalidateQueries({ queryKey: ['vendors'] });
  const save = useMutation({
    mutationFn: () => {
      const body = {
        name: form.name,
        taxId: form.taxId,
        contactName: form.contactName || undefined,
        contactEmail: form.contactEmail || undefined,
        contactPhone: form.contactPhone || undefined,
        addressLine: form.addressLine || undefined,
        bankName: form.bankName || undefined,
        bankAccountName: form.bankAccountName || undefined,
        bankAccountNumber: form.bankAccountNumber || undefined,
      };
      return editing ? updateVendor(editing.id!, body) : createVendor(body);
    },
    onSuccess: () => {
      toast.success(editing ? 'Vendor updated.' : 'Vendor created.');
      setOpen(false);
      setEditing(null);
      setForm(emptyForm);
      invalidate();
    },
  });
  const status = useMutation({
    mutationFn: (input: { id: string; action: 'activate' | 'suspend' | 'blacklist' }) =>
      setVendorStatus(input.id, input.action),
    onSuccess: () => {
      toast.success('Vendor status updated.');
      invalidate();
    },
  });
  const bulkRemove = useMutation({
    mutationFn: (ids: string[]) => bulkDeleteVendors(ids),
    onSuccess: (count) => {
      toast.success(`${count} vendor(s) deleted.`);
      invalidate();
    },
  });

  const openCreate = () => {
    setEditing(null);
    setForm(emptyForm);
    setOpen(true);
  };
  const openEdit = (vendor: Vendor) => {
    setEditing(vendor);
    setForm({
      name: vendor.name ?? '',
      taxId: vendor.taxId ?? '',
      contactName: vendor.contactName ?? '',
      contactEmail: vendor.contactEmail ?? '',
      contactPhone: vendor.contactPhone ?? '',
      addressLine: vendor.addressLine ?? '',
      bankName: vendor.bankName ?? '',
      bankAccountName: vendor.bankAccountName ?? '',
      bankAccountNumber: vendor.bankAccountNumber ?? '',
    });
    setOpen(true);
  };

  if (vendors.isLoading) return <Loading />;
  const rows = vendors.data?.content ?? [];

  return (
    <>
      <PageHeader
        title={branding.vendorLabel}
        actions={
          <Can I="create" a="Vendor">
            <Button variant="contained" onClick={openCreate}>
              New vendor
            </Button>
          </Can>
        }
      />
      {rows.length === 0 ? (
        <EmptyState message="No vendors yet." />
      ) : (
        <DataTable
          ariaLabel="Vendors"
          rows={rows}
          getRowId={(vendor) => vendor.id!}
          bulkActions={(ids, clear) => (
            <Can I="delete" a="Vendor">
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
              key: 'name',
              label: 'Name',
              render: (vendor) => vendor.name,
              sortValue: (vendor) => vendor.name ?? '',
            },
            {
              key: 'tax',
              label: 'Tax ID',
              render: (vendor) => vendor.taxId,
              sortValue: (vendor) => vendor.taxId ?? '',
            },
            {
              key: 'contact',
              label: 'Contact',
              render: (vendor) => vendor.contactEmail ?? '—',
            },
            {
              key: 'status',
              label: 'Status',
              render: (vendor) => vendor.status,
              sortValue: (vendor) => vendor.status ?? '',
            },
            {
              key: 'actions',
              label: 'Actions',
              align: 'right',
              render: (vendor) => (
                <Can I="update" a="Vendor">
                  <Stack direction="row" spacing={1} justifyContent="flex-end">
                    <Button size="small" onClick={() => openEdit(vendor)}>
                      Edit
                    </Button>
                    {vendor.status === 'ACTIVE' && (
                      <Button
                        size="small"
                        onClick={() => status.mutate({ id: vendor.id!, action: 'suspend' })}
                      >
                        Suspend
                      </Button>
                    )}
                    {vendor.status === 'SUSPENDED' && (
                      <Button
                        size="small"
                        onClick={() => status.mutate({ id: vendor.id!, action: 'activate' })}
                      >
                        Activate
                      </Button>
                    )}
                    {vendor.status !== 'BLACKLISTED' && (
                      <Button size="small" color="warning" onClick={() => setBlacklisting(vendor)}>
                        Blacklist
                      </Button>
                    )}
                    <Button size="small" color="error" onClick={() => setDeleting(vendor)}>
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
        open={Boolean(blacklisting)}
        title="Blacklist vendor"
        message={`Blacklist ${blacklisting?.name ?? 'this vendor'}? A blacklisted vendor cannot be reactivated or selected for new POs.`}
        confirmLabel="Blacklist"
        color="warning"
        busy={status.isPending}
        onCancel={() => setBlacklisting(null)}
        onConfirm={() => {
          if (blacklisting)
            status.mutate(
              { id: blacklisting.id!, action: 'blacklist' },
              { onSuccess: () => setBlacklisting(null) },
            );
        }}
      />
      <ConfirmDialog
        open={Boolean(deleting)}
        title="Delete vendor"
        message={`Delete ${deleting?.name ?? 'this vendor'}? This soft-deletes the vendor.`}
        busy={bulkRemove.isPending}
        onCancel={() => setDeleting(null)}
        onConfirm={() => {
          if (deleting) bulkRemove.mutate([deleting.id!], { onSuccess: () => setDeleting(null) });
        }}
      />
      <ConfirmDialog
        open={Boolean(bulkIds)}
        title="Delete selected vendors"
        message={`Delete ${bulkIds?.length ?? 0} vendor(s)?`}
        busy={bulkRemove.isPending}
        onCancel={() => setBulkIds(null)}
        onConfirm={() => {
          if (bulkIds) bulkRemove.mutate(bulkIds, { onSuccess: () => setBulkIds(null) });
        }}
      />

      <Dialog open={open} onClose={() => setOpen(false)} fullWidth maxWidth="md">
        <DialogTitle>{editing ? 'Edit vendor' : 'New vendor'}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
              <TextField
                label="Name"
                value={form.name}
                onChange={(event) => setForm({ ...form, name: event.target.value })}
                required
                fullWidth
              />
              <TextField
                label="Tax ID"
                value={form.taxId}
                onChange={(event) => setForm({ ...form, taxId: event.target.value })}
                required
                fullWidth
              />
            </Stack>
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
              <TextField
                label="Contact name"
                value={form.contactName}
                onChange={(event) => setForm({ ...form, contactName: event.target.value })}
                fullWidth
              />
              <TextField
                label="Contact email"
                type="email"
                value={form.contactEmail}
                onChange={(event) => setForm({ ...form, contactEmail: event.target.value })}
                fullWidth
              />
              <TextField
                label="Contact phone"
                value={form.contactPhone}
                onChange={(event) => setForm({ ...form, contactPhone: event.target.value })}
                fullWidth
              />
            </Stack>
            <TextField
              label="Address"
              value={form.addressLine}
              onChange={(event) => setForm({ ...form, addressLine: event.target.value })}
            />
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
              <TextField
                label="Bank name"
                value={form.bankName}
                onChange={(event) => setForm({ ...form, bankName: event.target.value })}
                fullWidth
              />
              <TextField
                label="Account name"
                value={form.bankAccountName}
                onChange={(event) => setForm({ ...form, bankAccountName: event.target.value })}
                fullWidth
              />
              <TextField
                label="Account number"
                value={form.bankAccountNumber}
                onChange={(event) => setForm({ ...form, bankAccountNumber: event.target.value })}
                fullWidth
              />
            </Stack>
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setOpen(false)}>Cancel</Button>
          <Button
            variant="contained"
            disabled={!form.name || !form.taxId || save.isPending}
            onClick={() => save.mutate()}
          >
            {editing ? 'Save' : 'Create'}
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
}
