import api from './api';

/**
 * Single JVM snapshot returned by GET /api/jvm/metrics.
 *
 * All numeric fields that could not be collected on the current runtime
 * (e.g. file descriptors on Windows, CPU load without Sun extensions)
 * are returned as -1 by the server.  The charts map -1 → null so those
 * points are simply skipped rather than drawing a misleading zero.
 */
export interface JvmMetricsEntry {
  /** Server-local timestamp, ISO-8601 without timezone: "2026-03-17T10:00:00" */
  timestamp: string;

  // Heap (MB)
  heapUsedMb: number;
  heapMaxMb: number; // -1 when JVM has no -Xmx limit
  heapCommittedMb: number;

  // Non-heap / Metaspace (MB)
  nonHeapUsedMb: number;
  metaspaceMb: number; // -1 if pool not found

  // NIO direct buffers
  directBufferUsedMb: number; // -1 if unavailable
  directBufferCount: number; // -1 if unavailable

  // Process / OS memory (MB)
  processVirtualMemoryMb: number; // -1 if com.sun.management unavailable
  freePhysicalMemoryMb: number; // -1 if unavailable

  // CPU (0.0–1.0; -1 if unavailable)
  processCpuLoad: number;
  systemCpuLoad: number;

  // Threads
  threadCount: number;
  peakThreadCount: number;
  daemonThreadCount: number;

  // GC (cumulative totals)
  gcCollectionCount: number;
  gcCollectionTimeMs: number;

  // JVM uptime
  jvmUptimeSeconds: number;

  // Database
  /** Total DB size in MB. -1 if unavailable (H2 tests, query error). */
  dbSizeMb: number;
}

/**
 * Format a JS Date as a server-local ISO-8601 string without timezone offset.
 * LocalDateTime.parse() on the backend accepts exactly this format.
 * Using string formatting avoids timezone conversion issues that arise with
 * Date.toISOString() (which always outputs UTC).
 */
export function toServerDateTime(date: Date): string {
  const p = (n: number) => n.toString().padStart(2, '0');
  return (
    `${date.getFullYear()}-${p(date.getMonth() + 1)}-${p(date.getDate())}` +
    `T${p(date.getHours())}:${p(date.getMinutes())}:${p(date.getSeconds())}`
  );
}

class JvmMetricsApi {
  /**
   * Fetch JVM metric snapshots filtered by an optional date range.
   * Pass JS Date objects; this method converts them to the server-expected format.
   * The response list is already ordered oldest-first by the server.
   */
  async getMetrics(start?: Date, end?: Date): Promise<JvmMetricsEntry[]> {
    const params: Record<string, string> = {};
    if (start) params.startDate = toServerDateTime(start);
    if (end) params.endDate = toServerDateTime(end);
    const response = await api.get<{ data: JvmMetricsEntry[] }>('/jvm/metrics', { params });
    return response.data.data;
  }
}

export const jvmMetricsApi = new JvmMetricsApi();
export default jvmMetricsApi;

