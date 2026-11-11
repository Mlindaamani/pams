import { useEffect, useMemo, useState } from 'react';
import {
  Box,
  Dialog,
  DialogContent,
  InputAdornment,
  List,
  ListItemButton,
  ListItemText,
  TextField,
  Typography,
} from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';

interface CommandItem {
  label: string;
  to: string;
}

interface Props {
  open: boolean;
  onClose: () => void;
  items: CommandItem[];
  onSelect: (to: string) => void;
}

export function CommandPalette({ open, onClose, items, onSelect }: Props) {
  const [query, setQuery] = useState('');

  useEffect(() => {
    if (open) setQuery('');
  }, [open]);

  const filtered = useMemo(() => {
    const normalized = query.trim().toLowerCase();
    if (!normalized) return items;
    return items.filter((item) => item.label.toLowerCase().includes(normalized));
  }, [items, query]);

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogContent sx={{ pb: 1 }}>
        <TextField
          autoFocus
          fullWidth
          placeholder="Search pages…"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === 'Enter' && filtered[0]) {
              onSelect(filtered[0].to);
            }
          }}
          slotProps={{
            input: {
              startAdornment: (
                <InputAdornment position="start">
                  <SearchIcon fontSize="small" />
                </InputAdornment>
              ),
            },
          }}
        />
        <Box sx={{ mt: 1, maxHeight: 360, overflowY: 'auto' }}>
          {filtered.length === 0 ? (
            <Typography color="text.secondary" sx={{ p: 2, textAlign: 'center' }}>
              No matching pages.
            </Typography>
          ) : (
            <List dense>
              {filtered.map((item) => (
                <ListItemButton key={item.to} onClick={() => onSelect(item.to)}>
                  <ListItemText primary={item.label} secondary={item.to} />
                </ListItemButton>
              ))}
            </List>
          )}
        </Box>
      </DialogContent>
    </Dialog>
  );
}
