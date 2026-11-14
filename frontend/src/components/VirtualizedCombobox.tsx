import {
  Children,
  Fragment,
  cloneElement,
  createContext,
  forwardRef,
  isValidElement,
  useContext,
  useMemo,
  type HTMLAttributes,
  type ReactElement,
  type ReactNode,
} from 'react';
import {
  Autocomplete,
  Box,
  MenuItem,
  TextField,
  Typography,
  type SxProps,
  type Theme,
} from '@mui/material';
import CheckIcon from '@mui/icons-material/Check';
import { FixedSizeList, type ListChildComponentProps } from 'react-window';

const LISTBOX_PADDING = 8;
const ROW_HEIGHT = 72;
const DEFAULT_VIRTUALIZE_THRESHOLD = 60;
const DEFAULT_VISIBLE_ROWS = 8;

/** One selectable option with an optional secondary description. */
export interface ComboboxOption<T = string> {
  value: T;
  label: string;
  description?: string;
  disabled?: boolean;
}

/** Event-shaped change payload kept for compatibility with MUI select migrations. */
export interface ComboboxChangeEvent<T> {
  target: { value: T };
}

interface BaseProps<T> {
  label: string;
  options?: readonly ComboboxOption<T>[];
  children?: ReactNode;
  size?: 'small' | 'medium';
  fullWidth?: boolean;
  required?: boolean;
  disabled?: boolean;
  readOnly?: boolean;
  error?: boolean;
  helperText?: ReactNode;
  placeholder?: string;
  noOptionsText?: ReactNode;
  loading?: boolean;
  loadingText?: ReactNode;
  clearable?: boolean;
  virtualizedThreshold?: number;
  sx?: SxProps<Theme>;
  id?: string;
  name?: string;
  onBlur?: () => void;
  renderOptionContent?: (option: ComboboxOption<T>, selected: boolean) => ReactNode;
}

export interface SingleComboboxProps<T = string> extends BaseProps<T> {
  multiple?: false;
  value?: T | null;
  defaultValue?: T;
  onChange?: (event: ComboboxChangeEvent<T>, value: T | null) => void;
  onValueChange?: (value: T | null) => void;
}

export interface MultipleComboboxProps<T = string> extends BaseProps<T> {
  multiple: true;
  value?: T[];
  onChange?: (event: ComboboxChangeEvent<T[]>, value: T[]) => void;
  onValueChange?: (value: T[]) => void;
  limitTags?: number;
  disableCloseOnSelect?: boolean;
}

type Props<T> = SingleComboboxProps<T> | MultipleComboboxProps<T>;

const OuterElementContext = createContext<Record<string, unknown>>({});

const OuterElementType = forwardRef<HTMLDivElement, HTMLAttributes<HTMLDivElement>>(
  function OuterElementType(props, ref) {
    const outerProps = useContext(OuterElementContext);
    return <div ref={ref} {...props} {...outerProps} />;
  },
);

function VirtualRow({ data, index, style }: ListChildComponentProps<ReactElement[]>) {
  const child = data[index];
  return cloneElement(child, {
    style: {
      ...(child.props as { style?: React.CSSProperties }).style,
      ...style,
      top: Number(style.top ?? 0) + LISTBOX_PADDING,
    },
  });
}

const VirtualizedListbox = forwardRef<HTMLDivElement, HTMLAttributes<HTMLElement>>(
  function VirtualizedListbox(props, ref) {
    const { children, ...other } = props;
    const rows = Children.toArray(children).filter(isValidElement) as ReactElement[];
    const rowCount = rows.length;
    const height = Math.min(rowCount, DEFAULT_VISIBLE_ROWS) * ROW_HEIGHT + LISTBOX_PADDING * 2;

    return (
      <div ref={ref}>
        <OuterElementContext.Provider value={other}>
          <FixedSizeList
            height={height}
            width="100%"
            itemSize={ROW_HEIGHT}
            itemCount={rowCount}
            itemData={rows}
            overscanCount={5}
            outerElementType={OuterElementType}
            innerElementType="ul"
          >
            {VirtualRow}
          </FixedSizeList>
        </OuterElementContext.Provider>
      </div>
    );
  },
);

function textFromNode(node: ReactNode): string {
  if (typeof node === 'string' || typeof node === 'number') return String(node);
  if (Array.isArray(node)) return node.map(textFromNode).join('');
  if (isValidElement<{ children?: ReactNode }>(node)) return textFromNode(node.props.children);
  return '';
}

function optionsFromChildren<T>(children: ReactNode): ComboboxOption<T>[] {
  const options: ComboboxOption<T>[] = [];

  const visit = (node: ReactNode) => {
    Children.forEach(node, (child) => {
      if (!isValidElement(child)) return;
      if (child.type === Fragment) {
        visit((child.props as { children?: ReactNode }).children);
        return;
      }
      const isMenuItem = child.type === MenuItem;
      const isNativeOption = child.type === 'option';
      if (!isMenuItem && !isNativeOption) return;
      const item = child as ReactElement<{
        value?: T;
        disabled?: boolean;
        children?: ReactNode;
      }>;
      if (item.props.value === undefined) return;
      const label = textFromNode(item.props.children).trim();
      options.push({
        value: item.props.value,
        label: label || String(item.props.value),
        disabled: item.props.disabled,
      });
    });
  };

  visit(children);
  return options;
}

function defaultFilter<T>(options: readonly ComboboxOption<T>[], inputValue: string) {
  const term = inputValue.trim().toLowerCase();
  if (!term) return [...options];
  return options.filter((option) =>
    `${option.label} ${option.description ?? ''}`.toLowerCase().includes(term),
  );
}

/**
 * Global select/combobox used by every feature form and filter.
 *
 * Small option sets use the normal MUI listbox; large sets switch to a
 * react-window virtual list. Pass either `options` or MenuItem/option children.
 */
export function VirtualizedCombobox<T = string>(props: SingleComboboxProps<T>): ReactElement;
export function VirtualizedCombobox<T = string>(props: MultipleComboboxProps<T>): ReactElement;
export function VirtualizedCombobox<T = string>(props: Props<T>) {
  const {
    label,
    options: providedOptions,
    children,
    multiple = false,
    size = 'medium',
    fullWidth = false,
    required = false,
    disabled = false,
    readOnly = false,
    error = false,
    helperText,
    placeholder,
    noOptionsText = 'No matching options',
    loading = false,
    loadingText,
    clearable = false,
    virtualizedThreshold = DEFAULT_VIRTUALIZE_THRESHOLD,
    sx,
    id,
    name,
    onBlur,
    renderOptionContent,
  } = props;

  const options = useMemo<readonly ComboboxOption<T>[]>(
    () => providedOptions ?? optionsFromChildren<T>(children),
    [providedOptions, children],
  );

  const findOption = (value: T | null | undefined) =>
    value == null ? null : (options.find((option) => option.value === value) ?? null);

  const selected = multiple
    ? ((props.value ?? []) as T[])
        .map((value) => findOption(value))
        .filter((option): option is ComboboxOption<T> => option !== null)
    : findOption(props.value as T | null | undefined);

  const defaultValue = multiple
    ? undefined
    : findOption((props as SingleComboboxProps<T>).defaultValue);

  const isSelected = (option: ComboboxOption<T>) =>
    multiple
      ? ((props.value ?? []) as T[]).includes(option.value)
      : (props.value as T | null | undefined) === option.value;

  const handleChange = (
    _event: React.SyntheticEvent,
    value: ComboboxOption<T> | ComboboxOption<T>[] | null,
  ) => {
    if (multiple) {
      const next = Array.isArray(value) ? value.map((option) => option.value) : [];
      (props as MultipleComboboxProps<T>).onChange?.({ target: { value: next } }, next);
      (props as MultipleComboboxProps<T>).onValueChange?.(next);
      return;
    }
    const next = Array.isArray(value) ? null : (value?.value ?? null);
    (props as SingleComboboxProps<T>).onChange?.({ target: { value: (next ?? '') as T } }, next);
    (props as SingleComboboxProps<T>).onValueChange?.(next);
  };

  return (
    <Autocomplete
      multiple={multiple}
      options={[...options]}
      value={selected as ComboboxOption<T> | ComboboxOption<T>[] | null}
      defaultValue={defaultValue ?? undefined}
      onChange={handleChange}
      getOptionLabel={(option) => option.label}
      isOptionEqualToValue={(option, current) => option.value === current.value}
      getOptionDisabled={(option) => Boolean(option.disabled)}
      filterOptions={(items, state) => defaultFilter(items, state.inputValue)}
      disableClearable={!clearable}
      disableListWrap
      openOnFocus
      autoHighlight
      handleHomeEndKeys
      disabled={disabled}
      readOnly={readOnly}
      loading={loading}
      loadingText={loadingText}
      noOptionsText={noOptionsText}
      size={size}
      fullWidth={fullWidth}
      id={id}
      onBlur={onBlur}
      limitTags={(props as MultipleComboboxProps<T>).limitTags}
      disableCloseOnSelect={(props as MultipleComboboxProps<T>).disableCloseOnSelect}
      slotProps={{
        popper: {
          sx: { zIndex: (theme) => theme.zIndex.modal + 1 },
        },
        paper: {
          sx: {
            borderRadius: 0,
            overflow: 'hidden',
            boxShadow: '0 12px 30px rgba(15, 23, 42, 0.16)',
          },
        },
        listbox:
          options.length > virtualizedThreshold ? { component: VirtualizedListbox } : undefined,
      }}
      renderOption={(optionProps, option) => {
        const { key, ...rest } = optionProps as HTMLAttributes<HTMLLIElement> & { key: string };
        const selectedOption = isSelected(option);
        return (
          <Box
            component="li"
            key={key}
            {...rest}
            sx={{
              minHeight: `${ROW_HEIGHT}px`,
              display: 'flex !important',
              alignItems: 'center',
              justifyContent: 'space-between',
              gap: 1.5,
              py: '8px !important',
              '&.Mui-focused': { backgroundColor: 'action.hover' },
            }}
          >
            <Box sx={{ minWidth: 0 }}>
              {renderOptionContent ? (
                renderOptionContent(option, selectedOption)
              ) : (
                <>
                  <Typography
                    variant="body2"
                    title={option.label}
                    sx={{ whiteSpace: 'normal', wordBreak: 'break-word' }}
                  >
                    {option.label}
                  </Typography>
                  {option.description && (
                    <Typography
                      variant="caption"
                      color="text.secondary"
                      display="block"
                      title={option.description}
                      sx={{ whiteSpace: 'normal', wordBreak: 'break-word' }}
                    >
                      {option.description}
                    </Typography>
                  )}
                </>
              )}
            </Box>
            {selectedOption && <CheckIcon fontSize="small" color="primary" />}
          </Box>
        );
      }}
      renderInput={(params) => (
        <TextField
          {...params}
          label={label}
          name={name}
          size={size}
          fullWidth
          required={required}
          error={error}
          helperText={helperText}
          placeholder={placeholder}
          sx={[
            { '& .MuiOutlinedInput-root': { borderRadius: 0 } },
            ...(Array.isArray(sx) ? sx : sx ? [sx] : []),
          ]}
        />
      )}
    />
  );
}
