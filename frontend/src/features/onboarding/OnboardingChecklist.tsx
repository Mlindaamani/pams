import { Link as RouterLink } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Box, Button, Card, CardContent, LinearProgress, Stack, Typography } from '@mui/material';
import CheckCircleRoundedIcon from '@mui/icons-material/CheckCircleRounded';
import RadioButtonUncheckedIcon from '@mui/icons-material/RadioButtonUnchecked';
import { getOnboardingChecklist } from './api';

const steps = [
  {
    key: 'hasDepartment',
    title: 'Create your first department',
    detail: 'Departments organize budgets, requests and approvals.',
    to: '/admin/departments',
    label: 'Departments',
  },
  {
    key: 'hasTeam',
    title: 'Invite your team',
    detail: 'Add requesters, approvers and finance users.',
    to: '/admin/users',
    label: 'Users',
  },
  {
    key: 'hasApprovalPolicy',
    title: 'Configure an approval policy',
    detail: 'Define who approves requests by amount.',
    to: '/admin/policies',
    label: 'Approval policies',
  },
  {
    key: 'hasSubmittedRequest',
    title: 'Submit your first request',
    detail: 'Run a real purchase request through approval.',
    to: '/requests/new',
    label: 'New request',
  },
  {
    key: 'hasIssuedPurchaseOrder',
    title: 'Issue your first purchase order',
    detail: 'Convert an approved request and send it to a vendor.',
    to: '/purchase-orders',
    label: 'Purchase orders',
  },
] as const;

export function OnboardingChecklist() {
  const checklist = useQuery({ queryKey: ['onboarding'], queryFn: getOnboardingChecklist });
  if (checklist.isLoading || !checklist.data || checklist.data.complete) return null;
  const data = checklist.data;
  const progress = Math.round(
    ((data.completedSteps ?? 0) / Math.max(data.totalSteps ?? steps.length, 1)) * 100,
  );

  return (
    <Card sx={{ mb: 3, borderColor: 'primary.light' }}>
      <CardContent>
        <Stack
          direction={{ xs: 'column', md: 'row' }}
          justifyContent="space-between"
          alignItems={{ xs: 'flex-start', md: 'center' }}
          spacing={2}
          sx={{ mb: 2 }}
        >
          <Box>
            <Typography variant="h6">Finish setting up your workspace</Typography>
            <Typography variant="body2" color="text.secondary">
              {data.completedSteps} of {data.totalSteps} setup steps completed.
            </Typography>
          </Box>
          <Box sx={{ width: { xs: '100%', md: 280 } }}>
            <LinearProgress
              variant="determinate"
              value={progress}
              sx={{ height: 8, borderRadius: 1 }}
            />
          </Box>
        </Stack>
        <Stack spacing={1}>
          {steps.map((step) => {
            const done = Boolean(data[step.key]);
            return (
              <Stack
                key={step.key}
                direction={{ xs: 'column', sm: 'row' }}
                spacing={1.5}
                alignItems={{ xs: 'flex-start', sm: 'center' }}
                sx={{ py: 1, borderTop: 1, borderColor: 'divider' }}
              >
                {done ? (
                  <CheckCircleRoundedIcon color="success" fontSize="small" />
                ) : (
                  <RadioButtonUncheckedIcon color="disabled" fontSize="small" />
                )}
                <Box sx={{ flexGrow: 1 }}>
                  <Typography
                    variant="body2"
                    fontWeight={600}
                    color={done ? 'text.secondary' : 'text.primary'}
                    sx={{ textDecoration: done ? 'line-through' : 'none' }}
                  >
                    {step.title}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    {step.detail}
                  </Typography>
                </Box>
                {!done && (
                  <Button component={RouterLink} to={step.to} size="small" variant="outlined">
                    {step.label}
                  </Button>
                )}
              </Stack>
            );
          })}
        </Stack>
      </CardContent>
    </Card>
  );
}
