import { IconButton, Stack, Tooltip, Typography } from '@mui/material';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import { toast } from '@/components/Toast';

export function CopyableUrl({ value }: { value: string }) {
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(value);
      toast.success('Endpoint URL copied.');
    } catch {
      toast.error('Could not copy endpoint URL.');
    }
  };

  return (
    <Stack direction="row" spacing={0.5} alignItems="center" sx={{ minWidth: 0 }}>
      <Tooltip title={value}>
        <Typography
          variant="body2"
          noWrap
          sx={{
            minWidth: 0,
            fontFamily: 'monospace',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
          }}
        >
          {value}
        </Typography>
      </Tooltip>
      <Tooltip title="Copy endpoint URL">
        <IconButton
          size="small"
          aria-label="Copy endpoint URL"
          onClick={() => void copy()}
          sx={{ flexShrink: 0 }}
        >
          <ContentCopyIcon sx={{ fontSize: 15 }} />
        </IconButton>
      </Tooltip>
    </Stack>
  );
}
