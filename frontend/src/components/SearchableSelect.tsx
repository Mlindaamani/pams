import CheckIcon from '@mui/icons-material/Check';
import { Autocomplete, Box, TextField, Typography, type SxProps, type Theme } from '@mui/material';

export interface SearchableOption {
  value: string;
  label: string;
  description?: string;
}

interface Props {
  label: string;
  value: string;
  options: SearchableOption[];
  onChange: (value: string) => void;
  size?: 'small' | 'medium';
  fullWidth?: boolean;
  disabled?: boolean;
  sx?: SxProps<Theme>;
}

export function SearchableSelect({
  label,
  value,
  options,
  onChange,
  size = 'small',
  fullWidth = true,
  disabled = false,
  sx,
}: Props) {
  const selected = options.find((option) => option.value === value) ?? null;

  return (
    <Autocomplete
      options={options}
      value={selected ?? undefined}
      onChange={(_event, option) => onChange(option?.value ?? '')}
      getOptionLabel={(option) => option.label}
      isOptionEqualToValue={(option, current) => option.value === current.value}
      filterOptions={(items, state) => {
        const term = state.inputValue.trim().toLowerCase();
        if (!term) return items;
        return items.filter((option) =>
          `${option.label} ${option.description ?? ''}`.toLowerCase().includes(term),
        );
      }}
      disableClearable
      openOnFocus
      disabled={disabled}
      noOptionsText="No matching options"
      slotProps={{
        popper: {
          sx: {
            zIndex: (theme) => theme.zIndex.modal + 1,
          },
        },
        paper: {
          elevation: 8,
          sx: {
            mt: 0.5,
            border: 1,
            borderColor: 'divider',
            borderRadius: 0,
            boxShadow: '0 16px 40px rgba(15, 23, 42, 0.22)',
            overflow: 'hidden',
          },
        },
        listbox: {
          sx: { maxHeight: 320, p: 0 },
        },
      }}
      renderOption={(props, option) => (
        <Box
          component="li"
          {...props}
          key={option.value}
          sx={{
            display: 'flex !important',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: 1.5,
            py: '8px !important',
            borderBottom: 1,
            borderColor: 'divider',
            '&:last-of-type': { borderBottom: 0 },
          }}
        >
          <Box sx={{ minWidth: 0 }}>
            <Typography variant="body2">{option.label}</Typography>
            {option.description && (
              <Typography
                variant="caption"
                color="text.secondary"
                sx={{ mt: 0.35, display: 'block' }}
              >
                {option.description}
              </Typography>
            )}
          </Box>
          {option.value === value && <CheckIcon fontSize="small" color="primary" />}
        </Box>
      )}
      renderInput={(params) => (
        <TextField
          {...params}
          label={label}
          size={size}
          fullWidth={fullWidth}
          sx={[
            { '& .MuiAutocomplete-input': { textOverflow: 'ellipsis' } },
            ...(Array.isArray(sx) ? sx : [sx]),
          ]}
        />
      )}
    />
  );
}
