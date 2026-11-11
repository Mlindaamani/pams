import { useEffect, useState } from 'react';
import { useFieldArray, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate, useParams } from 'react-router-dom';
import {
  Alert,
  Button,
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
import { createRequest, getRequest, updateRequest, uploadAttachment } from './api';
import type { PurchaseRequestRequest } from '@/api/types';
import { toast } from '@/components/Toast';

const lineSchema = z.object({
  description: z.string().min(1, 'Description is required'),
  quantity: z.number().positive('Quantity must be positive'),
  unit: z.string().min(1, 'Unit is required'),
  unitPrice: z.number().min(0, 'Price cannot be negative'),
  catalogItemId: z.string().optional(),
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
  lines: [{ description: '', quantity: 1, unit: 'EA', unitPrice: 0 }],
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
  const departments = useQuery({ queryKey: ['departments'], queryFn: listDepartments });
  const catalog = useQuery({ queryKey: ['catalog-items'], queryFn: listCatalogItems });

  const { control, register, handleSubmit, reset, watch, formState } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: defaults,
  });
  const { fields, append, remove } = useFieldArray({ control, name: 'lines' });
  const [files, setFiles] = useState<File[]>([]);

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
              helperText={formState.errors.title?.message}
            />
          </Grid>
          <Grid size={{ xs: 12, md: 4 }}>
            <TextField
              label="Currency"
              fullWidth
              required
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
              helperText={formState.errors.justification?.message}
            />
          </Grid>
          <Grid size={{ xs: 12, md: 6 }}>
            <TextField
              select
              label="Department"
              fullWidth
              required
              {...register('departmentId')}
              error={Boolean(formState.errors.departmentId)}
              helperText={formState.errors.departmentId?.message}
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
        <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 1 }}>
          <Typography variant="h6">Line items</Typography>
          <Button
            startIcon={<AddIcon />}
            onClick={() => append({ description: '', quantity: 1, unit: 'EA', unitPrice: 0 })}
          >
            Add line
          </Button>
        </Stack>

        <Stack spacing={2}>
          {fields.map((field, index) => (
            <Grid container spacing={1} key={field.id} alignItems="center">
              <Grid size={{ xs: 12, md: 3 }}>
                <TextField
                  label="Description"
                  fullWidth
                  {...register(`lines.${index}.description`)}
                  error={Boolean(formState.errors.lines?.[index]?.description)}
                />
              </Grid>
              <Grid size={{ xs: 6, md: 1.5 }}>
                <TextField
                  label="Qty"
                  type="number"
                  fullWidth
                  slotProps={{ htmlInput: { step: '0.0001', min: '0.0001' } }}
                  {...register(`lines.${index}.quantity`, { valueAsNumber: true })}
                  error={Boolean(formState.errors.lines?.[index]?.quantity)}
                />
              </Grid>
              <Grid size={{ xs: 6, md: 1.5 }}>
                <TextField label="Unit" fullWidth {...register(`lines.${index}.unit`)} />
              </Grid>
              <Grid size={{ xs: 6, md: 2 }}>
                <TextField
                  label={`Unit price (${currency})`}
                  type="number"
                  fullWidth
                  slotProps={{ htmlInput: { step: '0.0001', min: '0' } }}
                  {...register(`lines.${index}.unitPrice`, { valueAsNumber: true })}
                  error={Boolean(formState.errors.lines?.[index]?.unitPrice)}
                />
              </Grid>
              <Grid size={{ xs: 6, md: 2.5 }}>
                <TextField
                  select
                  label="Catalog item"
                  fullWidth
                  {...register(`lines.${index}.catalogItemId`)}
                >
                  <MenuItem value="">None</MenuItem>
                  {(catalog.data?.content ?? []).map((item) => (
                    <MenuItem key={item.id} value={item.id}>
                      {item.sku} · {item.name}
                    </MenuItem>
                  ))}
                </TextField>
              </Grid>
              <Grid size={{ xs: 12, md: 1 }}>
                <IconButton
                  aria-label="Remove line"
                  onClick={() => remove(index)}
                  disabled={fields.length === 1}
                >
                  <DeleteIcon />
                </IconButton>
              </Grid>
            </Grid>
          ))}
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
