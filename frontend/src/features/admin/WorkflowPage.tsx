import { Link as RouterLink, useSearchParams } from 'react-router-dom';
import { Button, Stack, Tab, Tabs } from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import { Can } from '@/auth/AbilityProvider';
import { PageHeader } from '@/components/Page';
import { WorkflowActionsTab } from './WorkflowActionsTab';
import { WorkflowPoliciesTab } from './WorkflowPoliciesTab';

export function WorkflowPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const tab = searchParams.get('tab') === 'actions' ? 1 : 0;

  return (
    <>
      <PageHeader
        title="Workflow"
        actions={
          tab === 0 ? (
            <Can I="manage" a="Policy">
              <Button
                component={RouterLink}
                to="/admin/workflow/policies/new"
                variant="contained"
                startIcon={<AddIcon />}
              >
                New workflow policy
              </Button>
            </Can>
          ) : undefined
        }
      />
      <Stack spacing={2}>
        <Tabs
          value={tab}
          onChange={(_, value: number) =>
            setSearchParams(value === 1 ? { tab: 'actions' } : { tab: 'policies' }, {
              replace: true,
            })
          }
        >
          <Tab label="Workflow policies" />
          <Tab label="Workflow actions" />
        </Tabs>
        {tab === 0 ? <WorkflowPoliciesTab /> : <WorkflowActionsTab />}
      </Stack>
    </>
  );
}
