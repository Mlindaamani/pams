import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Box, Button, Card, CardContent, Chip, Stack, Typography } from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import { Can } from '@/auth/AbilityProvider';
import { DataTable } from '@/components/DataTable';
import { Loading } from '@/components/Page';
import { listSubscriptionFeatures } from '@/features/subscription/api';

const CATEGORY_LABELS: Record<string, string> = {
  CORE: 'Core',
  PROCUREMENT: 'Procurement',
  FINANCE: 'Finance',
  INTEGRATIONS: 'Integrations',
  SECURITY: 'Security',
  SUPPORT: 'Support',
};

export function PlatformFeaturesTab() {
  const features = useQuery({
    queryKey: ['platform-subscription-features'],
    queryFn: listSubscriptionFeatures,
  });

  if (features.isLoading) return <Loading />;

  return (
    <Card>
      <CardContent>
        <Stack
          direction="row"
          justifyContent="space-between"
          alignItems="center"
          spacing={2}
          sx={{ mb: 2 }}
        >
          <Box>
            <Typography variant="h6">Feature catalogue</Typography>
            <Typography variant="body2" color="text.secondary">
              Feature labels available when composing subscription plans.
            </Typography>
          </Box>
          <Can I="manage" a="Tenant">
            <Button
              component={Link}
              to="/platform/features/new"
              variant="contained"
              size="small"
              startIcon={<AddIcon />}
            >
              New feature
            </Button>
          </Can>
        </Stack>
        <DataTable
          ariaLabel="Subscription features"
          rows={features.data ?? []}
          getRowId={(feature) => feature.code ?? ''}
          emptyMessage="No subscription features configured."
          columns={[
            {
              key: 'feature',
              label: 'Feature',
              render: (feature) => (
                <Box>
                  <Typography variant="body2" fontWeight={600}>
                    {feature.name}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    {feature.code}
                  </Typography>
                </Box>
              ),
              sortValue: (feature) => feature.name ?? '',
            },
            {
              key: 'category',
              label: 'Category',
              render: (feature) => (
                <Chip
                  size="small"
                  variant="outlined"
                  label={CATEGORY_LABELS[feature.category ?? ''] ?? feature.category}
                />
              ),
              sortValue: (feature) => feature.category ?? '',
            },
            {
              key: 'description',
              label: 'Description',
              render: (feature) => (
                <Typography variant="body2" color="text.secondary">
                  {feature.description}
                </Typography>
              ),
            },
            {
              key: 'status',
              label: 'Status',
              render: (feature) => (
                <Chip
                  size="small"
                  label={feature.active ? 'Active' : 'Inactive'}
                  color={feature.active ? 'success' : 'default'}
                  variant="outlined"
                />
              ),
            },
            {
              key: 'actions',
              label: 'Actions',
              align: 'right',
              render: (feature) => (
                <Can I="manage" a="Tenant">
                  <Button
                    component={Link}
                    to={`/platform/features/${feature.code}/edit`}
                    size="small"
                  >
                    Edit
                  </Button>
                </Can>
              ),
            },
          ]}
        />
      </CardContent>
    </Card>
  );
}
