import { Box, Card, CardContent, Stack, Typography } from '@mui/material';
import type { AuditEvent } from '@/api/types';
import { dateTime } from '@/lib/format';

export function TransitionLog({ events, loading }: { events: AuditEvent[]; loading?: boolean }) {
  return (
    <Card>
      <CardContent>
        <Typography variant="h6" gutterBottom>
          Transition log
        </Typography>
        {loading ? (
          <Typography color="text.secondary">Loading transitions…</Typography>
        ) : events.length === 0 ? (
          <Typography color="text.secondary">No transitions recorded yet.</Typography>
        ) : (
          <Stack spacing={0}>
            {events.map((event, index) => (
              <Stack key={event.id} direction="row" spacing={1.5}>
                <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
                  <Box
                    sx={{
                      mt: 0.6,
                      width: 10,
                      height: 10,
                      borderRadius: '50%',
                      bgcolor: transitionColor(event.action),
                      flexShrink: 0,
                    }}
                  />
                  {index < events.length - 1 && (
                    <Box sx={{ width: 2, flexGrow: 1, minHeight: 32, bgcolor: 'divider' }} />
                  )}
                </Box>
                <Box sx={{ pb: index < events.length - 1 ? 2 : 0, minWidth: 0 }}>
                  <Typography variant="body2" fontWeight={600}>
                    {event.action?.replaceAll('_', ' ') ?? 'Transition'}
                  </Typography>
                  <Typography variant="caption" color="text.secondary" display="block">
                    {event.actorName ?? 'System'} · {dateTime(event.occurredAt)}
                  </Typography>
                </Box>
              </Stack>
            ))}
          </Stack>
        )}
      </CardContent>
    </Card>
  );
}

function transitionColor(action?: string): string {
  if (!action) return 'text.disabled';
  if (action.includes('reject') || action.includes('cancel') || action.includes('delete')) {
    return 'error.main';
  }
  if (action.includes('approve') || action.includes('submit')) return 'success.main';
  if (action.includes('change') || action.includes('return')) return 'warning.main';
  return 'primary.main';
}
