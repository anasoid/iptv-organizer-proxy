import { useState, useMemo } from 'react';
import {
  Box,
  Paper,
  Typography,
  Button,
  Chip,
  CircularProgress,
  Divider,
  Tooltip,
  IconButton,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  FormControl,
  Select,
  MenuItem,
  Pagination,
  ToggleButton,
  ToggleButtonGroup,
} from '@mui/material';
import { Delete as DeleteIcon, Refresh as RefreshIcon } from '@mui/icons-material';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  BarChart,
  Bar,
  Brush,
  Cell,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip as ChartTooltip,
  Legend,
  ResponsiveContainer,
} from 'recharts';
import clientHistoryApi, { type StreamHistoryEntry } from '../services/clientHistoryApi';
import { useAuthStore } from '../stores/authStore';

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

type Granularity = 'day' | 'week' | 'month';

interface ChartBucket {
  /** Label shown on the X-axis */
  label: string;
  /** Actual stream counts — used in the tooltip */
  LIVE: number;
  VOD: number;
  SERIES: number;
  total: number;
  /** Normalised fractions (sum ≤ 1) — used by the Bar components so every
   *  non-empty bar reaches exactly height 1 regardless of absolute count. */
  LIVE_pct: number;
  VOD_pct: number;
  SERIES_pct: number;
  /** Full entry list for the custom tooltip */
  entries: StreamHistoryEntry[];
}

interface ColumnFilters {
  streamName: string;
  categoryName: string;
  streamId: string;
  streamType: string;
  dateFrom: string;
  dateTo: string;
}

const EMPTY_FILTERS: ColumnFilters = {
  streamName: '',
  categoryName: '',
  streamId: '',
  streamType: '',
  dateFrom: '',
  dateTo: '',
};

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

const TYPE_COLOR: Record<string, string> = {
  LIVE: '#1976d2',
  VOD: '#9c27b0',
  SERIES: '#0288d1',
};

/** Available bucket-size options per granularity (in minutes). */
const BUCKET_OPTIONS: Record<Granularity, { value: number; label: string }[]> = {
  day:   [{ value: 5, label: '5 min' }, { value: 15, label: '15 min' }, { value: 30, label: '30 min' }, { value: 60, label: '1 h' }],
  week:  [{ value: 60, label: '1 h' },  { value: 240, label: '4 h' },  { value: 720, label: '12 h' },  { value: 1440, label: '1 day' }],
  month: [{ value: 60, label: '1 h' },  { value: 240, label: '4 h' },  { value: 720, label: '12 h' },  { value: 1440, label: '1 day' }],
};

const DEFAULT_BUCKET: Record<Granularity, number> = { day: 5, week: 60, month: 60 };

/** Stable empty array so `?? []` doesn't produce a new reference on every render */
const EMPTY_ENTRIES: StreamHistoryEntry[] = [];

// ---------------------------------------------------------------------------
// Chart helpers  (only the label renderer changes)
// ---------------------------------------------------------------------------

/**
 * Returns a local "YYYY-MM-DD" string — avoids UTC-shift that
 * `toISOString().slice(0,10)` would introduce.
 */
function localDateStr(date: Date): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

/**
 * Fills the `total` and `*_pct` fields on every bucket so that bars with at
 * least one stream always reach height 1, with internal proportions reflecting
 * the actual type breakdown.
 */
function normalizeBuckets(buckets: ChartBucket[]): void {
  for (const b of buckets) {
    b.total = b.LIVE + b.VOD + b.SERIES;
    if (b.total > 0) {
      b.LIVE_pct = b.LIVE / b.total;
      b.VOD_pct  = b.VOD  / b.total;
      b.SERIES_pct = b.SERIES / b.total;
    }
  }
}

/**
 * Concise X-axis label for a bucket slot.
 * - day view      → "HH:MM"
 * - sub-day multi → "Mon HH:MM"
 * - full-day      → "Mar 11"
 */
function formatBucketLabel(slotStart: Date, bucketMinutes: number, granularity: Granularity): string {
  if (granularity === 'day') {
    const h = String(slotStart.getHours()).padStart(2, '0');
    const m = String(slotStart.getMinutes()).padStart(2, '0');
    return `${h}:${m}`;
  }
  if (bucketMinutes >= 1440) {
    return slotStart.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
  }
  const h  = String(slotStart.getHours()).padStart(2, '0');
  const mi = String(slotStart.getMinutes()).padStart(2, '0');
  const timeStr = bucketMinutes < 60 ? `${h}:${mi}` : `${h}:00`;
  if (granularity === 'month') {
    // Show day-of-month so adjacent days are distinguishable ("17 08:00")
    const d = String(slotStart.getDate()).padStart(2, '0');
    return `${d} ${timeStr}`;
  }
  // Week: show short weekday name ("Mon 08:00")
  const wd = slotStart.toLocaleDateString('en-US', { weekday: 'short' });
  return `${wd} ${timeStr}`;
}

/**
 * Calculates a recharts XAxis `interval` value so the axis never shows more
 * than ~12 labels regardless of the total number of buckets.
 */
function computeXAxisInterval(totalBuckets: number): number {
  if (totalBuckets <= 12)  return 0;
  if (totalBuckets <= 24)  return 1;
  if (totalBuckets <= 48)  return 3;
  if (totalBuckets <= 96)  return 7;
  if (totalBuckets <= 168) return 11;
  if (totalBuckets <= 288) return 23;
  return Math.floor(totalBuckets / 12);
}

/**
 * Builds an array of ChartBuckets covering the full window with all slots
 * pre-filled to 0.
 *
 * - day   → today 00:00 – 23:59, sliced into `bucketMinutes`-wide slots
 * - week  → last 7 days  00:00 – today 23:59
 * - month → last 30 days 00:00 – today 23:59
 *
 * Entries outside the window are silently ignored.
 */
function buildChartData(
  entries: StreamHistoryEntry[],
  granularity: Granularity,
  bucketMinutes: number,
): ChartBucket[] {
  const now = new Date();

  // Window start (local midnight)
  let windowStart: Date;
  if (granularity === 'day') {
    windowStart = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 0, 0, 0, 0);
  } else {
    const daysBack = granularity === 'week' ? 6 : 29;
    const d = new Date(now);
    d.setDate(d.getDate() - daysBack);
    windowStart = new Date(d.getFullYear(), d.getMonth(), d.getDate(), 0, 0, 0, 0);
  }
  // Window end = today 23:59:59
  const windowEnd = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 23, 59, 59, 999);

  const windowStartMs = windowStart.getTime();
  const bucketMs      = bucketMinutes * 60 * 1000;
  const totalBuckets  = Math.ceil((windowEnd.getTime() - windowStartMs) / bucketMs);

  const buckets: ChartBucket[] = Array.from({ length: totalBuckets }, (_, i) => ({
    label: formatBucketLabel(new Date(windowStartMs + i * bucketMs), bucketMinutes, granularity),
    LIVE: 0, VOD: 0, SERIES: 0, total: 0,
    LIVE_pct: 0, VOD_pct: 0, SERIES_pct: 0,
    entries: [],
  }));

  for (const entry of entries) {
    const entryMs = new Date(entry.start).getTime();
    if (entryMs < windowStartMs || entryMs > windowEnd.getTime()) continue;
    const idx = Math.floor((entryMs - windowStartMs) / bucketMs);
    if (idx >= 0 && idx < buckets.length) {
      buckets[idx][entry.streamType] += 1;
      buckets[idx].entries.push(entry);
    }
  }

  normalizeBuckets(buckets);
  return buckets;
}

/**
 * Returns a recharts `label` content-renderer (closure over `chartData`).
 *
 * Each bar gets:
 *  - its total stream count shown above in small grey text
 *  - the unique stream names shown as rotated text inside the bar,
 *    but only when bars are wide enough (≤ 72 total buckets, width ≥ 12 px)
 */
function makeBarLabelRenderer(chartData: ChartBucket[]) {
  return function BarLabel(props: {
    x?: number; y?: number; width?: number; index?: number;
  }) {
    const { x = 0, y = 0, width = 0, index } = props;
    if (index === undefined) return null;
    const bucket = chartData[index];
    // Show count for every non-empty bar
    if (!bucket || bucket.total === 0) return null;

    const cx     = x + width / 2;
    const barTop = y;

    // Count INSIDE the bar, near the top
    const countLabel = (
      <text
        key="count"
        x={cx}
        y={barTop + 12}
        textAnchor="middle"
        fontSize={9}
        fontWeight="bold"
        fill="rgba(255,255,255,0.9)"
      >
        {bucket.total}
      </text>
    );

    // Stream names as rotated text (only when bars are wide enough)
    const showNames = width >= 12 && chartData.length <= 72;
    if (!showNames) return <g>{countLabel}</g>;

    const names = [...new Set(bucket.entries.map((e) => e.streamName ?? 'unknown'))].slice(0, 3);
    const n     = names.length;

    return (
      <g>
        {countLabel}
        {names.map((name, i) => {
          const truncated = name.length > 18 ? name.slice(0, 17) + '…' : name;
          const xOff = cx + (i - (n - 1) / 2) * Math.min(10, (width - 2) / Math.max(n, 1));
          return (
            <text
              key={`n${i}`}
              x={xOff}
              y={barTop + 22}
              transform={`rotate(90, ${xOff}, ${barTop + 22})`}
              textAnchor="start"
              fontSize={8}
              fill="rgba(255,255,255,0.9)"
            >
              {truncated}
            </text>
          );
        })}
      </g>
    );
  };
}

// ---------------------------------------------------------------------------
// Custom recharts tooltip
// ---------------------------------------------------------------------------

interface TooltipPayload {
  payload: ChartBucket;
}

function CustomTooltip({
  active,
  payload,
}: {
  active?: boolean;
  payload?: TooltipPayload[];
}) {
  if (!active || !payload?.length) return null;

  const bucket = payload[0].payload;
  const total = bucket.total;
  if (total === 0) return null;

  // Build a compact type-count summary, e.g. "3 Live · 1 VOD"
  const typeSummary = (
    [
      bucket.LIVE > 0 && `${bucket.LIVE} Live`,
      bucket.VOD > 0 && `${bucket.VOD} VOD`,
      bucket.SERIES > 0 && `${bucket.SERIES} Series`,
    ] as (string | false)[]
  )
    .filter(Boolean)
    .join(' · ');

  return (
    <Paper
      elevation={4}
      sx={{ p: 1.5, maxWidth: 320, maxHeight: 260, overflow: 'auto', pointerEvents: 'none' }}
    >
      <Typography variant="subtitle2" fontWeight="bold">
        {bucket.label}
      </Typography>
      <Typography variant="caption" color="text.secondary" display="block" gutterBottom>
        {total} stream{total !== 1 ? 's' : ''} — {typeSummary}
      </Typography>
      <Divider sx={{ mb: 1 }} />
      <Stack spacing={0.5}>
        {bucket.entries.slice(0, 15).map((entry, i) => (
          <Box key={i} sx={{ display: 'flex', alignItems: 'flex-start', gap: 0.75 }}>
            {/* colour dot for stream type */}
            <Box
              sx={{
                mt: '3px',
                width: 8,
                height: 8,
                borderRadius: '50%',
                flexShrink: 0,
                backgroundColor: TYPE_COLOR[entry.streamType] ?? '#777',
              }}
            />
            <Typography variant="caption" sx={{ lineHeight: 1.4 }}>
              {entry.streamName ?? <em>unknown</em>}
              {entry.categoryName && (
                <Typography
                  component="span"
                  variant="caption"
                  color="text.secondary"
                >
                  {' '}({entry.categoryName})
                </Typography>
              )}
            </Typography>
          </Box>
        ))}
        {bucket.entries.length > 15 && (
          <Typography variant="caption" color="text.secondary">
            +{bucket.entries.length - 15} more…
          </Typography>
        )}
      </Stack>
    </Paper>
  );
}

// ---------------------------------------------------------------------------
// Filter helpers
// ---------------------------------------------------------------------------

function applyFilters(
  entries: StreamHistoryEntry[],
  filters: ColumnFilters,
): StreamHistoryEntry[] {
  const lsn = filters.streamName.toLowerCase();
  const lcat = filters.categoryName.toLowerCase();
  const lid = filters.streamId.toLowerCase();
  const dateFrom = filters.dateFrom ? new Date(filters.dateFrom).getTime() : null;
  const dateTo = filters.dateTo ? new Date(filters.dateTo + 'T23:59:59').getTime() : null;

  return entries.filter((e) => {
    if (lsn && !(e.streamName?.toLowerCase().includes(lsn) ?? false)) return false;
    if (lcat && !(e.categoryName?.toLowerCase().includes(lcat) ?? false)) return false;
    if (lid && !e.streamId.toLowerCase().includes(lid)) return false;
    if (filters.streamType && e.streamType !== filters.streamType) return false;
    const startTime = new Date(e.start).getTime();
    if (dateFrom && startTime < dateFrom) return false;
    if (dateTo && startTime > dateTo) return false;
    return true;
  });
}

function hasActiveFilters(filters: ColumnFilters): boolean {
  return Object.values(filters).some((v) => v !== '');
}

// ---------------------------------------------------------------------------
// Duration formatter
// ---------------------------------------------------------------------------

function formatDuration(start: string, end: string | null): string {
  if (!end) return '—';
  const ms = new Date(end).getTime() - new Date(start).getTime();
  if (ms < 0) return '—';
  const totalSec = Math.floor(ms / 1000);
  const h = Math.floor(totalSec / 3600);
  const m = Math.floor((totalSec % 3600) / 60);
  const s = totalSec % 60;
  if (h > 0) return `${h}h ${m}m ${s}s`;
  if (m > 0) return `${m}m ${s}s`;
  return `${s}s`;
}

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

interface Props {
  clientId: number;
}

export default function ClientHistoryTab({ clientId }: Props) {
  const { isAuthenticated } = useAuthStore();
  const queryClient = useQueryClient();

  const [granularity, setGranularity] = useState<Granularity>('day');
  // Per-granularity bucket duration (minutes); resets to default when granularity changes
  const [bucketMinutes, setBucketMinutes] = useState<Record<Granularity, number>>(DEFAULT_BUCKET);
  const [filters, setFilters] = useState<ColumnFilters>(EMPTY_FILTERS);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  // Chart selection state
  const [selectedBuckets, setSelectedBuckets] = useState<Set<number>>(new Set());
  const [brushIndices, setBrushIndices] = useState<{ startIndex: number; endIndex: number } | null>(null);

  const { data: historyResponse, isLoading, refetch } = useQuery({
    queryKey: ['client-history', clientId],
    queryFn: () => clientHistoryApi.getHistory(clientId),
    enabled: isAuthenticated && !!clientId,
    staleTime: 0,
  });

  const clearMutation = useMutation({
    mutationFn: () => clientHistoryApi.clearHistory(clientId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['client-history', clientId] });
      setSelectedBuckets(new Set());
      setBrushIndices(null);
    },
  });

  // Stable references — prevent spurious recharts Redux resets on every render
  const entries = historyResponse?.data?.data ?? EMPTY_ENTRIES;
  const currentBucket = bucketMinutes[granularity];
  const chartData = useMemo(
    () => buildChartData(entries, granularity, currentBucket),
    [entries, granularity, currentBucket],
  );
  const xAxisInterval = computeXAxisInterval(chartData.length);
  const barLabel     = makeBarLabelRenderer(chartData);

  // ── Selection helpers ──────────────────────────────────────────────────
  const hasSelection = brushIndices !== null || selectedBuckets.size > 0;
  const isActive = (i: number): boolean => {
    if (brushIndices) return i >= brushIndices.startIndex && i <= brushIndices.endIndex;
    if (selectedBuckets.size > 0) return selectedBuckets.has(i);
    return true;
  };
  const activeEntries = hasSelection
    ? chartData.filter((_, i) => isActive(i)).flatMap((b) => b.entries)
    : entries;

  const selectionLabel: string | null = (() => {
    if (brushIndices) {
      const s = chartData[brushIndices.startIndex]?.label ?? '';
      const e = chartData[brushIndices.endIndex]?.label ?? '';
      const count = brushIndices.endIndex - brushIndices.startIndex + 1;
      return `${count} slot${count !== 1 ? 's' : ''}: ${s} → ${e}`;
    }
    if (selectedBuckets.size > 0) {
      const idx = [...selectedBuckets][0];
      return `Slot: ${chartData[idx]?.label ?? ''}`;
    }
    return null;
  })();

  // ── Bar click → select / toggle that single bucket ────────────────────
  const handleBarClick = (_data: ChartBucket, index: number) => {
    setBrushIndices(null);
    setSelectedBuckets((prev) => {
      if (prev.has(index)) return new Set(); // toggle off
      return new Set<number>([index]);
    });
    setPage(1);
  };

  // ── Brush range → filter table to selected interval ───────────────────
  const handleBrushChange = (range: { startIndex?: number; endIndex?: number }) => {
    if (range.startIndex === undefined || range.endIndex === undefined || chartData.length === 0) return;
    const isFullRange = range.startIndex === 0 && range.endIndex === chartData.length - 1;
    if (isFullRange) {
      setBrushIndices(null);
    } else {
      setSelectedBuckets(new Set());
      setBrushIndices({ startIndex: range.startIndex, endIndex: range.endIndex });
      setPage(1);
    }
  };

  const clearSelection = () => {
    setSelectedBuckets(new Set());
    setBrushIndices(null);
  };

  const filtered   = applyFilters(activeEntries, filters);
  const totalPages = pageSize === 0 ? 1 : Math.ceil(filtered.length / pageSize);
  const displayed  =
    pageSize === 0
      ? filtered
      : filtered.slice((page - 1) * pageSize, page * pageSize);

  const setFilter = (key: keyof ColumnFilters, value: string) => {
    setFilters((f) => ({ ...f, [key]: value }));
    setPage(1);
  };

  if (isLoading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', p: 4 }}>
        <CircularProgress />
      </Box>
    );
  }

  // ── Render ────────────────────────────────────────────────────────────────
  return (
    <Box>
      {/* ── Header ── */}
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <Typography variant="h6">Watch History</Typography>
          <Chip
            label={`${entries.length} entries`}
            size="small"
            variant="outlined"
            color={entries.length > 0 ? 'primary' : 'default'}
          />
        </Box>
        <Stack direction="row" spacing={1}>
          <Tooltip title="Refresh">
            <IconButton size="small" onClick={() => refetch()} disabled={isLoading}>
              <RefreshIcon fontSize="small" />
            </IconButton>
          </Tooltip>
          <Button
            size="small"
            color="error"
            startIcon={<DeleteIcon />}
            onClick={() => clearMutation.mutate()}
            disabled={clearMutation.isPending || entries.length === 0}
          >
            Clear
          </Button>
        </Stack>
      </Box>

      {/* ── Activity chart ── */}
      <Paper sx={{ p: 2, mb: 3 }}>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
          <Box>
            <Typography variant="subtitle1" fontWeight="medium">
              Activity over time
            </Typography>
            <Typography variant="caption" color="text.secondary">
              {granularity === 'day'   && `Today — ${currentBucket < 60 ? `${currentBucket} min` : '1 h'} buckets`}
              {granularity === 'week'  && `Last 7 days — ${currentBucket < 1440 ? `${currentBucket} min` : '1 day'} buckets`}
              {granularity === 'month' && `Last 30 days — ${currentBucket < 1440 ? `${currentBucket} min` : '1 day'} buckets`}
              {' '}({chartData.length} bars · hover to see streams)
            </Typography>
          </Box>
          {/* Granularity toggle + bucket-size selector */}
          <Stack direction="row" spacing={1} alignItems="center">
            <Select
              size="small"
              value={currentBucket}
              onChange={(e) => {
                setBucketMinutes((prev) => ({ ...prev, [granularity]: e.target.value as number }));
                setSelectedBuckets(new Set());
                setBrushIndices(null);
              }}
              sx={{ fontSize: 12, minWidth: 80 }}
            >
              {BUCKET_OPTIONS[granularity].map((opt) => (
                <MenuItem key={opt.value} value={opt.value} sx={{ fontSize: 13 }}>
                  {opt.label}
                </MenuItem>
              ))}
            </Select>
            <ToggleButtonGroup
              value={granularity}
              exclusive
              onChange={(_, v) => {
                if (v) {
                  setGranularity(v as Granularity);
                  setSelectedBuckets(new Set());
                  setBrushIndices(null);
                }
              }}
              size="small"
            >
              <ToggleButton value="day">Day</ToggleButton>
              <ToggleButton value="week">Week</ToggleButton>
              <ToggleButton value="month">Month</ToggleButton>
            </ToggleButtonGroup>
          </Stack>
        </Box>

        <ResponsiveContainer width="100%" height={340}>
          <BarChart data={chartData} margin={{ top: 20, right: 90, left: 80, bottom: 5 }}>
            <CartesianGrid strokeDasharray="3 3" vertical={true} horizontal={true} />
            <XAxis dataKey="label" interval={xAxisInterval} tick={{ fontSize: 11 }} />
            {/* Y-axis is fixed 0→1; actual counts are shown above each bar and in the tooltip */}
            <YAxis domain={[0, 1]} tick={false} width={10} />
            <ChartTooltip content={<CustomTooltip />} cursor={{ fill: 'rgba(0,0,0,0.05)' }} />
            <Legend />
            <Bar
              dataKey="LIVE_pct"
              stackId="a"
              fill={TYPE_COLOR.LIVE}
              name="Live"
              style={{ cursor: 'pointer' }}
              onClick={handleBarClick}
            >
              {chartData.map((_, i) => (
                <Cell key={i} fill={TYPE_COLOR.LIVE} opacity={isActive(i) ? 1 : 0.3} />
              ))}
            </Bar>
            <Bar
              dataKey="VOD_pct"
              stackId="a"
              fill={TYPE_COLOR.VOD}
              name="VOD"
              style={{ cursor: 'pointer' }}
              onClick={handleBarClick}
            >
              {chartData.map((_, i) => (
                <Cell key={i} fill={TYPE_COLOR.VOD} opacity={isActive(i) ? 1 : 0.3} />
              ))}
            </Bar>
            {/* Top segment carries the custom label (count + names) */}
            <Bar
              dataKey="SERIES_pct"
              stackId="a"
              fill={TYPE_COLOR.SERIES}
              name="Series"
              radius={[2, 2, 0, 0]}
              label={{ content: barLabel }}
              style={{ cursor: 'pointer' }}
              onClick={handleBarClick}
            >
              {chartData.map((_, i) => (
                <Cell key={i} fill={TYPE_COLOR.SERIES} opacity={isActive(i) ? 1 : 0.3} />
              ))}
            </Bar>
            {/* Brush for interval selection — controlled: null brushIndices resets to full range.
                tickFormatter suppressed: range is already shown in the table header selectionLabel. */}
            <Brush
              dataKey="label"
              height={30}
              stroke="#8884d8"
              onChange={handleBrushChange}
              travellerWidth={8}
              startIndex={brushIndices?.startIndex ?? 0}
              endIndex={brushIndices?.endIndex ?? Math.max(0, chartData.length - 1)}
            />
          </BarChart>
        </ResponsiveContainer>
      </Paper>

      {/* ── Table with per-column filters ── */}
      <Paper sx={{ p: 2 }}>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
          <Box>
            <Typography variant="subtitle1" fontWeight="medium">
              Entries
              {(hasActiveFilters(filters) || hasSelection) && (
                <Typography component="span" variant="body2" color="text.secondary" sx={{ ml: 1 }}>
                  ({filtered.length} / {entries.length})
                </Typography>
              )}
            </Typography>
            {selectionLabel && (
              <Typography variant="caption" color="primary" display="block">
                📊 {selectionLabel}
                <Button
                  size="small"
                  variant="text"
                  sx={{ ml: 0.5, p: 0, minWidth: 'auto', fontSize: 11, textTransform: 'none' }}
                  onClick={clearSelection}
                >
                  (clear)
                </Button>
              </Typography>
            )}
          </Box>
          <Stack direction="row" spacing={1} alignItems="center">
            <FormControl size="small" sx={{ minWidth: 120 }}>
              <Select
                value={pageSize}
                onChange={(e) => { setPageSize(e.target.value as number); setPage(1); }}
              >
                <MenuItem value={20}>20 / page</MenuItem>
                <MenuItem value={50}>50 / page</MenuItem>
                <MenuItem value={100}>100 / page</MenuItem>
                <MenuItem value={0}>All</MenuItem>
              </Select>
            </FormControl>
            <Button
              size="small"
              variant="outlined"
              disabled={!hasActiveFilters(filters)}
              onClick={() => { setFilters(EMPTY_FILTERS); setPage(1); }}
            >
              Reset filters
            </Button>
          </Stack>
        </Box>

        <TableContainer>
          <Table size="small" stickyHeader>
            <TableHead>
              {/* Column labels */}
              <TableRow sx={{ backgroundColor: '#f5f5f5' }}>
                <TableCell>Stream Name</TableCell>
                <TableCell>Category</TableCell>
                <TableCell>Stream ID</TableCell>
                <TableCell>Type</TableCell>
                <TableCell>Start</TableCell>
                <TableCell>End</TableCell>
                <TableCell>Duration</TableCell>
              </TableRow>
              {/* Per-column filter inputs */}
              <TableRow>
                <TableCell sx={{ py: '4px', px: 1 }}>
                  <TextField
                    placeholder="Filter…"
                    size="small"
                    value={filters.streamName}
                    onChange={(e) => setFilter('streamName', e.target.value)}
                    fullWidth
                    inputProps={{ style: { fontSize: 12, padding: '4px 8px' } }}
                  />
                </TableCell>
                <TableCell sx={{ py: '4px', px: 1 }}>
                  <TextField
                    placeholder="Filter…"
                    size="small"
                    value={filters.categoryName}
                    onChange={(e) => setFilter('categoryName', e.target.value)}
                    fullWidth
                    inputProps={{ style: { fontSize: 12, padding: '4px 8px' } }}
                  />
                </TableCell>
                <TableCell sx={{ py: '4px', px: 1 }}>
                  <TextField
                    placeholder="Filter…"
                    size="small"
                    value={filters.streamId}
                    onChange={(e) => setFilter('streamId', e.target.value)}
                    fullWidth
                    inputProps={{ style: { fontSize: 12, padding: '4px 8px' } }}
                  />
                </TableCell>
                <TableCell sx={{ py: '4px', px: 1 }}>
                  <Select
                    size="small"
                    value={filters.streamType}
                    onChange={(e) => setFilter('streamType', e.target.value)}
                    displayEmpty
                    fullWidth
                    sx={{ fontSize: 12 }}
                  >
                    <MenuItem value="">All</MenuItem>
                    <MenuItem value="LIVE">Live</MenuItem>
                    <MenuItem value="VOD">VOD</MenuItem>
                    <MenuItem value="SERIES">Series</MenuItem>
                  </Select>
                </TableCell>
                <TableCell sx={{ py: '4px', px: 1 }}>
                  <Stack spacing={0.5}>
                    <TextField
                      type="date"
                      size="small"
                      value={filters.dateFrom}
                      onChange={(e) => setFilter('dateFrom', e.target.value)}
                      inputProps={{ style: { fontSize: 11, padding: '3px 6px' } }}
                    />
                    <TextField
                      type="date"
                      size="small"
                      value={filters.dateTo}
                      onChange={(e) => setFilter('dateTo', e.target.value)}
                      inputProps={{ style: { fontSize: 11, padding: '3px 6px' } }}
                    />
                  </Stack>
                </TableCell>
                <TableCell />{/* End — no filter */}
                <TableCell />{/* Duration — no filter */}
              </TableRow>
            </TableHead>

            <TableBody>
              {displayed.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={7} align="center" sx={{ color: 'text.secondary', py: 4 }}>
                    {entries.length === 0
                      ? 'No watch history recorded yet'
                      : 'No entries match the current filters'}
                  </TableCell>
                </TableRow>
              ) : (
                displayed.map((entry, idx) => (
                  <TableRow key={`${entry.streamId}-${entry.start}-${idx}`} hover>
                    <TableCell>
                      {entry.streamName ?? <em style={{ color: '#9e9e9e' }}>unknown</em>}
                    </TableCell>
                    <TableCell sx={{ color: entry.categoryName ? 'text.primary' : 'text.disabled', fontSize: '0.8rem' }}>
                      {entry.categoryName ?? '—'}
                    </TableCell>
                    <TableCell sx={{ fontFamily: 'monospace', fontSize: '0.8rem' }}>
                      {entry.streamId}
                    </TableCell>
                    <TableCell>
                      <Chip
                        label={entry.streamType}
                        size="small"
                        sx={{ backgroundColor: TYPE_COLOR[entry.streamType], color: '#fff', fontSize: 11 }}
                      />
                    </TableCell>
                    <TableCell sx={{ whiteSpace: 'nowrap', fontSize: '0.8rem' }}>
                      {new Date(entry.start).toLocaleString()}
                    </TableCell>
                    <TableCell sx={{ whiteSpace: 'nowrap', fontSize: '0.8rem' }}>
                      {entry.end
                        ? new Date(entry.end).toLocaleString()
                        : <Chip label="active" size="small" color="success" variant="outlined" />}
                    </TableCell>
                    <TableCell sx={{ fontSize: '0.8rem' }}>
                      {formatDuration(entry.start, entry.end)}
                    </TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </TableContainer>

        {pageSize !== 0 && totalPages > 1 && (
          <Box sx={{ display: 'flex', justifyContent: 'center', mt: 2 }}>
            <Pagination count={totalPages} page={page} onChange={(_, p) => setPage(p)} />
          </Box>
        )}
      </Paper>
    </Box>
  );
}
