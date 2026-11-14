import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative } from 'node:path';
import { describe, expect, it } from 'vitest';

const featuresRoot = join(process.cwd(), 'src/features');

function sourceFiles(dir: string): string[] {
  return readdirSync(dir).flatMap((entry) => {
    const path = join(dir, entry);
    return statSync(path).isDirectory() ? sourceFiles(path) : path.endsWith('.tsx') ? [path] : [];
  });
}

describe('combobox usage', () => {
  it('routes every select-like field through VirtualizedCombobox', () => {
    const violations = sourceFiles(featuresRoot).flatMap((file) => {
      const source = readFileSync(file, 'utf8');
      const problems: string[] = [];
      if (/<Autocomplete\b/.test(source)) problems.push('raw Autocomplete');
      if (/<Select\b/.test(source)) problems.push('raw Select');
      if (/SearchableSelect/.test(source)) problems.push('SearchableSelect');
      const textFieldTags = source.match(/<TextField\b[\s\S]*?>/g) ?? [];
      if (textFieldTags.some((tag) => /\bselect\b/.test(tag))) {
        problems.push('TextField select');
      }
      return problems.length ? [`${relative(process.cwd(), file)}: ${problems.join(', ')}`] : [];
    });

    expect(violations).toEqual([]);
  });
});
