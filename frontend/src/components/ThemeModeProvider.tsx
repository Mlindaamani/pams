import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type PropsWithChildren,
} from 'react';
import { CssBaseline, ThemeProvider } from '@mui/material';
import type { PaletteMode } from '@mui/material';
import { createAppTheme } from '@/theme';
import { useBranding } from '@/branding/BrandingProvider';

const STORAGE_KEY = 'pams.theme';

interface ThemeModeContextValue {
  mode: PaletteMode;
  toggle: () => void;
}

const ThemeModeContext = createContext<ThemeModeContextValue>({
  mode: 'light',
  toggle: () => undefined,
});

function initialMode(): PaletteMode {
  const saved = localStorage.getItem(STORAGE_KEY);
  if (saved === 'light' || saved === 'dark') return saved;
  return window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}

export function ThemeModeProvider({ children }: PropsWithChildren) {
  const [mode, setMode] = useState<PaletteMode>(initialMode);
  const { branding } = useBranding();

  useEffect(() => {
    localStorage.setItem(STORAGE_KEY, mode);
    document.documentElement.dataset.theme = mode;
  }, [mode]);

  const toggle = useCallback(() => {
    setMode((current) => (current === 'dark' ? 'light' : 'dark'));
  }, []);

  const value = useMemo(() => ({ mode, toggle }), [mode, toggle]);
  const theme = useMemo(
    () =>
      createAppTheme(mode, {
        primaryColor: branding.primaryColor,
        secondaryColor: branding.secondaryColor,
        cornerRadius: branding.cornerRadius,
        fontFamily: branding.fontFamily,
        uiDensity: branding.uiDensity,
      }),
    [
      mode,
      branding.primaryColor,
      branding.secondaryColor,
      branding.cornerRadius,
      branding.fontFamily,
      branding.uiDensity,
    ],
  );

  return (
    <ThemeModeContext.Provider value={value}>
      <ThemeProvider theme={theme}>
        <CssBaseline />
        {children}
      </ThemeProvider>
    </ThemeModeContext.Provider>
  );
}

export function useThemeMode() {
  return useContext(ThemeModeContext);
}
