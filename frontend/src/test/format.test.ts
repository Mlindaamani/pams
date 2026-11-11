import { describe, expect, it } from 'vitest';
import { money } from '@/lib/format';

describe('money', () => {
  it('formats amount and currency', () => {
    expect(money({ amount: '1234.5000', currency: 'TZS' })).toContain('1,234.50');
    expect(money({ amount: '1234.5000', currency: 'TZS' })).toContain('TZS');
  });

  it('handles missing values', () => {
    expect(money(undefined)).toBe('—');
  });
});
