import { createTheme, type PaletteMode } from '@mui/material/styles';

export function createAppTheme(
  mode: PaletteMode,
  brand?: {
    primaryColor?: string;
    secondaryColor?: string;
    cornerRadius?: number;
    fontFamily?: string;
    uiDensity?: string;
  },
) {
  const dark = mode === 'dark';
  const primary = brand?.primaryColor ?? '#0B5CAD';
  const secondary = brand?.secondaryColor ?? '#00695C';
  const fonts: Record<string, string> = {
    INTER: 'Inter,Roboto,Helvetica,Arial,sans-serif',
    ROBOTO: 'Roboto,Helvetica,Arial,sans-serif',
    SYSTEM: 'system-ui,-apple-system,BlinkMacSystemFont,Segoe UI,sans-serif',
    SERIF: 'Georgia,Cambria,Times New Roman,serif',
  };
  const fontFamily = fonts[brand?.fontFamily ?? 'INTER'] ?? fonts.INTER;
  const compact = brand?.uiDensity === 'COMPACT';
  return createTheme({
    palette: {
      mode,
      primary: {
        main: primary,
        light: primary,
        dark: primary,
        contrastText: '#FFFFFF',
      },
      secondary: {
        main: secondary,
        light: secondary,
        dark: secondary,
        contrastText: '#FFFFFF',
      },
      background: dark
        ? { default: '#0B1220', paper: '#111A2B' }
        : { default: '#F5F7FA', paper: '#FFFFFF' },
      text: dark
        ? { primary: '#E5EDF7', secondary: '#9FB0C7' }
        : { primary: '#1F2937', secondary: '#6B7280' },
      divider: dark ? 'rgba(255,255,255,0.12)' : '#E0E4EA',
      error: { main: dark ? '#F28B82' : '#C62828' },
      warning: { main: dark ? '#F6AD55' : '#EF6C00' },
      success: { main: dark ? '#6BD38A' : '#2E7D32' },
      info: { main: dark ? '#5AA7F5' : '#0B5CAD' },
    },
    shape: { borderRadius: brand?.cornerRadius ?? 10 },
    typography: {
      fontFamily,
      h4: { fontWeight: 700, letterSpacing: '-0.02em' },
      h5: { fontWeight: 700, letterSpacing: '-0.01em' },
      h6: { fontWeight: 650 },
      button: { textTransform: 'none', fontWeight: 600 },
    },
    components: {
      MuiCssBaseline: {
        styleOverrides: {
          a: {
            color: dark ? '#5AA7F5' : '#0B5CAD',
            fontWeight: 600,
            textDecoration: 'none',
            transition: 'color 140ms ease',
            '&:not(.MuiButtonBase-root):hover': {
              color: dark ? '#8CC3FA' : '#074079',
              textDecoration: 'none',
            },
            '&:focus-visible': {
              outline: `2px solid ${dark ? '#8CC3FA' : '#0B5CAD'}`,
              outlineOffset: 2,
              borderRadius: 2,
            },
          },
        },
      },
      MuiLink: {
        defaultProps: { underline: 'none' },
        styleOverrides: {
          root: {
            fontWeight: 600,
            textDecoration: 'none',
            '&:hover': { textDecoration: 'none' },
          },
        },
      },
      MuiAppBar: {
        styleOverrides: {
          root: {
            backgroundImage: 'none',
            backgroundColor: '#0B5CAD',
            boxShadow: dark ? '0 1px 0 rgba(255,255,255,0.08)' : '0 2px 10px rgba(9, 40, 77, 0.18)',
          },
        },
      },
      MuiCard: {
        styleOverrides: {
          root: {
            backgroundImage: 'none',
            border: `1px solid ${dark ? 'rgba(255,255,255,0.10)' : '#E0E4EA'}`,
            boxShadow: dark ? 'none' : '0 1px 2px rgba(16, 24, 40, 0.05)',
          },
        },
      },
      MuiPaper: { styleOverrides: { root: { backgroundImage: 'none' } } },
      MuiTableCell: {
        styleOverrides: {
          root: compact ? { padding: '6px 12px' } : {},
          head: { fontWeight: 700, whiteSpace: 'nowrap' },
        },
      },
      MuiCardContent: {
        styleOverrides: { root: compact ? { padding: 14 } : {} },
      },
      MuiListItemButton: {
        styleOverrides: { root: { borderRadius: 8 } },
      },
      MuiButton: {
        styleOverrides: { root: { textTransform: 'none', fontWeight: 600 } },
      },
      MuiChip: { styleOverrides: { root: { fontWeight: 600 } } },
      MuiCheckbox: {
        defaultProps: { disableRipple: true },
        styleOverrides: {
          root: {
            '&:hover': { backgroundColor: 'transparent' },
            '& .MuiTouchRipple-root': { display: 'none' },
          },
        },
      },
    },
  });
}
