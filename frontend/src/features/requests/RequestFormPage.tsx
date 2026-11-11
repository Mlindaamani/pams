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
  Drawer,
  Grid,
  IconButton,
  InputAdornment,
  MenuItem,
  Paper,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import DeleteIcon from '@mui/icons-material/Delete';
import AddIcon from '@mui/icons-material/Add';
import StarIcon from '@mui/icons-material/Star';
import StarBorderIcon from '@mui/icons-material/StarBorder';
import SearchIcon from '@mui/icons-material/Search';
import CloseIcon from '@mui/icons-material/Close';
import { PageHeader, Loading } from '@/components/Page';
import {
  favoriteCatalogItem,
  listCatalogItems,
  listDepartments,
  listFavoriteCatalogItems,
  listPopularCatalogItems,
  unfavoriteCatalogItem,
} from '@/features/reference/api';
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
  const catalog = useQuery({
    queryKey: ['catalog-items', profile.data?.departmentId],
    queryFn: () => listCatalogItems(profile.data?.departmentId),
    enabled: Boolean(profile.data?.departmentId),
  });
  const favorites = useQuery({
    queryKey: ['catalog-favorites'],
    queryFn: listFavoriteCatalogItems,
    enabled: Boolean(profile.data?.departmentId),
  });
  const popular = useQuery({
    queryKey: ['catalog-popular', profile.data?.departmentId],
    queryFn: () => listPopularCatalogItems(profile.data?.departmentId),
    enabled: Boolean(profile.data?.departmentId),
  });
  const vendors = useQuery({ queryKey: ['vendors'], queryFn: () => listVendors({ size: 100 }) });
  const canChooseDepartment = ability.can('request-for-any-department', 'PurchaseRequest');

  const { control, register, handleSubmit, reset, watch, setValue, getValues, formState } =
    useForm<FormValues>({
      resolver: zodResolver(schema),
      defaultValues: defaults,
    });
  const { fields, append, remove } = useFieldArray({ control, name: 'lines' });
  const [files, setFiles] = useState<File[]>([]);
  const [pickerOpen, setPickerOpen] = useState(false);
  const [catalogSearch, setCatalogSearch] = useState('');

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

  const toggleFavorite = useMutation({
    mutationFn: (input: { id: string; favorite: boolean }) =>
      input.favorite ? unfavoriteCatalogItem(input.id) : favoriteCatalogItem(input.id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['catalog-favorites'] });
    },
  });

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
  const watchedLines = watch('lines') ?? [];
  const itemCount = watchedLines.length;
  const estimatedTotal = watchedLines.reduce(
    (sum, line) => sum + (Number(line.quantity) || 0) * (Number(line.unitPrice) || 0),
    0,
  );

  const catalogItems = (catalog.data?.content ?? []).filter((item) => item.active);
  const catalogById = new Map(catalogItems.map((item) => [item.id!, item]));
  const favoriteIds = new Set((favorites.data ?? []).map((item) => item.id!));
  const departmentName =
    (departments.data?.content ?? []).find(
      (department) => department.id === profile.data?.departmentId,
    )?.name ?? 'your department';
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

  const addCustomItem = () => {
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
    setPickerOpen(false);
  };

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
        <Paper
          elevation={6}
          sx={{
            position: 'sticky',
            top: { xs: 72, md: 78 },
            zIndex: 8,
            p: 1.5,
            mb: 2,
            borderRadius: 2,
            border: 1,
            borderColor: 'divider',
            backdropFilter: 'blur(10px)',
          }}
        >
          <Stack
            direction={{ xs: 'column', sm: 'row' }}
            justifyContent="space-between"
            alignItems={{ xs: 'stretch', sm: 'center' }}
            spacing={1}
          >
            <Box>
              <Typography variant="subtitle2">
                {itemCount} item{itemCount === 1 ? '' : 's'} in this request
              </Typography>
              <Typography variant="caption" color="text.secondary">
                Estimated total {estimatedTotal.toLocaleString()} {currency}
              </Typography>
            </Box>
            <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
              <Button
                variant="contained"
                startIcon={<AddIcon />}
                onClick={() => setPickerOpen(true)}
              >
                Add item
              </Button>
              <Button variant="outlined" onClick={addCustomItem}>
                Can&apos;t find item?
              </Button>
            </Stack>
          </Stack>
        </Paper>

        <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 1 }}>
          <Typography variant="h6">Items</Typography>
          <Typography variant="caption" color="text.secondary">
            Only quantity and notes are required after selecting an item.
          </Typography>
        </Stack>

        <Stack spacing={2}>
          {fields.length === 0 && (
            <Card variant="outlined">
              <CardContent sx={{ textAlign: 'center', py: 5 }}>
                <Typography variant="h6">No items yet</Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                  Search the catalog or add a custom item. You will only need to enter quantity and
                  notes for catalog items.
                </Typography>
                <Stack direction="row" spacing={1} justifyContent="center">
                  <Button
                    variant="contained"
                    startIcon={<AddIcon />}
                    onClick={() => setPickerOpen(true)}
                  >
                    Add from catalog
                  </Button>
                  <Button variant="outlined" onClick={addCustomItem}>
                    Add custom item
                  </Button>
                </Stack>
              </CardContent>
            </Card>
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
                    {field.catalogItemId && (
                      <IconButton
                        aria-label={
                          favoriteIds.has(field.catalogItemId)
                            ? 'Remove from favorites'
                            : 'Add to favorites'
                        }
                        onClick={() =>
                          toggleFavorite.mutate({
                            id: field.catalogItemId!,
                            favorite: favoriteIds.has(field.catalogItemId!),
                          })
                        }
                      >
                        {favoriteIds.has(field.catalogItemId) ? (
                          <StarIcon color="warning" />
                        ) : (
                          <StarBorderIcon />
                        )}
                      </IconButton>
                    )}
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

      <Drawer
        anchor="right"
        open={pickerOpen}
        onClose={() => setPickerOpen(false)}
        sx={{ zIndex: (theme) => theme.zIndex.appBar + 10 }}
        slotProps={{
          paper: {
            sx: {
              width: { xs: '100%', sm: 520 },
              px: 2.5,
              pb: 2.5,
              pt: 9,
            },
          },
        }}
      >
        <Stack direction="row" justifyContent="space-between" alignItems="flex-start">
          <Box>
            <Typography variant="h6">Add items</Typography>
            <Typography variant="body2" color="text.secondary">
              Search the catalog or pick a favorite. Prices and units are locked automatically.
            </Typography>
          </Box>
          <IconButton aria-label="Close item picker" onClick={() => setPickerOpen(false)}>
            <CloseIcon />
          </IconButton>
        </Stack>

        <TextField
          autoFocus
          fullWidth
          placeholder="Search by item name or SKU"
          value={catalogSearch}
          onChange={(event) => setCatalogSearch(event.target.value)}
          sx={{ mt: 2 }}
          slotProps={{
            input: {
              startAdornment: (
                <InputAdornment position="start">
                  <SearchIcon fontSize="small" />
                </InputAdornment>
              ),
            },
          }}
        />

        <Stack spacing={2.5} sx={{ mt: 2, overflowY: 'auto', pr: 0.5 }}>
          {(favorites.data?.length ?? 0) > 0 && (
            <Box>
              <Typography variant="subtitle2" sx={{ mb: 1 }}>
                Favorites
              </Typography>
              <Stack spacing={1}>
                {(favorites.data ?? []).map((item) => (
                  <CatalogPickerRow
                    key={item.id}
                    item={item}
                    added={watchedLines.some((line) => line.catalogItemId === item.id)}
                    favorite={favoriteIds.has(item.id!)}
                    onAdd={() => addCatalogItem(item.id!)}
                    onToggleFavorite={() =>
                      toggleFavorite.mutate({ id: item.id!, favorite: favoriteIds.has(item.id!) })
                    }
                  />
                ))}
              </Stack>
            </Box>
          )}

          {(popular.data?.length ?? 0) > 0 && (
            <Box>
              <Typography variant="subtitle2" sx={{ mb: 1 }}>
                Popular in {departmentName}
              </Typography>
              <Stack spacing={1}>
                {(popular.data ?? []).map((item) => (
                  <CatalogPickerRow
                    key={item.id}
                    item={item}
                    added={watchedLines.some((line) => line.catalogItemId === item.id)}
                    favorite={favoriteIds.has(item.id!)}
                    onAdd={() => addCatalogItem(item.id!)}
                    onToggleFavorite={() =>
                      toggleFavorite.mutate({ id: item.id!, favorite: favoriteIds.has(item.id!) })
                    }
                  />
                ))}
              </Stack>
            </Box>
          )}

          <Box>
            <Typography variant="subtitle2" sx={{ mb: 1 }}>
              All catalog items
            </Typography>
            <Stack spacing={1}>
              {catalogItems
                .filter((item) => {
                  const term = catalogSearch.trim().toLowerCase();
                  if (!term) return true;
                  return `${item.name ?? ''} ${item.sku ?? ''} ${item.category ?? ''}`
                    .toLowerCase()
                    .includes(term);
                })
                .map((item) => (
                  <CatalogPickerRow
                    key={item.id}
                    item={item}
                    added={watchedLines.some((line) => line.catalogItemId === item.id)}
                    favorite={favoriteIds.has(item.id!)}
                    onAdd={() => addCatalogItem(item.id!)}
                    onToggleFavorite={() =>
                      toggleFavorite.mutate({ id: item.id!, favorite: favoriteIds.has(item.id!) })
                    }
                  />
                ))}
              {catalogItems.filter((item) => {
                const term = catalogSearch.trim().toLowerCase();
                if (!term) return true;
                return `${item.name ?? ''} ${item.sku ?? ''} ${item.category ?? ''}`
                  .toLowerCase()
                  .includes(term);
              }).length === 0 && (
                <Typography variant="body2" color="text.secondary">
                  No catalog items match your search.
                </Typography>
              )}
            </Stack>
          </Box>
        </Stack>

        <Button variant="outlined" startIcon={<AddIcon />} onClick={addCustomItem} sx={{ mt: 2 }}>
          Can&apos;t find your item? Add a custom item
        </Button>
      </Drawer>
    </>
  );
}

function CatalogPickerRow({
  item,
  added,
  favorite,
  onAdd,
  onToggleFavorite,
}: {
  item: {
    id?: string;
    name?: string;
    sku?: string;
    unit?: string;
    defaultPrice?: { amount?: string; currency?: string };
    category?: string;
  };
  added: boolean;
  favorite: boolean;
  onAdd: () => void;
  onToggleFavorite: () => void;
}) {
  return (
    <Paper variant="outlined" sx={{ p: 1.5 }}>
      <Stack direction="row" spacing={1.5} alignItems="center">
        <Box sx={{ flexGrow: 1, minWidth: 0 }}>
          <Typography variant="body2" fontWeight={600} noWrap>
            {item.name}
          </Typography>
          <Typography variant="caption" color="text.secondary" display="block" noWrap>
            {item.sku} · {item.unit} · {Number(item.defaultPrice?.amount ?? 0).toLocaleString()}{' '}
            {item.defaultPrice?.currency}
          </Typography>
          <Typography variant="caption" color="text.secondary" noWrap>
            {item.category ?? 'Uncategorized'}
          </Typography>
        </Box>
        <IconButton
          aria-label={favorite ? 'Remove from favorites' : 'Add to favorites'}
          onClick={onToggleFavorite}
        >
          {favorite ? <StarIcon color="warning" /> : <StarBorderIcon />}
        </IconButton>
        <Button
          size="small"
          variant={added ? 'outlined' : 'contained'}
          onClick={onAdd}
          disabled={added}
        >
          {added ? 'Added' : 'Add'}
        </Button>
      </Stack>
    </Paper>
  );
}
