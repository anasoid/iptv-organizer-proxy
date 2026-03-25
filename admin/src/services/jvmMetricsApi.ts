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
  /** RSS — actual physical RAM used by this process (what top/ps show). -1 on non-Linux. */
  processRssMb: number;
  /** VSZ — virtual address space size; always much larger than RSS, not what top shows. -1 if com.sun.management unavailable */
  processVirtualMemoryMb: number; // -1 if com.sun.management unavailable
  /** MemFree — truly free pages only, excludes buff/cache (lower than what free -m shows). -1 if unavailable */
  freePhysicalMemoryMb: number; // -1 if unavailable
  /** MemAvailable — free + reclaimable cache/buffers; matches free -m "available" column. -1 on non-Linux. */
  memAvailableMb: number;

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
 * Live snapshot of a single JVM thread returned by GET /api/jvm/metrics/threads.
 */
export interface ThreadInfo {
  /** JVM-assigned thread id — unique within a single JVM lifetime. */
  id: number;
  /** Thread name (e.g. "executor-thread-1"). */
  name: string;
  /**
   * Current state: "NEW" | "RUNNABLE" | "BLOCKED" | "WAITING" | "TIMED_WAITING" | "TERMINATED"
   */
  state: 'NEW' | 'RUNNABLE' | 'BLOCKED' | 'WAITING' | 'TIMED_WAITING' | 'TERMINATED' | string;
  /** true for daemon (background JVM) threads. */
  daemon: boolean;
  /** Thread scheduling priority (1–10; normal = 5). */
  priority: number;
}

/**
 * Format a JS Date as an ISO-8601 timestamp with timezone (UTC "Z").
 * The backend accepts offset timestamps and converts them to server-local
 * time using the same instant, which keeps filtering correct across
 * browser/container timezone differences.
 */
export function toServerDateTime(date: Date): string {
  return date.toISOString();
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

  /**
   * Fetch a live snapshot of all JVM threads with their current state.
   * The list is sorted alphabetically by thread name.
   */
  async getThreads(): Promise<ThreadInfo[]> {
    const response = await api.get<{ data: ThreadInfo[] }>('/jvm/metrics/threads');
    return response.data.data;
  }
}

export const jvmMetricsApi = new JvmMetricsApi();
export default jvmMetricsApi;

