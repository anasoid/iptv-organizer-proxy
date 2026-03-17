package org.anasoid.iptvorganizer.models.monitor;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

/**
 * Immutable snapshot of JVM and OS metrics collected at a single point in time.
 *
 * <p>Fields that cannot be retrieved on the current JVM / OS combination (e.g. open file
 * descriptors on Windows, or CPU load on GraalVM without the {@code com.sun.management} extension)
 * are stored as {@code -1} and serialised as {@code -1} in JSON. The admin UI maps {@code -1} to
 * {@code null} so charts simply omit those data points rather than drawing a misleading zero.
 *
 * <p>All memory values are in megabytes (MB) to keep numbers human-readable.
 */
@Getter
@Builder(toBuilder = true)
public class JvmMetricsEntry {

  // ── Timing ───────────────────────────────────────────────────────────────

  /** Timestamp when this snapshot was taken (server-local time). */
  private final LocalDateTime timestamp;

  // ── Heap memory (MB) ─────────────────────────────────────────────────────

  /** Heap memory currently used by live objects. */
  private final long heapUsedMb;

  /**
   * Maximum heap size configured for this JVM ({@code -Xmx}). {@code -1} when no limit is set (rare
   * but possible).
   */
  private final long heapMaxMb;

  /** Heap memory currently reserved (committed) from the OS — always ≥ heapUsedMb. */
  private final long heapCommittedMb;

  // ── Non-heap / Metaspace (MB) ─────────────────────────────────────────────

  /** Non-heap memory used (JIT compiled code, class metadata, …). */
  private final long nonHeapUsedMb;

  /**
   * Metaspace (class metadata) usage. {@code -1} if the pool cannot be found (e.g. old PermGen JVMs
   * or exotic GC configurations).
   */
  private final long metaspaceMb;

  // ── NIO direct buffers (MB / count) ──────────────────────────────────────

  /**
   * Memory used by the JVM's off-heap NIO {@code direct} buffer pool. This is highly relevant for
   * an IPTV proxy: stream-copy code allocates direct buffers for zero-copy I/O. {@code -1} if the
   * pool cannot be found.
   */
  private final long directBufferUsedMb;

  /** Number of allocated direct {@link java.nio.ByteBuffer} objects. {@code -1} if unavailable. */
  private final long directBufferCount;

  // ── Process / OS memory (MB) ─────────────────────────────────────────────

  /**
   * Virtual memory committed (reserved) for the process by the OS. Includes heap + non-heap + JIT
   * code cache + thread stacks + native libraries. A growing trend here hints at native memory
   * leaks. {@code -1} if {@code com.sun.management.OperatingSystemMXBean} is not available.
   */
  private final long processVirtualMemoryMb;

  /**
   * Free physical RAM on the host at sample time. Useful for detecting host memory pressure that
   * would force the OS to swap. {@code -1} if unavailable.
   */
  private final long freePhysicalMemoryMb;

  // ── CPU (0.0–1.0; -1 if unavailable) ────────────────────────────────────

  /**
   * Recent CPU utilisation of the JVM process (0.0 = idle, 1.0 = 100 % of one core). Averaged over
   * the interval between two samples. {@code -1.0} if the metric is not available (GraalVM without
   * Sun extensions, some container environments).
   */
  private final double processCpuLoad;

  /** Recent CPU utilisation of the entire host system. {@code -1.0} if unavailable. */
  private final double systemCpuLoad;

  // ── Threads ──────────────────────────────────────────────────────────────

  /** Number of live (non-terminated) threads at sample time. */
  private final int threadCount;

  /**
   * Highest thread count since JVM start (or since last call to {@link
   * java.lang.management.ThreadMXBean#resetPeakThreadCount()}).
   */
  private final int peakThreadCount;

  /** Number of daemon threads at sample time. */
  private final int daemonThreadCount;

  // ── Garbage collection (cumulative totals across ALL collectors) ──────────

  /** Total GC collections performed since JVM start. */
  private final long gcCollectionCount;

  /** Total wall-clock time spent in GC pauses since JVM start (milliseconds). */
  private final long gcCollectionTimeMs;

  // ── JVM runtime ──────────────────────────────────────────────────────────

  /** Seconds elapsed since JVM start. Useful for detecting unexpected restarts. */
  private final long jvmUptimeSeconds;

  // ── Database ──────────────────────────────────────────────────────────────

  /**
   * Total database size in megabytes at sample time. Computed via {@code PRAGMA page_count} ×
   * {@code page_size} for SQLite, or {@code information_schema} for MySQL. {@code -1} if the query
   * fails or the dialect is unsupported (e.g. H2 in tests).
   */
  private final long dbSizeMb;
}
