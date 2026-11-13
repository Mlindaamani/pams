import { Box, Checkbox, FormControlLabel, Stack, Typography } from '@mui/material';
import type { WebhookEventGroup } from './webhookEvents';

export function WebhookEventPicker({
  groups,
  selected,
  onChange,
}: {
  groups: WebhookEventGroup[];
  selected: string[];
  onChange: (eventTypes: string[]) => void;
}) {
  const toggleEvent = (eventType: string) => {
    onChange(
      selected.includes(eventType)
        ? selected.filter((value) => value !== eventType)
        : [...selected, eventType],
    );
  };

  const toggleGroup = (group: WebhookEventGroup) => {
    const groupValues = group.events.map((event) => event.value);
    const allSelected = groupValues.every((value) => selected.includes(value));
    onChange(
      allSelected
        ? selected.filter((value) => !groupValues.includes(value))
        : [...new Set([...selected, ...groupValues])],
    );
  };

  const half = Math.ceil(groups.length / 2);
  const columns = [groups.slice(0, half), groups.slice(half)];

  return (
    <Box
      sx={{
        display: 'grid',
        gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, minmax(0, 1fr))' },
        gap: 1.5,
        alignItems: 'start',
      }}
    >
      {columns.map((column, columnIndex) => (
        <Stack key={columnIndex} spacing={1.5}>
          {column.map((group) => (
            <EventGroupCard
              key={group.id}
              group={group}
              selected={selected}
              onToggleEvent={toggleEvent}
              onToggleGroup={() => toggleGroup(group)}
            />
          ))}
        </Stack>
      ))}
    </Box>
  );
}

function EventGroupCard({
  group,
  selected,
  onToggleEvent,
  onToggleGroup,
}: {
  group: WebhookEventGroup;
  selected: string[];
  onToggleEvent: (eventType: string) => void;
  onToggleGroup: () => void;
}) {
  const selectedCount = group.events.filter((event) => selected.includes(event.value)).length;
  const allSelected = selectedCount === group.events.length;
  const someSelected = selectedCount > 0 && !allSelected;
  const helperId = `${group.id}-events-helper`;

  return (
    <Box
      sx={{
        p: 1.5,
        border: 1,
        borderColor: 'divider',
        borderRadius: 2,
        bgcolor: 'background.paper',
      }}
    >
      <Stack direction="row" alignItems="flex-start" spacing={0.5}>
        <Checkbox
          size="small"
          checked={allSelected}
          indeterminate={someSelected}
          onChange={onToggleGroup}
          slotProps={{
            input: {
              'aria-label': `Select all ${group.label} events`,
              'aria-describedby': helperId,
            },
          }}
        />
        <Box sx={{ pt: 0.75 }}>
          <Typography variant="subtitle2">{group.label}</Typography>
          <Typography
            id={helperId}
            variant="caption"
            color="text.secondary"
            display="block"
            sx={{ mb: 0.5 }}
          >
            {group.helperText}
          </Typography>
          <Stack sx={{ ml: -1 }}>
            {group.events.map((event) => (
              <FormControlLabel
                key={event.value}
                control={
                  <Checkbox
                    size="small"
                    checked={selected.includes(event.value)}
                    onChange={() => onToggleEvent(event.value)}
                  />
                }
                label={event.label}
              />
            ))}
          </Stack>
        </Box>
      </Stack>
    </Box>
  );
}
