import { describe, expect, it } from 'vitest';
import { createPamsThemeOptions } from '../../../shared/pamsTheme';

describe('shared PAMS theme', () => {
  it('uses the PAMS brand defaults', () => {
    const options = createPamsThemeOptions('light');
    expect(options.palette.mode).toBe('light');
    expect(options.palette.primary.main).toBe('#0B5CAD');
    expect(options.shape.borderRadius).toBe(10);
    expect(options.direction).toBe('ltr');
  });

  it('supports tenant branding and RTL', () => {
    const options = createPamsThemeOptions(
      'dark',
      { primaryColor: '#7A3E00', cornerRadius: 14 },
      'rtl',
    );
    expect(options.palette.mode).toBe('dark');
    expect(options.palette.primary.main).toBe('#7A3E00');
    expect(options.shape.borderRadius).toBe(14);
    expect(options.direction).toBe('rtl');
  });
});
