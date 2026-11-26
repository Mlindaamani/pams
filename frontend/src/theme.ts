import { createTheme } from '@mui/material/styles';
import type { PaletteMode } from '@mui/material';
import { createPamsThemeOptions, type PamsBrand } from '../../shared/pamsTheme';

export type { PamsBrand };

export function createAppTheme(mode: PaletteMode, brand?: PamsBrand) {
  return createTheme(createPamsThemeOptions(mode, brand));
}
