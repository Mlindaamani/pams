import { Box, Tooltip, Typography } from '@mui/material';
import CheckIcon from '@mui/icons-material/Check';
import CloseIcon from '@mui/icons-material/Close';
import RemoveIcon from '@mui/icons-material/Remove';
import type { ApprovalInstance } from '@/api/types';

export function ApprovalProgress({ timeline }: { timeline: ApprovalInstance }) {
  const stages = new Map<number, NonNullable<ApprovalInstance['steps']>>();
  (timeline.steps ?? []).forEach((step) => {
    const stage = step.sequence ?? 1;
    stages.set(stage, [...(stages.get(stage) ?? []), step]);
  });
  const sorted = [...stages.entries()].sort(([left], [right]) => left - right);

  return (
    <Box
      aria-label="Approval progress"
      sx={{
        display: 'flex',
        alignItems: 'flex-start',
        gap: 0,
        overflowX: 'auto',
        py: 0.5,
        scrollbarWidth: 'thin',
      }}
    >
      <ProgressPoint label="Submitted" detail="Request submitted" state="complete" first />
      {sorted.map(([stage, steps]) => {
        const complete = steps.every(
          (step) => step.status === 'APPROVED' || step.status === 'SKIPPED',
        );
        const rejected = steps.some((step) => step.status === 'REJECTED');
        const current = timeline.status === 'IN_PROGRESS' && timeline.currentSequence === stage;
        const roles = [
          ...new Set(steps.map((step) => step.assigneeRole ?? step.name ?? 'Approver')),
        ];
        return (
          <ProgressPoint
            key={stage}
            label={`Stage ${stage}`}
            detail={roles.join(' + ')}
            state={rejected ? 'rejected' : complete ? 'complete' : current ? 'current' : 'pending'}
            tooltip={steps
              .map((step) => `${step.name}: ${(step.status ?? 'PENDING').toLowerCase()}`)
              .join('\n')}
          />
        );
      })}
    </Box>
  );
}

function ProgressPoint({
  label,
  detail,
  state,
  first = false,
  tooltip,
}: {
  label: string;
  detail: string;
  state: 'complete' | 'current' | 'pending' | 'rejected';
  first?: boolean;
  tooltip?: string;
}) {
  const color =
    state === 'complete'
      ? 'success.main'
      : state === 'current'
        ? 'primary.main'
        : state === 'rejected'
          ? 'error.main'
          : 'divider';
  const content = (
    <Box sx={{ display: 'flex', alignItems: 'flex-start', minWidth: 104 }}>
      <Box sx={{ display: 'flex', alignItems: 'center', width: first ? 20 : 34, flexShrink: 0 }}>
        {!first && (
          <Box
            aria-hidden
            sx={{
              height: 2,
              width: 16,
              bgcolor: state === 'complete' ? 'success.main' : 'divider',
            }}
          />
        )}
        <Box
          sx={{
            width: 20,
            height: 20,
            borderRadius: '50%',
            border: 2,
            borderColor: color,
            bgcolor: state === 'complete' || state === 'rejected' ? color : 'background.paper',
            color: 'common.white',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            flexShrink: 0,
          }}
        >
          {state === 'complete' && <CheckIcon sx={{ fontSize: 12 }} />}
          {state === 'rejected' && <CloseIcon sx={{ fontSize: 12 }} />}
          {state === 'pending' && <RemoveIcon sx={{ fontSize: 11, color: 'text.disabled' }} />}
          {state === 'current' && (
            <Box sx={{ width: 6, height: 6, borderRadius: '50%', bgcolor: 'primary.main' }} />
          )}
        </Box>
      </Box>
      <Box sx={{ ml: 1, pr: 2, minWidth: 0 }}>
        <Typography variant="caption" fontWeight={700} display="block" noWrap>
          {label}
        </Typography>
        <Typography
          variant="caption"
          color="text.secondary"
          display="block"
          noWrap
          sx={{ maxWidth: 150 }}
        >
          {detail}
        </Typography>
      </Box>
    </Box>
  );
  return tooltip ? <Tooltip title={tooltip}>{content}</Tooltip> : content;
}
