import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MenuItem } from '@mui/material';
import { describe, expect, it, vi } from 'vitest';
import { VirtualizedCombobox, type ComboboxOption } from '@/components/VirtualizedCombobox';

const options: ComboboxOption[] = [
  { value: 'dept-ict', label: 'ICT' },
  { value: 'dept-fin', label: 'Finance', description: 'Financial operations' },
  { value: 'dept-ops', label: 'Operations', disabled: true },
];

describe('VirtualizedCombobox', () => {
  it('selects an option and reports an event-compatible value', async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();
    render(
      <VirtualizedCombobox label="Department" value={null} onChange={onChange} options={options} />,
    );

    await user.click(screen.getByRole('combobox', { name: 'Department' }));
    await user.click(screen.getByRole('option', { name: /Finance/ }));

    expect(onChange).toHaveBeenCalledWith({ target: { value: 'dept-fin' } }, 'dept-fin');
  });

  it('filters by label and description', async () => {
    const user = userEvent.setup();
    render(
      <VirtualizedCombobox label="Department" value={null} onChange={() => {}} options={options} />,
    );

    const input = screen.getByRole('combobox', { name: 'Department' });
    await user.click(input);
    await user.type(input, 'financial');

    expect(screen.getByRole('option', { name: /Finance/ })).toBeInTheDocument();
    expect(screen.queryByRole('option', { name: /ICT/ })).not.toBeInTheDocument();
  });

  it('supports MenuItem children for incremental migration', async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();
    render(
      <VirtualizedCombobox label="Status" value="" onChange={onChange}>
        <MenuItem value="">All statuses</MenuItem>
        <MenuItem value="ACTIVE">Active</MenuItem>
        <MenuItem value="SUSPENDED">Suspended</MenuItem>
      </VirtualizedCombobox>,
    );

    await user.click(screen.getByRole('combobox', { name: 'Status' }));
    await user.click(screen.getByRole('option', { name: 'Active' }));

    expect(onChange).toHaveBeenCalledWith({ target: { value: 'ACTIVE' } }, 'ACTIVE');
  });

  it('supports multi-select values', async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();
    render(
      <VirtualizedCombobox
        multiple
        label="Departments"
        value={['dept-ict']}
        onChange={onChange}
        options={options}
      />,
    );

    await user.click(screen.getByRole('combobox', { name: 'Departments' }));
    await user.click(screen.getByRole('option', { name: /Finance/ }));

    expect(onChange).toHaveBeenCalledWith({ target: { value: ['dept-ict', 'dept-fin'] } }, [
      'dept-ict',
      'dept-fin',
    ]);
  });

  it('virtualizes large option lists', async () => {
    const user = userEvent.setup();
    const manyOptions = Array.from({ length: 500 }, (_, index) => ({
      value: `option-${index}`,
      label: `Option ${index}`,
    }));
    render(
      <VirtualizedCombobox
        label="Large list"
        value={null}
        onChange={() => {}}
        options={manyOptions}
      />,
    );

    await user.click(screen.getByRole('combobox', { name: 'Large list' }));

    const renderedOptions = screen.getAllByRole('option');
    expect(renderedOptions.length).toBeGreaterThan(0);
    expect(renderedOptions.length).toBeLessThan(100);
  });

  it('clears the value when clearable', async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();
    render(
      <VirtualizedCombobox
        label="Department"
        value="dept-ict"
        onChange={onChange}
        options={options}
        clearable
      />,
    );

    await user.click(screen.getByRole('combobox', { name: 'Department' }));
    await user.click(screen.getByLabelText('Clear'));
    expect(onChange).toHaveBeenCalledWith({ target: { value: '' } }, null);
  });

  it('uses a full-width form control so it fills its container', () => {
    render(
      <VirtualizedCombobox label="Department" value={null} onChange={() => {}} options={options} />,
    );

    const input = screen.getByRole('combobox', { name: 'Department' });
    expect(input.closest('.MuiFormControl-root')).toHaveClass('MuiFormControl-fullWidth');
  });

  it('shows all options when opened with a selected value', async () => {
    const user = userEvent.setup();
    render(
      <VirtualizedCombobox
        label="Department"
        value="dept-ict"
        onChange={() => {}}
        options={options}
      />,
    );

    await user.click(screen.getByRole('combobox', { name: 'Department' }));
    expect(screen.getAllByRole('option')).toHaveLength(3);
  });

  it('shows validation helper text', () => {
    render(
      <VirtualizedCombobox
        label="Department"
        value={null}
        onChange={() => {}}
        options={options}
        required
        error
        helperText="Department is required"
      />,
    );

    expect(screen.getByText('Department is required')).toBeInTheDocument();
  });
});

describe('VirtualizedCombobox native option children', () => {
  it('parses option children', async () => {
    const user = userEvent.setup();
    render(
      <VirtualizedCombobox label="Signing method" value="HMAC_SHA256" onChange={() => {}}>
        <option value="HMAC_SHA256">HMAC-SHA256</option>
      </VirtualizedCombobox>,
    );
    await user.click(screen.getByRole('combobox', { name: 'Signing method' }));
    expect(screen.getByRole('option', { name: 'HMAC-SHA256' })).toBeInTheDocument();
  });
});
