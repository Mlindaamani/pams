import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Divider,
  Grid,
  Stack,
  Typography,
} from '@mui/material';
import { useRefreshEntitlements } from '@/auth/AbilityProvider';
import { Loading, PageHeader } from '@/components/Page';
import { toast } from '@/components/Toast';
import { problemMessage } from '@/api/client';
import { dateOnly } from '@/lib/format';
import { getSubscription, previewPlanChange, startCheckout } from './api';

const usd = (value: string | number | undefined, currency = 'USD') =>
  new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency,
    maximumFractionDigits: 2,
  }).format(Number(value ?? 0));

export function PlanChangePreviewPage() {
  const [searchParams] = useSearchParams();
  const planCode = searchParams.get('planCode') ?? '';
  const interval = searchParams.get('interval') === 'ANNUAL' ? 'ANNUAL' : 'MONTHLY';
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const refreshEntitlements = useRefreshEntitlements();
  const subscription = useQuery({ queryKey: ['subscription'], queryFn: getSubscription });
  const preview = useQuery({
    queryKey: ['plan-change-preview', planCode, interval],
    queryFn: () => previewPlanChange(planCode, interval),
    enabled: Boolean(planCode),
  });

  const confirm = useMutation({
    mutationFn: () => startCheckout(planCode, interval),
    onSuccess: (result) => {
      if (result?.url) {
        window.location.assign(result.url);
        return;
      }
      toast.success('Subscription plan updated.');
      void queryClient.invalidateQueries({ queryKey: ['subscription'] });
      void queryClient.invalidateQueries({ queryKey: ['subscription-plans'] });
      refreshEntitlements();
      navigate('/admin/billing');
    },
    onError: (error) => toast.error(problemMessage(error)),
  });

  if (!planCode) {
    return <Alert severity="error">No plan was selected.</Alert>;
  }
  if (subscription.isLoading || preview.isLoading) return <Loading />;
  if (subscription.error || !subscription.data) {
    return <Alert severity="error">Current subscription could not be loaded.</Alert>;
  }
  if (preview.error || !preview.data) {
    return <Alert severity="error">Plan change preview could not be loaded.</Alert>;
  }

  const data = preview.data;
  const currentPlan = subscription.data.plan;
  const stripeBacked = data.billingSource === 'STRIPE';
  const amountDue = usd(data.amountDue, data.currency);
  const nextBilling = usd(data.nextBillingAmount, data.currency);

  return (
    <>
      <PageHeader title="Review plan change" backTo="/admin/billing" backLabel="Back to billing" />
      <Grid container spacing={2}>
        <Grid size={{ xs: 12, md: 8 }}>
          <Card>
            <CardContent>
              <Stack spacing={2}>
                <Box>
                  <Typography color="text.secondary">Change plan</Typography>
                  <Typography variant="h5" sx={{ mt: 0.5 }}>
                    {currentPlan?.name} → {data.planName}
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    {interval === 'ANNUAL' ? 'Annual' : 'Monthly'} billing
                  </Typography>
                </Box>
                <Divider />
                {stripeBacked ? (
                  <>
                    <Grid container spacing={2}>
                      <Grid size={{ xs: 12, sm: 6 }}>
                        <Typography color="text.secondary">Estimated amount due now</Typography>
                        <Typography variant="h4">{amountDue}</Typography>
                      </Grid>
                      <Grid size={{ xs: 12, sm: 6 }}>
                        <Typography color="text.secondary">Next renewal</Typography>
                        <Typography variant="h4">{nextBilling}</Typography>
                        <Typography variant="caption" color="text.secondary">
                          {data.nextPaymentAt
                            ? dateOnly(data.nextPaymentAt)
                            : 'At next billing cycle'}
                        </Typography>
                      </Grid>
                    </Grid>
                    {data.proration && (
                      <Alert severity="info">
                        Stripe calculates prorated credits and charges when the plan changes.
                      </Alert>
                    )}
                    {(data.lines ?? []).length > 0 && (
                      <Stack spacing={1}>
                        <Typography variant="subtitle2">Proration preview</Typography>
                        {(data.lines ?? []).map((line, index) => (
                          <Stack
                            key={`${line.description}-${index}`}
                            direction="row"
                            justifyContent="space-between"
                            spacing={2}
                          >
                            <Typography variant="body2" color="text.secondary">
                              {line.description}
                            </Typography>
                            <Typography
                              variant="body2"
                              color={Number(line.amount) < 0 ? 'success.main' : 'text.primary'}
                            >
                              {usd(line.amount, data.currency)}
                            </Typography>
                          </Stack>
                        ))}
                      </Stack>
                    )}
                  </>
                ) : (
                  <Alert severity="info">
                    This subscription is manually billed. The plan changes immediately with no
                    payment collected by PAMS.
                  </Alert>
                )}
                <Stack direction="row" spacing={1}>
                  <Button
                    variant="contained"
                    disabled={confirm.isPending}
                    onClick={() => confirm.mutate()}
                  >
                    {stripeBacked ? 'Confirm and continue' : 'Confirm plan change'}
                  </Button>
                  <Button component={Link} to="/admin/billing">
                    Cancel
                  </Button>
                </Stack>
              </Stack>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </>
  );
}
