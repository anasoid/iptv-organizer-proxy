import { useState, useCallback } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  Box,
  Paper,
  Typography,
  ToggleButton,
  ToggleButtonGroup,
  Switch,
  FormControlLabel,
  IconButton,
  Grid,
  Chip,
  CircularProgress,
  Alert,
  Tooltip,
} from '@mui/material';
import { Refresh as RefreshIcon } from '@mui/icons-material';
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip as ChartTooltip,
  Legend,
  ResponsiveContainer,
} from 'recharts';
import jvmMetricsApi, { type JvmMetricsEntry } from '../services/jvmMetricsApi';
// ---------------------------------------------------------------------------
// Time range helpers
// ---------------------------------------------------------------------------
const TIME_RANGES: { label: string; minutes: number }[] = [
  { label: '15m', minutes: 15 },
  { label: '30m', minutes: 30 },
  { label: '1h', minutes: 60 },
  { label: '6h', minutes: 360 },
  { label: '24h', minutes: 1440 },
];
/**
 * Format an ISO timestamp string "2026-03-17T10:05:00" for the X-axis label.
 * We parse by string split (not new Date()) to avoid timezone conversion.
 */
function fmtTimestamp(ts: string, rangeMinutes: number): string {
  const [datePart, timePart = ''] = ts.split('T');
  const hhmm = timePart.substring(0, 5); // "HH:mm"
  if (rangeMinutes <= 60) return hhmm;
  const mmdd = datePart.substring(5); // "MM-DD"
  return `${mmdd} ${hhmm}`;
}
function fmtUptime(seconds: number): string {
  if (seconds < 60) return `${seconds}s`;
  if (seconds < 3600) return `${Math.floor(seconds / 60)}m`;
  if (seconds < 86400)
    return `${Math.floor(seconds / 3600)}h ${Math.floor((seconds % 3600) / 60)}m`;
  return `${Math.floor(seconds / 86400)}d ${Math.floor((seconds % 86400) / 3600)}h`;
}
// Map -1 (unavailable) to null so Recharts skips those points.
function avail(v: number): number | null {
  return v < 0 ? null : v;
}
// ---------------------------------------------------------------------------
// Reusable chart component
// ---------------------------------------------------------------------------
interface ChartLine {
  key: string;
  name: string;
  color: string;
  unit?: string;
}
interface MetricChartProps {
  title: string;
  data: Record<string, unknown>[];
  lines: ChartLine[];
  yFormatter?: (v: number) => string;
  height?: number;
}
function MetricChart({ title, data, lines, yFormatter, height = 220 }: MetricChartProps) {
  const fmt = yFormatter ?? ((v: number) => String(v));
  return (
    <Paper sx={{ p: 2, height: '100%' }}>
      <Typography variant="subtitle2" fontWeight="bold" gutterBottom>
        {title}
      </Typography>
      {data.length === 0 ? (
        <Box
          sx={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            height,
            color: 'text.secondary',
          }}
        >
          <Typography variant="body2">
            No data yet — metrics are collected every minute.
          </Typography>
        </Box>
      ) : (
        <ResponsiveContainer width="100%" height={height}>
          <LineChart data={data} margin={{ top: 4, right: 16, left: 0, bottom: 4 }}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis dataKey="_label" tick={{ fontSize: 11 }} />
            <YAxis tickFormatter={fmt} tick={{ fontSize: 11 }} width={56} />
            <ChartTooltip
              formatter={(value, name) => [
                value !== null ? fmt(value as number) : 'N/A',
                name,
              ]}
            />
            <Legend wrapperStyle={{ fontSize: 12 }} />
            {lines.map((l) => (
              <Line
                key={l.key}
                type="monotone"
                dataKey={l.key}
                name={l.name}
                stroke={l.color}
                dot={false}
                strokeWidth={1.5}
                connectNulls={false}
              />
            ))}
          </LineChart>
        </ResponsiveContainer>
      )}
    </Paper>
  );
}
// ---------------------------------------------------------------------------
// Main page
// ---------------------------------------------------------------------------
export default function JvmMetrics() {
  const [rangeMinutes, setRangeMinutes] = useState(60);
  const [autoRefresh, setAutoRefresh] = useState(true);
  const [refreshSeed, setRefreshSeed] = useState(0);
  const fetchMetrics = useCallback(() => {
    const end = new Date();
    const start = new Date(end.getTime() - rangeMinutes * 60 * 1000);
    return jvmMetricsApi.getMetrics(start, end);
  }, [rangeMinutes, refreshSeed]); // eslint-disable-line react-hooks/exhaustive-deps
  const { data: raw, isLoading, error } = useQuery({
    queryKey: ['jvmMetrics', rangeMinutes, refreshSeed],
    queryFn: fetchMetrics,
    refetchInterval: autoRefresh ? 60_000 : false,
  });
  const entries: JvmMetricsEntry[] = raw ?? [];
  // Transform entries into chart-ready rows
  const chartData = entries.map((m) => ({
    _label: fmtTimestamp(m.timestamp, rangeMinutes),
    // Heap
    heapUsed: avail(m.heapUsedMb),
    heapCommitted: avail(m.heapCommittedMb),
    heapMax: m.heapMaxMb > 0 ? m.heapMaxMb : null,
    // Non-heap
    nonHeapUsed: avail(m.nonHeapUsedMb),
    metaspace: avail(m.metaspaceMb),
    directBuffer: avail(m.directBufferUsedMb),
    // Process memory
    rss: avail(m.processRssMb),
    virtualMem: avail(m.processVirtualMemoryMb),
    memAvailable: avail(m.memAvailableMb),
    freePhysical: avail(m.freePhysicalMemoryMb),
    // CPU (convert 0-1 to %)
    processCpu: m.processCpuLoad >= 0 ? +((m.processCpuLoad * 100).toFixed(1)) : null,
    systemCpu: m.systemCpuLoad >= 0 ? +((m.systemCpuLoad * 100).toFixed(1)) : null,
    // Threads
    threads: avail(m.threadCount),
    peakThreads: avail(m.peakThreadCount),
    daemonThreads: avail(m.daemonThreadCount),
    // GC
    gcTime: avail(m.gcCollectionTimeMs),
    gcCount: avail(m.gcCollectionCount),
    // DB size
    dbSize: avail(m.dbSizeMb),
  }));
  const latest = entries.length > 0 ? entries[entries.length - 1] : null;
  const pct = (v: number) =>
    v >= 0 ? `${(v * 100).toFixed(1)}%` : 'N/A';
  const mb = (v: number) => `${v} MB`;
  const fmtPct = (v: number) => `${v}%`;
  return (
    <Box>
      {/* ── Header ─────────────────────────────────────────────────────── */}
      <Box
        sx={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          mb: 2,
          flexWrap: 'wrap',
          gap: 1,
        }}
      >
        <Typography variant="h5">JVM Metrics</Typography>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <FormControlLabel
            control={
              <Switch
                checked={autoRefresh}
                onChange={(e) => setAutoRefresh(e.target.checked)}
                size="small"
              />
            }
            label={<Typography variant="body2">Auto-refresh (1 min)</Typography>}
          />
          <Tooltip title="Refresh now">
            <IconButton size="small" onClick={() => setRefreshSeed((s) => s + 1)}>
              <RefreshIcon />
            </IconButton>
          </Tooltip>
        </Box>
      </Box>
      {/* ── Time range selector ─────────────────────────────────────────── */}
      <Box sx={{ mb: 2 }}>
        <ToggleButtonGroup
          value={rangeMinutes}
          exclusive
          onChange={(_, v) => v != null && setRangeMinutes(v)}
          size="small"
        >
          {TIME_RANGES.map((r) => (
            <ToggleButton key={r.minutes} value={r.minutes}>
              {r.label}
            </ToggleButton>
          ))}
        </ToggleButtonGroup>
      </Box>
      {/* ── Loading / error states ──────────────────────────────────────── */}
      {isLoading && (
        <Box sx={{ display: 'flex', justifyContent: 'center', my: 4 }}>
          <CircularProgress />
        </Box>
      )}
      {error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          Failed to load JVM metrics.
        </Alert>
      )}
      {/* ── Summary chips ───────────────────────────────────────────────── */}
      {latest && (
        <Box sx={{ display: 'flex', gap: 1, mb: 2, flexWrap: 'wrap' }}>
          <Chip
            label={`Heap: ${latest.heapUsedMb} / ${latest.heapMaxMb > 0 ? latest.heapMaxMb : '?'} MB`}
            color="primary"
            variant="outlined"
            size="small"
          />
          {latest.processRssMb >= 0 && (
            <Chip
              label={`RSS: ${latest.processRssMb} MB`}
              color="warning"
              variant="outlined"
              size="small"
            />
          )}
          <Chip
            label={`CPU: ${pct(latest.processCpuLoad)}`}
            color="secondary"
            variant="outlined"
            size="small"
          />
          <Chip
            label={`Threads: ${latest.threadCount}`}
            variant="outlined"
            size="small"
          />
          <Chip
            label={`Uptime: ${fmtUptime(latest.jvmUptimeSeconds)}`}
            variant="outlined"
            size="small"
          />
          {latest.dbSizeMb >= 0 && (
            <Chip
              label={`DB: ${latest.dbSizeMb} MB`}
              color="success"
              variant="outlined"
              size="small"
            />
          )}
        </Box>
      )}
      {/* ── Charts grid ─────────────────────────────────────────────────── */}
      {!isLoading && (
        <Grid container spacing={2}>
          {/* 1 – Heap memory */}
          <Grid size={{ xs: 12, md: 6 }}>
            <MetricChart
              title="Heap Memory (MB)"
              data={chartData}
              lines={[
                { key: 'heapUsed', name: 'Used', color: '#f44336' },
                { key: 'heapCommitted', name: 'Committed', color: '#ff9800' },
                { key: 'heapMax', name: 'Max', color: '#9e9e9e' },
              ]}
              yFormatter={mb}
            />
          </Grid>
          {/* 2 – Non-heap / Metaspace / Direct buffers */}
          <Grid size={{ xs: 12, md: 6 }}>
            <MetricChart
              title="Non-Heap Memory (MB)"
              data={chartData}
              lines={[
                { key: 'nonHeapUsed', name: 'Non-Heap Used', color: '#9c27b0' },
                { key: 'metaspace', name: 'Metaspace', color: '#e91e63' },
                { key: 'directBuffer', name: 'Direct Buffers', color: '#00bcd4' },
              ]}
              yFormatter={mb}
            />
          </Grid>
          {/* 3 – Process memory */}
          <Grid size={{ xs: 12, md: 6 }}>
            <MetricChart
              title="Process Memory (MB)"
              data={chartData}
              lines={[
                { key: 'rss', name: 'RSS (actual, matches top/ps)', color: '#f44336' },
                { key: 'memAvailable', name: 'Available RAM (incl. cache)', color: '#4caf50' },
                { key: 'freePhysical', name: 'MemFree (excl. cache)', color: '#bdbdbd' },
                { key: 'virtualMem', name: 'VSZ (virtual)', color: '#e0e0e0' },
              ]}
              yFormatter={mb}
            />
          </Grid>
          {/* 4 – CPU */}
          <Grid size={{ xs: 12, md: 6 }}>
            <MetricChart
              title="CPU Load (%)"
              data={chartData}
              lines={[
                { key: 'processCpu', name: 'Process CPU', color: '#2196f3' },
                { key: 'systemCpu', name: 'System CPU', color: '#009688' },
              ]}
              yFormatter={fmtPct}
            />
          </Grid>
          {/* 5 – Threads */}
          <Grid size={{ xs: 12, md: 6 }}>
            <MetricChart
              title="Threads"
              data={chartData}
              lines={[
                { key: 'threads', name: 'Live', color: '#3f51b5' },
                { key: 'peakThreads', name: 'Peak', color: '#f44336' },
                { key: 'daemonThreads', name: 'Daemon', color: '#009688' },
              ]}
            />
          </Grid>
          {/* 6 – GC */}
          <Grid size={{ xs: 12, md: 6 }}>
            <MetricChart
              title="GC Activity (per minute)"
              data={chartData}
              lines={[
                { key: 'gcTime', name: 'GC pause time/min (ms)', color: '#ff5722' },
                { key: 'gcCount', name: 'GC collections/min', color: '#795548' },
              ]}
            />
          </Grid>
          {/* 7 – Database size */}
          <Grid size={{ xs: 12, md: 6 }}>
            <MetricChart
              title="Database Size (MB)"
              data={chartData}
              lines={[
                { key: 'dbSize', name: 'DB Size', color: '#43a047' },
              ]}
              yFormatter={mb}
            />
          </Grid>
        </Grid>
      )}
    </Box>
  );
}
