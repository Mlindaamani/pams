import type { Money } from '@/api/types';

export function money(value?: Money | null): string {
  if (!value?.amount) return '—';
  return `${Number(value.amount).toLocaleString(undefined, { minimumFractionDigits: 2 })} ${value.currency ?? ''}`.trim();
}

export function dateTime(value?: string | null): string {
  if (!value) return '—';
  return new Date(value).toLocaleString();
}

export function dateOnly(value?: string | null): string {
  if (!value) return '—';
  return new Date(value).toLocaleDateString();
}

export function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}
