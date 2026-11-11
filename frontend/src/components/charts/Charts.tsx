import { useEffect, useRef, useState } from 'react';
import { Box, Stack, Typography, useTheme } from '@mui/material';

export interface ChartPoint {
  label: string;
  value: number;
}

const palette = ['#0b5cad', '#00897b', '#ef6c00', '#6a1b9a', '#c62828', '#546e7a', '#ad1457'];

function compact(value: number): string {
  return new Intl.NumberFormat(undefined, { notation: 'compact', maximumFractionDigits: 1 }).format(
    value,
  );
}

export function AreaChart({
  data,
  height = 220,
  ariaLabel,
}: {
  data: ChartPoint[];
  height?: number;
  ariaLabel: string;
}) {
  const theme = useTheme();
  const containerRef = useRef<HTMLDivElement>(null);
  const [width, setWidth] = useState(640);

  useEffect(() => {
    const element = containerRef.current;
    if (!element) return;
    const observer = new ResizeObserver((entries) => {
      const nextWidth = entries[0]?.contentRect.width;
      if (nextWidth && nextWidth > 0) setWidth(nextWidth);
    });
    observer.observe(element);
    return () => observer.disconnect();
  }, []);

  if (data.length === 0) {
    return <Typography color="text.secondary">No data for this period.</Typography>;
  }
  const padding = { left: 40, right: 8, top: 14, bottom: 28 };
  const innerWidth = width - padding.left - padding.right;
  const innerHeight = height - padding.top - padding.bottom;
  const max = Math.max(...data.map((point) => point.value), 1);
  const step = data.length > 1 ? innerWidth / (data.length - 1) : 0;
  const points = data.map((point, index) => ({
    x: padding.left + index * step,
    y: padding.top + innerHeight - (point.value / max) * innerHeight,
    ...point,
  }));
  const line = points
    .map((point, index) => `${index === 0 ? 'M' : 'L'}${point.x},${point.y}`)
    .join(' ');
  const area = `${line} L${points[points.length - 1].x},${padding.top + innerHeight} L${points[0].x},${padding.top + innerHeight} Z`;
  const grid = [0, 0.25, 0.5, 0.75, 1];

  return (
    <Box ref={containerRef} sx={{ width: '100%', minWidth: 0, overflow: 'hidden' }}>
      <svg
        viewBox={`0 0 ${width} ${height}`}
        width="100%"
        height={height}
        preserveAspectRatio="xMinYMin meet"
        role="img"
        aria-label={ariaLabel}
      >
        {grid.map((ratio) => {
          const y = padding.top + innerHeight * ratio;
          return (
            <g key={ratio}>
              <line
                x1={padding.left}
                x2={width - padding.right}
                y1={y}
                y2={y}
                stroke="#e0e4ea"
                strokeDasharray="4 4"
              />
              <text x={padding.left - 8} y={y + 4} textAnchor="end" fontSize="10" fill="#6b7280">
                {compact(max * (1 - ratio))}
              </text>
            </g>
          );
        })}
        <path d={area} fill={theme.palette.primary.main} opacity={0.16} />
        <path d={line} fill="none" stroke={theme.palette.primary.main} strokeWidth={2.5} />
        {points.map((point) => (
          <g key={point.label}>
            <circle cx={point.x} cy={point.y} r={4} fill={theme.palette.primary.main}>
              <title>
                {point.label}: {point.value.toLocaleString()}
              </title>
            </circle>
            {(data.length <= 12 || point === points[0] || point === points[points.length - 1]) && (
              <text x={point.x} y={height - 8} textAnchor="middle" fontSize="10" fill="#6b7280">
                {point.label}
              </text>
            )}
          </g>
        ))}
      </svg>
    </Box>
  );
}

export function DonutChart({
  data,
  ariaLabel,
  size = 200,
  valueFormatter = (value: number) => value.toLocaleString(),
}: {
  data: ChartPoint[];
  ariaLabel: string;
  size?: number;
  valueFormatter?: (value: number) => string;
}) {
  const total = data.reduce((sum, point) => sum + point.value, 0);
  if (total <= 0) {
    return <Typography color="text.secondary">No data for this period.</Typography>;
  }
  const radius = 70;
  const stroke = 26;
  let cursor = -90;
  const arcs = data.map((point, index) => {
    const fraction = point.value / total;
    const sweep = fraction * 360;
    const start = cursor;
    const end = cursor + sweep;
    cursor = end;
    return { ...point, start, end, fraction, color: palette[index % palette.length] };
  });

  return (
    <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} alignItems="center">
      <svg viewBox="0 0 200 200" width={size} height={size} role="img" aria-label={ariaLabel}>
        <circle cx="100" cy="100" r={radius} fill="none" stroke="#eef1f5" strokeWidth={stroke} />
        {arcs.map((arc) => {
          if (arc.fraction >= 0.999) {
            return (
              <circle
                key={arc.label}
                cx="100"
                cy="100"
                r={radius}
                fill="none"
                stroke={arc.color}
                strokeWidth={stroke}
              >
                <title>
                  {arc.label}: {valueFormatter(arc.value)} ({Math.round(arc.fraction * 100)}%)
                </title>
              </circle>
            );
          }
          const start = polar(100, 100, radius, arc.start);
          const end = polar(100, 100, radius, arc.end);
          const large = arc.end - arc.start > 180 ? 1 : 0;
          return (
            <path
              key={arc.label}
              d={`M ${start.x} ${start.y} A ${radius} ${radius} 0 ${large} 1 ${end.x} ${end.y}`}
              fill="none"
              stroke={arc.color}
              strokeWidth={stroke}
              strokeLinecap="butt"
            >
              <title>
                {arc.label}: {valueFormatter(arc.value)} ({Math.round(arc.fraction * 100)}%)
              </title>
            </path>
          );
        })}
        <text x="100" y="95" textAnchor="middle" fontSize="18" fontWeight="700" fill="#1f2937">
          {compact(total)}
        </text>
        <text x="100" y="114" textAnchor="middle" fontSize="10" fill="#6b7280">
          total
        </text>
      </svg>
      <Stack spacing={0.75} sx={{ minWidth: 160 }}>
        {arcs.map((arc) => (
          <Stack key={arc.label} direction="row" spacing={1} alignItems="center">
            <Box sx={{ width: 10, height: 10, borderRadius: '50%', bgcolor: arc.color }} />
            <Typography variant="body2" sx={{ flexGrow: 1 }}>
              {arc.label}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              {Math.round(arc.fraction * 100)}%
            </Typography>
          </Stack>
        ))}
      </Stack>
    </Stack>
  );
}

export function BarChart({
  data,
  ariaLabel,
  valueFormatter = (value: number) => value.toLocaleString(),
}: {
  data: ChartPoint[];
  ariaLabel: string;
  valueFormatter?: (value: number) => string;
}) {
  if (data.length === 0) {
    return <Typography color="text.secondary">No data for this period.</Typography>;
  }
  const max = Math.max(...data.map((point) => point.value), 1);
  return (
    <Stack spacing={1.25} role="img" aria-label={ariaLabel}>
      {data.map((point, index) => (
        <Stack key={point.label} spacing={0.5}>
          <Stack direction="row" justifyContent="space-between" spacing={2}>
            <Typography variant="body2" noWrap title={point.label}>
              {point.label}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              {valueFormatter(point.value)}
            </Typography>
          </Stack>
          <Box sx={{ bgcolor: 'grey.100', borderRadius: 1, height: 10, overflow: 'hidden' }}>
            <Box
              sx={{
                width: `${Math.max(2, (point.value / max) * 100)}%`,
                height: '100%',
                bgcolor: palette[index % palette.length],
                borderRadius: 1,
              }}
            />
          </Box>
        </Stack>
      ))}
    </Stack>
  );
}

function polar(cx: number, cy: number, radius: number, angle: number) {
  const radians = (angle * Math.PI) / 180;
  return { x: cx + radius * Math.cos(radians), y: cy + radius * Math.sin(radians) };
}
