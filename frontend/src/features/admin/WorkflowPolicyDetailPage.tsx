import type { ReactNode } from 'react';
import { Link as RouterLink, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Box, Button, Card, CardContent, Chip, Grid, Stack, Typography } from '@mui/material';
import { Can } from '@/auth/AbilityProvider';
import { Loading, PageHeader } from '@/components/Page';
import { dateTime } from '@/lib/format';
import { getPolicy } from './api';
import type { PolicyStep } from './types';

export function WorkflowPolicyDetailPage() {
  const { id } = useParams();
  const policy = useQuery({ queryKey: ['policy', id], queryFn: () => getPolicy(id!) });

  if (policy.isLoading) return <Loading />;
  if (policy.error || !policy.data) {
    return <Typography color="error">This workflow policy could not be loaded.</Typography>;
  }

  const data = policy.data;
  const stages = new Map<number, NonNullable<typeof data.steps>>();
  (data.steps ?? []).forEach((step) => {
    const stage = step.sequence ?? 1;
    stages.set(stage, [...(stages.get(stage) ?? []), step]);
  });

  return (
    <>
      <PageHeader
        title={`Workflow policy · ${data.name}`}
        backTo="/admin/workflow?tab=policies"
        actions={
          <Can I="manage" a="Policy">
            <Button
              component={RouterLink}
              to={`/admin/workflow/policies/${data.id}/new-version`}
              variant="contained"
            >
              New version
            </Button>
          </Can>
        }
      />

      <Grid container spacing={2}>
        <Grid size={{ xs: 12, md: 8 }}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Definition
              </Typography>
              <Grid container spacing={2}>
                <Info
                  label="Applies to"
                  value={
                    data.entityType === 'PURCHASE_ORDER' ? 'Purchase orders' : 'Purchase requests'
                  }
                />
                <Info label="Amount band" value={`${data.amountMin} – ${data.amountMax ?? '∞'}`} />
                <Info label="Version" value={String(data.policyVersion ?? 1)} />
                <Info
                  label="Status"
                  value={
                    <Chip
                      size="small"
                      label={data.active ? 'Active' : 'Inactive'}
                      color={data.active ? 'success' : 'default'}
                      variant="outlined"
                    />
                  }
                />
                <Info label="Created" value={dateTime(data.createdAt)} />
                <Info label="Updated" value={dateTime(data.updatedAt)} />
              </Grid>
            </CardContent>
          </Card>

          <Card sx={{ mt: 2 }}>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Approval stages
              </Typography>
              {stages.size === 0 ? (
                <Typography color="text.secondary">No stages configured.</Typography>
              ) : (
                <Stack direction="row" spacing={1.5} sx={{ overflowX: 'auto', pb: 1 }}>
                  {[...stages.entries()]
                    .sort(([left], [right]) => left - right)
                    .map(([stage, steps]) => (
                      <Box
                        key={stage}
                        sx={{
                          minWidth: 260,
                          flex: 1,
                          p: 2,
                          border: 1,
                          borderColor: 'divider',
                          borderRadius: 2,
                          bgcolor: 'grey.50',
                        }}
                      >
                        <Stack
                          direction="row"
                          justifyContent="space-between"
                          alignItems="center"
                          sx={{ mb: 1 }}
                        >
                          <Typography variant="overline" color="text.secondary">
                            Stage {stage}
                          </Typography>
                          <Chip
                            size="small"
                            label={`Quorum ${steps[0]?.minApprovals ?? steps.length}`}
                            variant="outlined"
                          />
                        </Stack>
                        {steps.length > 1 && (
                          <Typography variant="caption" color="text.secondary">
                            Parallel approvers
                          </Typography>
                        )}
                        <Stack spacing={1} sx={{ mt: 1 }}>
                          {steps.map((step) => (
                            <Box
                              key={step.id}
                              sx={{
                                p: 1.25,
                                border: 1,
                                borderColor: 'divider',
                                borderRadius: 1.5,
                                bgcolor: 'background.paper',
                              }}
                            >
                              <Typography variant="body2" fontWeight={600}>
                                {step.name}
                              </Typography>
                              <Typography variant="caption" color="text.secondary" display="block">
                                {actorLabel(step)} · {conditionLabel(step)}
                              </Typography>
                              <Typography variant="caption" color="text.secondary">
                                SLA {step.slaHours}h
                              </Typography>
                            </Box>
                          ))}
                        </Stack>
                      </Box>
                    ))}
                </Stack>
              )}
            </CardContent>
          </Card>
        </Grid>

        <Grid size={{ xs: 12, md: 4 }}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Policy notes
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Changes create a new immutable policy version. In-flight approvals keep the version
                and stage rules they started with.
              </Typography>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </>
  );
}

function Info({ label, value }: { label: string; value: ReactNode }) {
  return (
    <Grid size={{ xs: 12, sm: 6, md: 4 }}>
      <Typography variant="caption" color="text.secondary" display="block">
        {label}
      </Typography>
      {typeof value === 'string' ? <Typography variant="body2">{value}</Typography> : value}
    </Grid>
  );
}

function actorLabel(step: PolicyStep): string {
  if (step.approverType === 'REQUESTER_MANAGER') return 'Requester manager';
  if (step.approverType === 'USER') return step.approverUserId ?? 'Specific user';
  return step.approverRole ?? 'Role';
}

function conditionLabel(step: PolicyStep): string {
  if (step.conditionType === 'BUDGET_EXCEPTION') return 'Budget exception';
  if (step.conditionType === 'DEPARTMENT') return 'Specific department';
  return 'Always';
}
