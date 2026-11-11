import type { ReactNode } from 'react';
import { Box, Typography } from '@mui/material';
import CheckIcon from '@mui/icons-material/Check';
import CloseIcon from '@mui/icons-material/Close';
import RemoveIcon from '@mui/icons-material/Remove';
import SwapHorizIcon from '@mui/icons-material/SwapHoriz';
import type { ApprovalInstance, ApprovalStep } from '@/api/types';
import { dateTime } from '@/lib/format';

const statusStyle = {
  APPROVED: { label: 'Completed', dot: 'success.main' },
  PENDING: { label: 'Waiting', dot: 'divider' },
  REJECTED: { label: 'Rejected', dot: 'error.main' },
  SKIPPED: { label: 'Skipped', dot: 'text.disabled' },
  DELEGATED: { label: 'Delegated', dot: 'warning.main' },
} as const;

interface ApprovalTimelineProps {
  timeline: ApprovalInstance;
  requesterName?: string;
  submittedAt?: string;
}

export function ApprovalTimeline({ timeline, requesterName, submittedAt }: ApprovalTimelineProps) {
  const steps = timeline.steps ?? [];

  return (
    <Box>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        Policy version {timeline.policyVersion ?? '—'}
      </Typography>

      <Box sx={{ display: 'flex', alignItems: 'stretch', gap: 1.5 }}>
        <TimelineMarker
          last={steps.length === 0}
          current={false}
          dotColor="success.main"
          filled
          lineColor="success.main"
          label="Request submitted"
        >
          <CheckIcon sx={{ fontSize: 12 }} />
        </TimelineMarker>
        <Box sx={{ flex: 1, minWidth: 0, pb: steps.length === 0 ? 0 : 2.5 }}>
          <Typography fontWeight={600} sx={{ overflowWrap: 'anywhere' }}>
            Submitted by {requesterName ?? 'Requester'}
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
            Requester · {dateTime(submittedAt)}
          </Typography>
        </Box>
      </Box>

      {steps.map((step, index) => (
        <TimelineStep
          key={step.id}
          step={step}
          last={index === steps.length - 1}
          current={
            timeline.status === 'IN_PROGRESS' &&
            index === steps.findIndex((candidate) => candidate.status === 'PENDING')
          }
        />
      ))}
    </Box>
  );
}

function TimelineMarker({
  last,
  current,
  dotColor,
  filled,
  lineColor,
  label,
  children,
}: {
  last: boolean;
  current: boolean;
  dotColor: string;
  filled: boolean;
  lineColor: string;
  label: string;
  children: ReactNode;
}) {
  return (
    <Box
      sx={{
        width: 16,
        flexShrink: 0,
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
      }}
    >
      <Box
        aria-label={label}
        sx={{
          mt: 0.6,
          width: 18,
          height: 18,
          borderRadius: '50%',
          border: 2,
          borderColor: dotColor,
          bgcolor: filled ? dotColor : 'background.paper',
          color: 'common.white',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          boxShadow: current ? `0 0 0 4px rgba(11, 92, 173, 0.14)` : 3,
          zIndex: 1,
        }}
      >
        {children}
      </Box>
      {!last && (
        <Box
          aria-hidden
          sx={{
            width: 2,
            flexGrow: 1,
            minHeight: 34,
            my: 0.5,
            borderRadius: 1,
            bgcolor: lineColor,
          }}
        />
      )}
    </Box>
  );
}

function TimelineStep({
  step,
  last,
  current,
}: {
  step: ApprovalStep;
  last: boolean;
  current: boolean;
}) {
  const status = statusStyle[step.status as keyof typeof statusStyle] ?? statusStyle.PENDING;
  const dotColor = current ? 'primary.main' : status.dot;
  const filled =
    step.status === 'APPROVED' || step.status === 'REJECTED' || step.status === 'DELEGATED';
  const lineColor =
    step.status === 'APPROVED'
      ? 'success.main'
      : step.status === 'REJECTED'
        ? 'error.main'
        : step.status === 'DELEGATED'
          ? 'warning.main'
          : 'divider';

  return (
    <Box sx={{ display: 'flex', alignItems: 'stretch', gap: 1.5 }}>
      <TimelineMarker
        last={last}
        current={current}
        dotColor={dotColor}
        filled={filled}
        lineColor={lineColor}
        label={current ? 'Current approval step' : status.label}
      >
        {step.status === 'APPROVED' && <CheckIcon sx={{ fontSize: 12 }} />}
        {step.status === 'REJECTED' && <CloseIcon sx={{ fontSize: 12 }} />}
        {step.status === 'DELEGATED' && <SwapHorizIcon sx={{ fontSize: 11 }} />}
        {step.status === 'SKIPPED' && <RemoveIcon sx={{ fontSize: 12, color: 'text.disabled' }} />}
        {step.status === 'PENDING' && current && (
          <Box
            sx={{
              width: 6,
              height: 6,
              borderRadius: '50%',
              bgcolor: 'primary.main',
            }}
          />
        )}
      </TimelineMarker>

      <Box sx={{ flex: 1, minWidth: 0, pb: last ? 0 : 2.5 }}>
        <Typography fontWeight={600} sx={{ overflowWrap: 'anywhere' }}>
          {step.sequence}. {step.name}
        </Typography>

        <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
          {step.assigneeRole ?? step.assigneeUserId ?? 'Requester manager'}
        </Typography>

        {step.actedByName && (
          <Typography variant="body2" sx={{ mt: 0.5 }}>
            {step.actedByName} · {dateTime(step.actedAt)}
          </Typography>
        )}

        {step.status === 'PENDING' && step.slaDueAt && (
          <Typography variant="caption" color="text.secondary">
            Due {dateTime(step.slaDueAt)}
          </Typography>
        )}

        {step.comment && (
          <Box
            sx={{
              mt: 1,
              px: 1.25,
              py: 0.75,
              borderLeft: 2,
              borderColor: dotColor,
              bgcolor: 'action.hover',
              borderRadius: 1,
            }}
          >
            <Typography variant="body2" sx={{ overflowWrap: 'anywhere' }}>
              “{step.comment}”
            </Typography>
          </Box>
        )}
      </Box>
    </Box>
  );
}
