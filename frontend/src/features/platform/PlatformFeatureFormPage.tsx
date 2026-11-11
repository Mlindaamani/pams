import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  Button,
  Card,
  CardContent,
  Grid,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { Loading, PageHeader } from '@/components/Page';
import { toast } from '@/components/Toast';
import { problemMessage } from '@/api/client';
import {
  createSubscriptionFeature,
  deactivateSubscriptionFeature,
  listSubscriptionFeatures,
  updateSubscriptionFeature,
  type FeatureInput,
} from '@/features/subscription/api';

const CATEGORIES = [
  { value: 'CORE', label: 'Core' },
  { value: 'PROCUREMENT', label: 'Procurement' },
  { value: 'FINANCE', label: 'Finance' },
  { value: 'INTEGRATIONS', label: 'Integrations' },
  { value: 'SECURITY', label: 'Security' },
  { value: 'SUPPORT', label: 'Support' },
];

interface FeatureDraft {
  code: string;
  name: string;
  description: string;
  category: string;
  active: boolean;
  sortOrder: string;
}

const emptyDraft = (): FeatureDraft => ({
  code: '',
  name: '',
  description: '',
  category: 'CORE',
  active: true,
  sortOrder: '10',
});

const payload = (draft: FeatureDraft): FeatureInput => ({
  code: draft.code.trim().toLowerCase(),
  name: draft.name.trim(),
  description: draft.description.trim(),
  category: draft.category,
  active: draft.active,
  sortOrder: Number(draft.sortOrder),
});

export function PlatformFeatureFormPage() {
  const { code } = useParams();
  const editing = Boolean(code);
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const features = useQuery({
    queryKey: ['platform-subscription-features'],
    queryFn: listSubscriptionFeatures,
  });
  const [draft, setDraft] = useState<FeatureDraft>(emptyDraft);
  const [deactivateOpen, setDeactivateOpen] = useState(false);
  const feature = features.data?.find((item) => item.code === code);

  useEffect(() => {
    if (!feature) return;
    setDraft({
      code: feature.code ?? '',
      name: feature.name ?? '',
      description: feature.description ?? '',
      category: feature.category ?? 'CORE',
      active: feature.active ?? true,
      sortOrder: String(feature.sortOrder ?? 10),
    });
  }, [feature]);

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: ['platform-subscription-features'] });
    void queryClient.invalidateQueries({ queryKey: ['platform-all-plans'] });
  };

  const save = useMutation({
    mutationFn: () =>
      editing
        ? updateSubscriptionFeature(code!, payload(draft))
        : createSubscriptionFeature(payload(draft)),
    onSuccess: () => {
      refresh();
      toast.success(editing ? 'Feature updated.' : 'Feature created.');
      navigate('/platform/plans?tab=features');
    },
    onError: (error) => toast.error(problemMessage(error)),
  });
  const deactivate = useMutation({
    mutationFn: () => deactivateSubscriptionFeature(code!),
    onSuccess: () => {
      setDeactivateOpen(false);
      refresh();
      toast.success('Feature deactivated.');
      navigate('/platform/plans?tab=features');
    },
    onError: (error) => toast.error(problemMessage(error)),
  });

  if (editing && features.isLoading) return <Loading />;
  if (editing && features.error) {
    return <Alert severity="error">Subscription features could not be loaded.</Alert>;
  }

  return (
    <>
      <Button
        component={Link}
        to="/platform/plans?tab=features"
        size="small"
        startIcon={<ArrowBackIcon />}
        sx={{ mb: 1 }}
      >
        Back to features
      </Button>
      <PageHeader title={editing ? `Edit ${feature?.name ?? 'feature'}` : 'New feature'} />
      <Card>
        <CardContent>
          <Grid container spacing={2}>
            <Grid size={{ xs: 12, sm: 6 }}>
              <TextField
                label="Feature code"
                fullWidth
                value={draft.code}
                onChange={(event) =>
                  setDraft((current) => ({
                    ...current,
                    code: event.target.value.toLowerCase(),
                  }))
                }
                disabled={editing}
                helperText="Immutable lowercase identifier."
                required
              />
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <TextField
                label="Name"
                fullWidth
                value={draft.name}
                onChange={(event) =>
                  setDraft((current) => ({ ...current, name: event.target.value }))
                }
                required
              />
            </Grid>
            <Grid size={{ xs: 12 }}>
              <TextField
                label="Description"
                fullWidth
                multiline
                minRows={2}
                value={draft.description}
                onChange={(event) =>
                  setDraft((current) => ({ ...current, description: event.target.value }))
                }
                required
              />
            </Grid>
            <Grid size={{ xs: 12, sm: 4 }}>
              <TextField
                select
                label="Category"
                fullWidth
                value={draft.category}
                onChange={(event) =>
                  setDraft((current) => ({ ...current, category: event.target.value }))
                }
              >
                {CATEGORIES.map((category) => (
                  <MenuItem key={category.value} value={category.value}>
                    {category.label}
                  </MenuItem>
                ))}
              </TextField>
            </Grid>
            <Grid size={{ xs: 12, sm: 4 }}>
              <TextField
                label="Sort order"
                type="number"
                fullWidth
                value={draft.sortOrder}
                onChange={(event) =>
                  setDraft((current) => ({ ...current, sortOrder: event.target.value }))
                }
                slotProps={{ htmlInput: { min: 0 } }}
              />
            </Grid>
            <Grid size={{ xs: 12, sm: 4 }}>
              <TextField
                select
                label="Status"
                fullWidth
                value={draft.active ? 'ACTIVE' : 'INACTIVE'}
                onChange={(event) =>
                  setDraft((current) => ({ ...current, active: event.target.value === 'ACTIVE' }))
                }
              >
                <MenuItem value="ACTIVE">Active</MenuItem>
                <MenuItem value="INACTIVE">Inactive</MenuItem>
              </TextField>
            </Grid>
          </Grid>

          <Stack direction="row" spacing={1} sx={{ mt: 3 }}>
            <Button
              variant="contained"
              disabled={!draft.code || !draft.name || !draft.description || save.isPending}
              onClick={() => save.mutate()}
            >
              {editing ? 'Save feature' : 'Create feature'}
            </Button>
            <Button component={Link} to="/platform/plans?tab=features">
              Cancel
            </Button>
            {editing && feature?.active && (
              <Button color="error" onClick={() => setDeactivateOpen(true)}>
                Deactivate feature
              </Button>
            )}
          </Stack>
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 2 }}>
            Deactivation keeps existing plan assignments but prevents new plans from selecting this
            feature.
          </Typography>
        </CardContent>
      </Card>

      <ConfirmDialog
        open={deactivateOpen}
        title="Deactivate feature"
        message={`Deactivate ${feature?.name ?? 'this feature'}? Existing plans keep it.`}
        confirmLabel="Deactivate"
        color="error"
        busy={deactivate.isPending}
        onCancel={() => setDeactivateOpen(false)}
        onConfirm={() => deactivate.mutate()}
      />
    </>
  );
}
