import { useEffect, useState } from 'react';
import { useFieldArray, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate, useParams } from 'react-router-dom';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Divider,
  Grid,
  IconButton,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import DeleteIcon from '@mui/icons-material/Delete';
import AddIcon from '@mui/icons-material/Add';
import { PageHeader, Loading } from '@/components/Page';
import { listDepartments, listCatalogItems } from '@/features/reference/api';
import { useAbility } from '@/auth/AbilityProvider';
import { getMe } from '@/features/admin/usersApi';
import { listVendors } from '@/features/vendors/api';
import { createRequest, getRequest, updateRequest, uploadAttachment } from './api';
import type { PurchaseRequestRequest } from '@/api/types';
import { toast } from '@/components/Toast';

const lineSchema = z.object({
  description: z.string().min(1, 'Description is required'),
  quantity: z.number().positive('Quantity must be positive'),
  unit: z.string().min(1, 'Unit is required'),
  unitPrice: z.number().min(0, 'Price cannot be negative'),
  catalogItemId: z.string().optional(),
  vendorId: z.string().optional(),
  notes: z.string().max(500).optional(),
  nonCatalog: z.boolean(),
});

const schema = z.object({
  title: z.string().min(1, 'Title is required').max(300),
  justification: z.string().min(1, 'Justification is required').max(2000),
  departmentId: z.string().min(1, 'Department is required'),
  neededBy: z.string().optional(),
  currency: z.string().length(3, 'Use a 3-letter currency code'),
  budgetExceptionRequested: z.boolean(),
  lines: z.array(lineSchema).min(1, 'Add at least one line'),
});

type FormValues = z.infer<typeof schema>;

const defaults: FormValues = {
  title: '',
  justification: '',
  departmentId: '',
  neededBy: '',
  currency: 'TZS',
  budgetExceptionRequested: false,
  lines: [],
};

export function RequestFormPage() {
  const { id } = useParams();
  const edit = Boolean(id);
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const existing = useQuery({
    queryKey: ['request', id],
    queryFn: () => getRequest(id!),
    enabled: edit,
  });
  const ability = useAbility();
  const profile = useQuery({ queryKey: ['me'], queryFn: getMe });
  const departments = useQuery({ queryKey: ['departments'], queryFn: listDepartments });
  const catalog = useQuery({ queryKey: ['catalog-items'], queryFn: listCatalogItems });
  const vendors = useQuery({ queryKey: ['vendors'], queryFn: () => listVendors({ size: 100 }) });
  const canChooseDepartment = ability.can('request-for-any-department', 'PurchaseRequest');

  const { control, register, handleSubmit, reset, watch, setValue, getValues, formState } =
    useForm<FormValues>({
      resolver: zodResolver(schema),
      defaultValues: defaults,
    });
  const { fields, append, remove } = useFieldArray({ control, name: 'lines' });
  const [files, setFiles] = useState<File[]>([]);

  useEffect(() => {
    if (!edit && profile.data?.departmentId) {
      setValue('departmentId', profile.data.departmentId);
    }
  }, [edit, profile.data?.departmentId, setValue]);

  useEffect(() => {
    if (existing.data) {
      const request = existing.data;
      if (!['DRAFT', 'CHANGES_REQUESTED'].includes(request.status ?? '')) {
        return;
      }
      reset({
        title: request.title,
        justification: request.justification,
        departmentId: request.departmentId ?? '',
        neededBy: request.neededBy ?? '',
        currency: request.currency ?? 'TZS',
        budgetExceptionRequested: request.budgetExceptionRequested ?? false,
        lines: (request.lines ?? []).map((line) => ({
          description: line.description ?? '',
          quantity: Number(line.quantity ?? 1),
          unit: line.unit ?? 'EA',
          unitPrice: Number(line.unitPrice?.amount ?? 0),
          catalogItemId: line.catalogItemId ?? undefined,
          vendorId: line.vendorId ?? undefined,
          notes: line.notes ?? '',
          nonCatalog: line.nonCatalog ?? !line.catalogItemId,
        })),
      });
    }
  }, [existing.data, reset]);

  const mutation = useMutation({
    mutationFn: async (values: FormValues) => {
      const body: PurchaseRequestRequest = {
        title: values.title,
        justification: values.justification,
        departmentId: values.departmentId,
        neededBy: values.neededBy || undefined,
        currency: values.currency.toUpperCase(),
        budgetExceptionRequested: values.budgetExceptionRequested,
        lines: values.lines.map((line) => ({
          description: line.description,
          quantity: line.quantity,
          unit: line.unit,
          unitPrice: line.unitPrice,
          catalogItemId: line.catalogItemId || undefined,
          vendorId: line.vendorId || undefined,
          notes: line.notes || undefined,
        })),
      };
      return edit && existing.data
        ? updateRequest(id!, body, existing.data.version ?? 0)
        : createRequest(body);
    },
    onSuccess: async (request) => {
      const results = await Promise.allSettled(
        files.map((file) => uploadAttachment(request.id!, file)),
      );
      const failed = results.filter((result) => result.status === 'rejected').length;
      if (failed > 0) {
        toast.error(`${failed} attachment(s) failed to upload.`);
      } else {
        toast.success(edit ? 'Request updated.' : 'Draft created.');
      }
      void queryClient.invalidateQueries({ queryKey: ['requests'] });
      navigate(`/requests/${request.id}`);
    },
  });

  if (edit && existing.isLoading) return <Loading />;
  if (
    edit &&
    existing.data &&
    !['DRAFT', 'CHANGES_REQUESTED'].includes(existing.data.status ?? '')
  ) {
    return <Alert severity="warning">This request is no longer editable.</Alert>;
  }

  const currency = watch('currency').toUpperCase();

  const catalogItems = (catalog.data?.content ?? []).filter((item) => item.active);
  const catalogById = new Map(catalogItems.map((item) => [item.id!, item]));
  const vendorNames = new Map(
    (vendors.data?.content ?? []).map((vendor) => [vendor.id!, vendor.name ?? '']),
  );

  const addCatalogItem = (catalogItemId: string) => {
    const item = catalogById.get(catalogItemId);
    if (!item) return;
    append({
      description: item.name ?? '',
      quantity: 1,
      unit: item.unit ?? 'EA',
      unitPrice: Number(item.defaultPrice?.amount ?? 0),
      catalogItemId: item.id,
      vendorId: item.defaultVendorId,
      notes: '',
      nonCatalog: false,
    });
    if (!getValues('title')) {
      setValue('title', item.name ?? 'Purchase request');
    }
    if (item.defaultPrice?.currency) {
      setValue('currency', item.defaultPrice.currency);
    }
  };

  const addCustomItem = () =>
    append({
      description: '',
      quantity: 1,
      unit: 'EA',
      unitPrice: 0,
      catalogItemId: undefined,
      vendorId: undefined,
      notes: '',
      nonCatalog: true,
    });

  return (
    <>
      <PageHeader title={edit ? 'Edit request' : 'New purchase request'} />
      <form onSubmit={handleSubmit((values) => mutation.mutate(values))}>
        <Grid container spacing={2}>
          <Grid size={{ xs: 12, md: 8 }}>
            <TextField
              label="Title"
              fullWidth
              required
              {...register('title')}
              error={Boolean(formState.errors.title)}
              helperText={
                formState.errors.title?.message ?? 'Suggested from the first item you add.'
              }
            />
          </Grid>
          <Grid size={{ xs: 12, md: 4 }}>
            <TextField
              label="Currency"
              fullWidth
              required
              disabled
              slotProps={{ htmlInput: { maxLength: 3 } }}
              {...register('currency')}
              error={Boolean(formState.errors.currency)}
              helperText={formState.errors.currency?.message}
            />
          </Grid>
          <Grid size={{ xs: 12 }}>
            <TextField
              label="Justification"
              fullWidth
              required
              multiline
              minRows={3}
              {...register('justification')}
              error={Boolean(formState.errors.justification)}
              helperText={
                formState.errors.justification?.message ?? 'Explain why these items are needed.'
              }
            />
          </Grid>
          <Grid size={{ xs: 12, md: 6 }}>
            <TextField
              select
              label="Department"
              fullWidth
              required
              disabled={Boolean(profile.data) && !canChooseDepartment}
              value={watch('departmentId')}
              onChange={(event) =>
                setValue('departmentId', event.target.value, { shouldValidate: true })
              }
              error={Boolean(formState.errors.departmentId)}
              helperText={
                formState.errors.departmentId?.message ??
                (canChooseDepartment
                  ? 'You can create this request for another department.'
                  : 'This is your assigned department.')
              }
            >
              {(departments.data?.content ?? []).map((department) => (
                <MenuItem key={department.id} value={department.id}>
                  {department.name}
                </MenuItem>
              ))}
            </TextField>
          </Grid>
          <Grid size={{ xs: 12, md: 6 }}>
            <TextField
              label="Needed by"
              type="date"
              fullWidth
              slotProps={{ inputLabel: { shrink: true } }}
              {...register('neededBy')}
            />
          </Grid>
        </Grid>

        <Divider sx={{ my: 3 }} />
        <Card sx={{ mb: 2 }}>
          <CardContent>
            <Typography variant="h6">Add items</Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
              Pick an item from the catalog. Prices, units and vendors are filled for you.
            </Typography>
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5}>
              <TextField
                select
                label="Add from catalog"
                value=""
                onChange={(event) => addCatalogItem(event.target.value)}
                sx={{ minWidth: 320 }}
              >
                <MenuItem value="" disabled>
                  Choose an item…
                </MenuItem>
                {catalogItems.map((item) => (
                  <MenuItem key={item.id} value={item.id}>
                    {item.sku} · {item.name} ({item.unit})
                  </MenuItem>
                ))}
              </TextField>
              <Button variant="outlined" startIcon={<AddIcon />} onClick={addCustomItem}>
                Can&apos;t find your item?
              </Button>
            </Stack>
          </CardContent>
        </Card>

        <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 1 }}>
          <Typography variant="h6">Items</Typography>
          <Typography variant="caption" color="text.secondary">
            Only quantity and notes are required after selecting an item.
          </Typography>
        </Stack>

        <Stack spacing={2}>
          {fields.length === 0 && (
            <Alert severity="info">Add at least one catalog item or custom item.</Alert>
          )}
          {fields.map((field, index) => {
            const catalogItem = field.catalogItemId
              ? catalogById.get(field.catalogItemId)
              : undefined;
            const vendorId = catalogItem?.defaultVendorId ?? field.vendorId;
            return (
              <Card key={field.id} variant="outlined">
                <CardContent>
                  <Stack
                    direction={{ xs: 'column', md: 'row' }}
                    spacing={2}
                    alignItems={{ xs: 'stretch', md: 'flex-start' }}
                  >
                    <Box sx={{ flexGrow: 1 }}>
                      <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
                        <Typography variant="subtitle1" fontWeight={600}>
                          {field.description || 'Custom item'}
                        </Typography>
                        {field.nonCatalog && (
                          <Chip
                            size="small"
                            color="warning"
                            variant="outlined"
                            label="Non-catalog — admin review"
                          />
                        )}
                      </Stack>
                      <Typography variant="body2" color="text.secondary">
                        {field.nonCatalog
                          ? 'Describe the item, unit and estimated price.'
                          : `${catalogItem?.sku ?? 'Catalog item'} · ${field.unit} · ${Number(field.unitPrice).toLocaleString()} ${currency} · ${vendorNames.get(vendorId ?? '') ?? (vendorId ? 'Vendor' : 'Vendor to be confirmed')}`}
                      </Typography>
                    </Box>
                    <IconButton
                      aria-label="Remove line"
                      onClick={() => remove(index)}
                      disabled={fields.length === 1}
                    >
                      <DeleteIcon />
                    </IconButton>
                  </Stack>

                  {field.nonCatalog && (
                    <Grid container spacing={1.5} sx={{ mt: 1 }}>
                      <Grid size={{ xs: 12, md: 4 }}>
                        <TextField
                          label="Item description"
                          fullWidth
                          required
                          {...register(`lines.${index}.description`)}
                          error={Boolean(formState.errors.lines?.[index]?.description)}
                        />
                      </Grid>
                      <Grid size={{ xs: 6, md: 2 }}>
                        <TextField
                          label="Unit / UOM"
                          fullWidth
                          required
                          {...register(`lines.${index}.unit`)}
                        />
                      </Grid>
                      <Grid size={{ xs: 6, md: 2 }}>
                        <TextField
                          label={`Estimated price (${currency})`}
                          type="number"
                          fullWidth
                          slotProps={{ htmlInput: { step: '0.0001', min: '0' } }}
                          {...register(`lines.${index}.unitPrice`, { valueAsNumber: true })}
                        />
                      </Grid>
                      <Grid size={{ xs: 12, md: 4 }}>
                        <TextField
                          select
                          label="Vendor (optional)"
                          fullWidth
                          {...register(`lines.${index}.vendorId`)}
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
                      </Grid>
                    </Grid>
                  )}

                  <Grid container spacing={1.5} sx={{ mt: 1 }}>
                    <Grid size={{ xs: 6, md: 2 }}>
                      <TextField
                        label="Quantity"
                        type="number"
                        fullWidth
                        slotProps={{ htmlInput: { step: '0.0001', min: '0.0001' } }}
                        {...register(`lines.${index}.quantity`, { valueAsNumber: true })}
                        error={Boolean(formState.errors.lines?.[index]?.quantity)}
                      />
                    </Grid>
                    <Grid size={{ xs: 12, md: 10 }}>
                      <TextField
                        label="Line note (optional)"
                        fullWidth
                        {...register(`lines.${index}.notes`)}
                        helperText="Special instructions, size, colour or delivery note."
                      />
                    </Grid>
                  </Grid>
                </CardContent>
              </Card>
            );
          })}
        </Stack>

        {formState.errors.lines?.root?.message && (
          <Alert severity="error" sx={{ mt: 2 }}>
            {formState.errors.lines.root.message}
          </Alert>
        )}

        <Stack spacing={1.5} sx={{ mt: 3 }}>
          <Typography variant="h6">Attachments</Typography>
          <Typography variant="body2" color="text.secondary">
            PDF, PNG, JPG, DOCX or XLSX · max 10 MB each · max 10 files.
          </Typography>
          <Button component="label" variant="outlined" sx={{ alignSelf: 'flex-start' }}>
            Add files
            <input
              type="file"
              hidden
              multiple
              accept=".pdf,.png,.jpg,.jpeg,.docx,.xlsx"
              onChange={(event) => {
                setFiles((current) => [...current, ...Array.from(event.target.files ?? [])]);
                event.target.value = '';
              }}
            />
          </Button>
          <Stack spacing={0.5}>
            {files.map((file, index) => (
              <Stack key={`${file.name}-${index}`} direction="row" spacing={1} alignItems="center">
                <Typography variant="body2">{file.name}</Typography>
                <Button
                  size="small"
                  color="error"
                  onClick={() => setFiles((current) => current.filter((_, i) => i !== index))}
                >
                  Remove
                </Button>
              </Stack>
            ))}
          </Stack>
        </Stack>

        <Stack direction="row" spacing={2} sx={{ mt: 3 }}>
          <Button type="submit" variant="contained" disabled={mutation.isPending}>
            {edit ? 'Save changes' : 'Create draft'}
          </Button>
          <Button onClick={() => navigate(-1)}>Cancel</Button>
        </Stack>
      </form>
    </>
  );
}
