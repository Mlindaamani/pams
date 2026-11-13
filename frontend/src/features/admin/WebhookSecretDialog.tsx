import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  TextField,
} from '@mui/material';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import { toast } from '@/components/Toast';

export function WebhookSecretDialog({
  secret,
  onClose,
}: {
  secret: string | null;
  onClose: () => void;
}) {
  return (
    <Dialog open={Boolean(secret)} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>Webhook signing secret</DialogTitle>
      <DialogContent>
        <Alert severity="warning" sx={{ mb: 2 }}>
          Copy this secret now. It will not be shown again.
        </Alert>
        <TextField
          value={secret ?? ''}
          fullWidth
          multiline
          slotProps={{ htmlInput: { readOnly: true } }}
        />
      </DialogContent>
      <DialogActions>
        <Button
          startIcon={<ContentCopyIcon />}
          onClick={async () => {
            if (secret) {
              await navigator.clipboard.writeText(secret);
              toast.success('Secret copied to clipboard.');
            }
          }}
        >
          Copy
        </Button>
        <Button variant="contained" onClick={onClose}>
          Done
        </Button>
      </DialogActions>
    </Dialog>
  );
}
