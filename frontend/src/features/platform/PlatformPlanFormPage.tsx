import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Checkbox,
  Chip,
  Grid,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { Loading, PageHeader } from '@/components/Page';
import { toast } from '@/components/Toast';
import { problemMessage } from '@/api/client';
import type { PlanDraft } from './types';
import {
  createPlatformPlan,
  deactivatePlatformPlan,
  listAllPlatformPlans,
  listSubscriptionFeatures,
  updatePlatformPlan,
  type PlanInput,
  type PlatformPlan,
} from '@/features/subscription/api';

const emptyDraft = (): PlanDraft => ({
  code: '',
  name: '',
  description: '',
  monthlyPrice: '0',
  annualPrice: '0',
  currency: 'USD',
  trialDays: '14',
  maxUsers: '',
  maxDepartments: '',
  maxRequestsMonth: '',
  maxStorageMb: '',
  featureCodes: [],
  active: true,
  sortOrder: '10',
  stripePriceMonthly: '',
  stripePriceAnnual: '',
});

const toDraft = (plan: PlatformPlan): PlanDraft => ({
  code: plan.code ?? '',
  name: plan.name ?? '',
  description: plan.description ?? '',
  monthlyPrice: String(plan.monthlyPrice ?? 0),
  annualPrice: String(plan.annualPrice ?? 0),
  currency: plan.currency ?? 'USD',
  trialDays: String(plan.trialDays ?? 14),
  maxUsers: plan.maxUsers == null ? '' : String(plan.maxUsers),
  maxDepartments: plan.maxDepartments == null ? '' : String(plan.maxDepartments),
  maxRequestsMonth: plan.maxRequestsMonth == null ? '' : String(plan.maxRequestsMonth),
  maxStorageMb: plan.maxStorageMb == null ? '' : String(plan.maxStorageMb),
  featureCodes: (plan.features ?? []).map((feature) => feature.code!).filter(Boolean),
  active: plan.active ?? true,
  sortOrder: String(plan.sortOrder ?? 10),
  stripePriceMonthly: plan.stripePriceMonthly ?? '',
  stripePriceAnnual: plan.stripePriceAnnual ?? '',
});

const optionalNumber = (value: string) => (value.trim() === '' ? undefined : Number(value));

const FEATURE_CATEGORIES = [
  { value: 'CORE', label: 'Core' },
  { value: 'PROCUREMENT', label: 'Procurement' },
  { value: 'FINANCE', label: 'Finance' },
  { value: 'INTEGRATIONS', label: 'Integrations' },
  { value: 'SECURITY', label: 'Security' },
  { value: 'SUPPORT', label: 'Support' },
];

const payload = (draft: PlanDraft): PlanInput => ({
  code: draft.code.trim().toUpperCase(),
  name: draft.name.trim(),
  description: draft.description.trim(),
  monthlyPrice: Number(draft.monthlyPrice),
  annualPrice: Number(draft.annualPrice),
  currency: draft.currency.trim().toUpperCase(),
  trialDays: Number(draft.trialDays),
  maxUsers: optionalNumber(draft.maxUsers),
  maxDepartments: optionalNumber(draft.maxDepartments),
  maxRequestsMonth: optionalNumber(draft.maxRequestsMonth),
  maxStorageMb: optionalNumber(draft.maxStorageMb),
  featureCodes: draft.featureCodes,
  active: draft.active,
  sortOrder: Number(draft.sortOrder),
  stripePriceMonthly: draft.stripePriceMonthly.trim() || undefined,
  stripePriceAnnual: draft.stripePriceAnnual.trim() || undefined,
});

export function PlatformPlanFormPage() {
  const { code } = useParams();
  const editing = Boolean(code);
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const plans = useQuery({ queryKey: ['platform-all-plans'], queryFn: listAllPlatformPlans });
  const features = useQuery({
    queryKey: ['platform-subscription-features'],
    queryFn: listSubscriptionFeatures,
  });
  const [draft, setDraft] = useState<PlanDraft>(emptyDraft);
  const [featureSearch, setFeatureSearch] = useState('');
  const [deactivateOpen, setDeactivateOpen] = useState(false);
  const plan = plans.data?.find((item) => item.code === code);
  const visibleFeatures = (features.data ?? []).filter((feature) => {
    const selected = draft.featureCodes.includes(feature.code ?? '');
    if (!feature.active && !selected) return false;
    const term = featureSearch.trim().toLowerCase();
    if (!term) return true;
    return [feature.name, feature.code, feature.description]
      .filter(Boolean)
      .some((value) => value!.toLowerCase().includes(term));
  });

  useEffect(() => {
    if (plan) setDraft(toDraft(plan));
  }, [plan]);

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: ['platform-all-plans'] });
    void queryClient.invalidateQueries({ queryKey: ['platform-subscription-plans'] });
    void queryClient.invalidateQueries({ queryKey: ['subscription-plans'] });
  };

  const save = useMutation({
    mutationFn: () =>
      editing ? updatePlatformPlan(code!, payload(draft)) : createPlatformPlan(payload(draft)),
    onSuccess: () => {
      refresh();
      toast.success(editing ? 'Plan updated.' : 'Plan created.');
      navigate('/platform/plans');
    },
    onError: (error) => toast.error(problemMessage(error)),
  });
  const deactivate = useMutation({
    mutationFn: () => deactivatePlatformPlan(code!),
    onSuccess: () => {
      setDeactivateOpen(false);
      refresh();
      toast.success('Plan deactivated.');
      navigate('/platform/plans');
    },
    onError: (error) => toast.error(problemMessage(error)),
  });

  if (editing && plans.isLoading) return <Loading />;
  if (editing && plans.error) {
    return <Alert severity="error">Subscription plans could not be loaded.</Alert>;
  }

  return (
    <>
      <PageHeader
        title={editing ? `Edit ${plan?.name ?? 'plan'}` : 'New subscription plan'}
        backTo="/platform/plans"
        backLabel="Back to plans"
      />
      <Card>
        <CardContent>
          <Grid container spacing={2}>
            <Grid size={{ xs: 12, sm: 6 }}>
              <TextField
                label="Plan code"
                fullWidth
                value={draft.code}
                onChange={(event) =>
                  setDraft((current) => ({ ...current, code: event.target.value.toUpperCase() }))
                }
                disabled={editing}
                helperText="Uppercase letters, numbers and underscores."
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
                value={draft.description}
                onChange={(event) =>
                  setDraft((current) => ({ ...current, description: event.target.value }))
                }
                required
              />
            </Grid>
            <Grid size={{ xs: 12, sm: 4 }}>
              <TextField
                label="Monthly price"
                type="number"
                fullWidth
                value={draft.monthlyPrice}
                onChange={(event) =>
                  setDraft((current) => ({ ...current, monthlyPrice: event.target.value }))
                }
                slotProps={{ htmlInput: { min: 0, step: 0.01 } }}
                required
              />
            </Grid>
            <Grid size={{ xs: 12, sm: 4 }}>
              <TextField
                label="Annual price"
                type="number"
                fullWidth
                value={draft.annualPrice}
                onChange={(event) =>
                  setDraft((current) => ({ ...current, annualPrice: event.target.value }))
                }
                slotProps={{ htmlInput: { min: 0, step: 0.01 } }}
                required
              />
            </Grid>
            <Grid size={{ xs: 12, sm: 4 }}>
              <TextField
                label="Currency"
                fullWidth
                value={draft.currency}
                onChange={(event) =>
                  setDraft((current) => ({
                    ...current,
                    currency: event.target.value.toUpperCase(),
                  }))
                }
                slotProps={{ htmlInput: { maxLength: 3 } }}
                required
              />
            </Grid>
            <Grid size={{ xs: 12, sm: 4 }}>
              <TextField
                label="Trial days"
                type="number"
                fullWidth
                value={draft.trialDays}
                onChange={(event) =>
                  setDraft((current) => ({ ...current, trialDays: event.target.value }))
                }
                slotProps={{ htmlInput: { min: 0, max: 365 } }}
              />
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
            <Grid size={{ xs: 12, sm: 3 }}>
              <TextField
                label="Max users"
                type="number"
                fullWidth
                value={draft.maxUsers}
                onChange={(event) =>
                  setDraft((current) => ({ ...current, maxUsers: event.target.value }))
                }
                helperText="Blank = unlimited"
              />
            </Grid>
            <Grid size={{ xs: 12, sm: 3 }}>
              <TextField
                label="Max departments"
                type="number"
                fullWidth
                value={draft.maxDepartments}
                onChange={(event) =>
                  setDraft((current) => ({ ...current, maxDepartments: event.target.value }))
                }
                helperText="Blank = unlimited"
              />
            </Grid>
            <Grid size={{ xs: 12, sm: 3 }}>
              <TextField
                label="Requests / month"
                type="number"
                fullWidth
                value={draft.maxRequestsMonth}
                onChange={(event) =>
                  setDraft((current) => ({ ...current, maxRequestsMonth: event.target.value }))
                }
                helperText="Blank = unlimited"
              />
            </Grid>
            <Grid size={{ xs: 12, sm: 3 }}>
              <TextField
                label="Storage (MB)"
                type="number"
                fullWidth
                value={draft.maxStorageMb}
                onChange={(event) =>
                  setDraft((current) => ({ ...current, maxStorageMb: event.target.value }))
                }
                helperText="Blank = unlimited"
              />
            </Grid>
            <Grid size={{ xs: 12 }}>
              <Box
                sx={{
                  border: '1px solid',
                  borderColor: 'divider',
                  borderRadius: 2,
                  p: 2,
                }}
              >
                <Stack
                  direction={{ xs: 'column', sm: 'row' }}
                  justifyContent="space-between"
                  alignItems={{ xs: 'flex-start', sm: 'center' }}
                  spacing={1}
                  sx={{ mb: 1.5 }}
                >
                  <Box>
                    <Typography variant="h6">Included features</Typography>
                    <Typography variant="body2" color="text.secondary">
                      Select the capabilities customers receive on this plan.
                    </Typography>
                  </Box>
                  <Stack direction="row" spacing={1} alignItems="center">
                    <Chip
                      size="small"
                      label={`${draft.featureCodes.length} selected`}
                      color="primary"
                      variant="outlined"
                    />
                    <Button component={Link} to="/platform/plans?tab=features" size="small">
                      Manage features
                    </Button>
                  </Stack>
                </Stack>
                <TextField
                  size="small"
                  fullWidth
                  label="Search features"
                  value={featureSearch}
                  onChange={(event) => setFeatureSearch(event.target.value)}
                  sx={{ mb: 1.5 }}
                />
                {features.isLoading ? (
                  <Typography variant="body2" color="text.secondary">
                    Loading features…
                  </Typography>
                ) : (features.data ?? []).length === 0 ? (
                  <Alert severity="info">
                    No features are configured. Add features from the Features tab first.
                  </Alert>
                ) : (
                  <Stack spacing={1.5}>
                    {FEATURE_CATEGORIES.map((category) => {
                      const items = visibleFeatures.filter(
                        (feature) => feature.category === category.value,
                      );
                      if (items.length === 0) return null;
                      return (
                        <Box key={category.value}>
                          <Typography
                            variant="overline"
                            color="text.secondary"
                            sx={{ display: 'block', lineHeight: 1.8 }}
                          >
                            {category.label}
                          </Typography>
                          <Grid container spacing={1}>
                            {items.map((feature) => {
                              const selected = draft.featureCodes.includes(feature.code ?? '');
                              return (
                                <Grid key={feature.code} size={{ xs: 12, md: 6 }}>
                                  <Stack
                                    direction="row"
                                    spacing={1}
                                    alignItems="flex-start"
                                    sx={{
                                      p: 1,
                                      borderRadius: 1,
                                      bgcolor: selected ? 'action.hover' : 'transparent',
                                    }}
                                  >
                                    <Checkbox
                                      size="small"
                                      checked={selected}
                                      disabled={!feature.active && !selected}
                                      onChange={(event) =>
                                        setDraft((current) => ({
                                          ...current,
                                          featureCodes: event.target.checked
                                            ? [...current.featureCodes, feature.code!]
                                            : current.featureCodes.filter(
                                                (code) => code !== feature.code,
                                              ),
                                        }))
                                      }
                                    />
                                    <Box>
                                      <Stack
                                        direction="row"
                                        spacing={0.5}
                                        alignItems="center"
                                        flexWrap="wrap"
                                        useFlexGap
                                      >
                                        <Typography variant="body2" fontWeight={600}>
                                          {feature.name}
                                        </Typography>
                                        {!feature.active && (
                                          <Chip
                                            size="small"
                                            label="Inactive"
                                            color="default"
                                            variant="outlined"
                                          />
                                        )}
                                      </Stack>
                                      <Typography variant="caption" color="text.secondary">
                                        {feature.description}
                                      </Typography>
                                    </Box>
                                  </Stack>
                                </Grid>
                              );
                            })}
                          </Grid>
                        </Box>
                      );
                    })}
                  </Stack>
                )}
              </Box>
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <TextField
                label="Stripe monthly price ID"
                fullWidth
                value={draft.stripePriceMonthly}
                onChange={(event) =>
                  setDraft((current) => ({ ...current, stripePriceMonthly: event.target.value }))
                }
              />
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <TextField
                label="Stripe annual price ID"
                fullWidth
                value={draft.stripePriceAnnual}
                onChange={(event) =>
                  setDraft((current) => ({ ...current, stripePriceAnnual: event.target.value }))
                }
              />
            </Grid>
          </Grid>

          <Stack direction="row" spacing={1} sx={{ mt: 3 }}>
            <Button
              variant="contained"
              disabled={
                !draft.code ||
                !draft.name ||
                !draft.description ||
                !draft.monthlyPrice ||
                !draft.annualPrice ||
                save.isPending
              }
              onClick={() => save.mutate()}
            >
              {editing ? 'Save plan' : 'Create plan'}
            </Button>
            <Button component={Link} to="/platform/plans">
              Cancel
            </Button>
            {editing && plan?.active && (
              <Button color="error" onClick={() => setDeactivateOpen(true)}>
                Deactivate plan
              </Button>
            )}
          </Stack>
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 2 }}>
            Deactivation is soft: existing tenant subscriptions stay on this plan, but it is hidden
            from new tenants and checkout.
          </Typography>
        </CardContent>
      </Card>

      <ConfirmDialog
        open={deactivateOpen}
        title="Deactivate plan"
        message={`Deactivate ${plan?.name ?? 'this plan'}? Existing tenants keep the plan.`}
        confirmLabel="Deactivate"
        color="error"
        busy={deactivate.isPending}
        onCancel={() => setDeactivateOpen(false)}
        onConfirm={() => deactivate.mutate()}
      />
    </>
  );
}
