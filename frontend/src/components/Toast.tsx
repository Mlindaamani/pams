import { useEffect, useState, type PropsWithChildren } from 'react';
import { Box, IconButton, Paper, Slide, Snackbar, Stack, Typography } from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutline';
import InfoOutlinedIcon from '@mui/icons-material/InfoOutlined';

export type ToastSeverity = 'success' | 'error' | 'info';

interface ToastItem {
  id: number;
  severity: ToastSeverity;
  title?: string;
  message: string;
}

let listener: ((item: ToastItem) => void) | null = null;
let counter = 0;

function publish(severity: ToastSeverity, message: string, title?: string) {
  listener?.({ id: ++counter, severity, title, message });
}

export const toast = {
  success: (message: string, title?: string) => publish('success', message, title),
  error: (message: string, title?: string) => publish('error', message, title),
  info: (message: string, title?: string) => publish('info', message, title),
};

const accent: Record<ToastSeverity, string> = {
  success: '#2e7d32',
  error: '#c62828',
  info: '#0b5cad',
};

export function ToastProvider({ children }: PropsWithChildren) {
  const [queue, setQueue] = useState<ToastItem[]>([]);

  useEffect(() => {
    listener = (item) => setQueue((current) => [...current.slice(-2), item]);
    return () => {
      listener = null;
    };
  }, []);

  const current = queue[0];
  const close = () => setQueue((currentQueue) => currentQueue.slice(1));

  return (
    <>
      {children}
      <Snackbar
        key={current?.id}
        open={Boolean(current)}
        autoHideDuration={current?.severity === 'error' ? 7000 : 4200}
        onClose={close}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
        slots={{ transition: Slide }}
      >
        {current ? (
          <Paper
            elevation={8}
            role={current.severity === 'error' ? 'alert' : 'status'}
            sx={{
              minWidth: 320,
              maxWidth: 440,
              borderRadius: 1.5,
              overflow: 'hidden',
            }}
          >
            <Stack direction="row" spacing={1.5} alignItems="flex-start" sx={{ p: 1.75 }}>
              <Box sx={{ color: accent[current.severity], display: 'flex', mt: 0.2 }}>
                {current.severity === 'success' && <CheckCircleIcon fontSize="small" />}
                {current.severity === 'error' && <ErrorOutlineIcon fontSize="small" />}
                {current.severity === 'info' && <InfoOutlinedIcon fontSize="small" />}
              </Box>
              <Box sx={{ flexGrow: 1 }}>
                <Typography variant="subtitle2">
                  {current.title ?? (current.severity === 'error' ? 'PAMS error' : 'PAMS')}
                </Typography>
                <Typography variant="body2" sx={{ mt: 0.5, whiteSpace: 'pre-line' }}>
                  {current.message}
                </Typography>
              </Box>
              <IconButton size="small" aria-label="Dismiss notification" onClick={close}>
                <CloseIcon fontSize="small" />
              </IconButton>
            </Stack>
          </Paper>
        ) : (
          <span />
        )}
      </Snackbar>
    </>
  );
}
