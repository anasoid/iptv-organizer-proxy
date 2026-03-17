import { useState } from 'react';
import {
  Box,
  Paper,
  Typography,
  Button,
  Chip,
  CircularProgress,
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
// Chart helpers
// ---------------------------------------------------------------------------

/** Returns the ISO week label "YYYY-Www" for a given date. */
function toISOWeekLabel(date: Date): string {
  const d = new Date(Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()));
  const dayNum = d.getUTCDay() || 7;
  d.setUTCDate(d.getUTCDate() + 4 - dayNum);
  const yearStart = new Date(Date.UTC(d.getUTCFullYear(), 0, 1));
  const week = Math.ceil(((d.getTime() - yearStart.getTime()) / 86400000 + 1) / 7);
  return `${d.getUTCFullYear()}-W${String(week).padStart(2, '0')}`;
}

function toBucketLabel(isoDate: string, granularity: Granularity): string {
  const d = new Date(isoDate);
  if (granularity === 'day') return d.toISOString().slice(0, 10);   // YYYY-MM-DD
  if (granularity === 'month') return d.toISOString().slice(0, 7);  // YYYY-MM
  return toISOWeekLabel(d);                                          // YYYY-Www
}

interface ChartBucket {
  label: string;
  LIVE: number;
  VOD: number;
  SERIES: number;
}

function buildChartData(entries: StreamHistoryEntry[], granularity: Granularity): ChartBucket[] {
  const buckets = new Map<string, ChartBucket>();
  for (const entry of entries) {
    const label = toBucketLabel(entry.start, granularity);
    const b = buckets.get(label) ?? { label, LIVE: 0, VOD: 0, SERIES: 0 };
    b[entry.streamType] += 1;
    buckets.set(label, b);
  }
  return Array.from(buckets.values()).sort((a, b) => a.label.localeCompare(b.label));
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
// Formatting helpers
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
// Constants
// ---------------------------------------------------------------------------

const TYPE_COLOR: Record<string, string> = {
  LIVE: '#1976d2',
  VOD: '#9c27b0',
  SERIES: '#0288d1',
};

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

interface Props {
  clientId: number;
}

export default function ClientHistoryTab({ clientId }: Props) {
  const { isAuthenticated } = useAuthStore();
  const queryClient = useQueryClient();

  // chart state
  const [granularity, setGranularity] = useState<Granularity>('day');

  // table state
  const [filters, setFilters] = useState<ColumnFilters>(EMPTY_FILTERS);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);

  // data
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
    },
  });

  const entries = historyResponse?.data?.data ?? [];
  const chartData = buildChartData(entries, granularity);
  const filtered = applyFilters(entries, filters);
  const totalPages = pageSize === 0 ? 1 : Math.ceil(filtered.length / pageSize);
  const displayed =
    pageSize === 0
      ? filtered
      : filtered.slice((page - 1) * pageSize, page * pageSize);

  const setFilter = (key: keyof ColumnFilters, value: string) => {
    setFilters((f) => ({ ...f, [key]: value }));
    setPage(1);
  };

  // ---------------------------------------------------------------------------
  // Render
  // ---------------------------------------------------------------------------

  if (isLoading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', p: 4 }}>
        <CircularProgress />
      </Box>
    );
  }

  return (
    <Box>
      {/* ── Header ── */}
      <Box
        sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}
      >
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
        <Box
          sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}
        >
          <Typography variant="subtitle1" fontWeight="medium">
            Activity over time
          </Typography>
          <ToggleButtonGroup
            value={granularity}
            exclusive
            onChange={(_, v) => v && setGranularity(v as Granularity)}
            size="small"
          >
            <ToggleButton value="day">Day</ToggleButton>
            <ToggleButton value="week">Week</ToggleButton>
            <ToggleButton value="month">Month</ToggleButton>
          </ToggleButtonGroup>
        </Box>

        {chartData.length === 0 ? (
          <Box
            sx={{ height: 200, display: 'flex', alignItems: 'center', justifyContent: 'center' }}
          >
            <Typography color="text.secondary">No data to display</Typography>
          </Box>
        ) : (
          <ResponsiveContainer width="100%" height={260}>
            <BarChart data={chartData} margin={{ top: 5, right: 20, left: 0, bottom: 5 }}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="label" tick={{ fontSize: 11 }} />
              <YAxis allowDecimals={false} tick={{ fontSize: 11 }} />
              <ChartTooltip />
              <Legend />
              <Bar dataKey="LIVE" stackId="a" fill={TYPE_COLOR.LIVE} name="Live" />
              <Bar dataKey="VOD" stackId="a" fill={TYPE_COLOR.VOD} name="VOD" />
              <Bar dataKey="SERIES" stackId="a" fill={TYPE_COLOR.SERIES} name="Series" radius={[2, 2, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        )}
      </Paper>

      {/* ── Table ── */}
      <Paper sx={{ p: 2 }}>
        <Box
          sx={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            mb: 1,
          }}
        >
          <Typography variant="subtitle1" fontWeight="medium">
            Entries
            {hasActiveFilters(filters) && (
              <Typography component="span" variant="body2" color="text.secondary" sx={{ ml: 1 }}>
                ({filtered.length} / {entries.length})
              </Typography>
            )}
          </Typography>
          <Stack direction="row" spacing={1} alignItems="center">
            <FormControl size="small" sx={{ minWidth: 120 }}>
              <Select
                value={pageSize}
                onChange={(e) => {
                  setPageSize(e.target.value as number);
                  setPage(1);
                }}
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
              onClick={() => {
                setFilters(EMPTY_FILTERS);
                setPage(1);
              }}
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
                  {/* Date range filter spans Start column */}
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
                {/* End & Duration columns: no filter */}
                <TableCell />
                <TableCell />
              </TableRow>
            </TableHead>

            <TableBody>
              {displayed.length === 0 ? (
                <TableRow>
                  <TableCell
                    colSpan={7}
                    align="center"
                    sx={{ color: 'text.secondary', py: 4 }}
                  >
                    {entries.length === 0
                      ? 'No watch history recorded yet'
                      : 'No entries match the current filters'}
                  </TableCell>
                </TableRow>
              ) : (
                displayed.map((entry, idx) => (
                  <TableRow key={`${entry.streamId}-${entry.start}-${idx}`} hover>
                    <TableCell>
                      {entry.streamName ?? (
                        <em style={{ color: '#9e9e9e' }}>unknown</em>
                      )}
                    </TableCell>
                    <TableCell
                      sx={{
                        color: entry.categoryName ? 'text.primary' : 'text.disabled',
                        fontSize: '0.8rem',
                      }}
                    >
                      {entry.categoryName ?? '—'}
                    </TableCell>
                    <TableCell sx={{ fontFamily: 'monospace', fontSize: '0.8rem' }}>
                      {entry.streamId}
                    </TableCell>
                    <TableCell>
                      <Chip
                        label={entry.streamType}
                        size="small"
                        sx={{
                          backgroundColor: TYPE_COLOR[entry.streamType],
                          color: '#fff',
                          fontSize: 11,
                        }}
                      />
                    </TableCell>
                    <TableCell sx={{ whiteSpace: 'nowrap', fontSize: '0.8rem' }}>
                      {new Date(entry.start).toLocaleString()}
                    </TableCell>
                    <TableCell sx={{ whiteSpace: 'nowrap', fontSize: '0.8rem' }}>
                      {entry.end ? (
                        new Date(entry.end).toLocaleString()
                      ) : (
                        <Chip label="active" size="small" color="success" variant="outlined" />
                      )}
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

