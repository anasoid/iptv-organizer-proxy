package org.anasoid.iptvorganizer.services.monitor;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import java.lang.management.BufferPoolMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.anasoid.iptvorganizer.models.monitor.JvmMetricsEntry;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Collects JVM and OS metrics every minute into a bounded, time-limited in-memory deque.
 *
 * <p>Retention policy (applied after each sample):
 *
 * <ol>
 *   <li>Size cap - drop oldest entries beyond {@code jvm.metrics.max.size}.
 *   <li>Age cap - drop entries older than {@code jvm.metrics.max.age.hours}.
 * </ol>
 *
 * <p>All {@code com.sun.management} extension access is guarded by {@code instanceof}
 * pattern-matching so the code is safe on GraalVM/Mandrel native without reflection config.
 */
@ApplicationScoped
@Slf4j
public class JvmMonitorService {
  @ConfigProperty(name = "jvm.metrics.max.size", defaultValue = "1440")
  int maxSize;

  @ConfigProperty(name = "jvm.metrics.max.age.hours", defaultValue = "24")
  int maxAgeHours;

  private final ArrayDeque<JvmMetricsEntry> metrics = new ArrayDeque<>();
  private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();

  @Scheduled(every = "1m", identity = "jvm-metrics")
  void collectMetrics() {
    JvmMetricsEntry entry = collectSnapshot();
    rwLock.writeLock().lock();
    try {
      metrics.addFirst(entry);
      // 1) size-based pruning
      while (metrics.size() > maxSize) {
        metrics.removeLast();
      }
      // 2) time-based pruning
      LocalDateTime cutoff = LocalDateTime.now().minusHours(maxAgeHours);
      while (!metrics.isEmpty() && metrics.peekLast().getTimestamp().isBefore(cutoff)) {
        metrics.removeLast();
      }
    } finally {
      rwLock.writeLock().unlock();
    }
    log.trace(
        "JVM metrics collected: heap={}MB cpu={}",
        entry.getHeapUsedMb(),
        entry.getProcessCpuLoad());
  }

  /**
   * Returns entries in the requested date range, ordered oldest-first.
   *
   * <p>Raw {@code gcCollectionCount} / {@code gcCollectionTimeMs} in the deque are cumulative JVM
   * totals. This method converts them to <strong>per-minute rates</strong> before returning:
   *
   * <ul>
   *   <li>For each in-range entry the delta against the immediately preceding entry (which may lie
   *       outside the requested range) is divided by the actual elapsed minutes between the two
   *       samples, keeping the rate accurate even when samples are slightly late or a gap exists.
   *   <li>The very first entry — when no predecessor exists in the deque — gets a delta of {@code
   *       0}.
   *   <li>Negative deltas (counter reset after JVM restart) are clamped to {@code 0}.
   * </ul>
   *
   * @param start inclusive lower bound; {@code null} means no lower bound
   * @param end inclusive upper bound; {@code null} means no upper bound
   */
  public List<JvmMetricsEntry> getMetrics(LocalDateTime start, LocalDateTime end) {
    List<JvmMetricsEntry> all;
    rwLock.readLock().lock();
    try {
      all =
          metrics.stream()
              .sorted(Comparator.comparing(JvmMetricsEntry::getTimestamp))
              .collect(Collectors.toList());
    } finally {
      rwLock.readLock().unlock();
    }
    return applyGcDeltas(all, start, end);
  }

  /**
   * Iterates <em>all</em> stored entries in chronological order. Out-of-range entries are not added
   * to the result but still advance {@code prev}, so the first in-range entry always has a valid
   * predecessor for delta computation.
   */
  private List<JvmMetricsEntry> applyGcDeltas(
      List<JvmMetricsEntry> all, LocalDateTime start, LocalDateTime end) {

    List<JvmMetricsEntry> result = new ArrayList<>();
    JvmMetricsEntry prev = null;

    for (JvmMetricsEntry current : all) {
      boolean inRange =
          (start == null || !current.getTimestamp().isBefore(start))
              && (end == null || !current.getTimestamp().isAfter(end));

      if (inRange) {
        if (prev == null) {
          // No predecessor available — emit zero rather than a misleading value.
          result.add(current.toBuilder().gcCollectionCount(0L).gcCollectionTimeMs(0L).build());
        } else {
          long seconds = Duration.between(prev.getTimestamp(), current.getTimestamp()).getSeconds();
          double minutes = seconds > 0 ? seconds / 60.0 : 1.0;
          long deltaCount =
              Math.max(0L, current.getGcCollectionCount() - prev.getGcCollectionCount());
          long deltaTime =
              Math.max(0L, current.getGcCollectionTimeMs() - prev.getGcCollectionTimeMs());
          result.add(
              current.toBuilder()
                  .gcCollectionCount(Math.round(deltaCount / minutes))
                  .gcCollectionTimeMs(Math.round(deltaTime / minutes))
                  .build());
        }
      }
      // Always advance prev — even for out-of-range entries — so the first in-range entry
      // can be properly diffed against its true predecessor.
      prev = current;
    }
    return result;
  }

  /** Returns the number of retained entries (for diagnostics). */
  public int size() {
    rwLock.readLock().lock();
    try {
      return metrics.size();
    } finally {
      rwLock.readLock().unlock();
    }
  }

  private JvmMetricsEntry collectSnapshot() {
    // -- Heap --
    MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();
    MemoryUsage heap = memBean.getHeapMemoryUsage();
    MemoryUsage nonHeap = memBean.getNonHeapMemoryUsage();
    long heapMax = heap.getMax(); // -1 when no -Xmx limit
    // -- Metaspace --
    long metaspaceMb =
        ManagementFactory.getMemoryPoolMXBeans().stream()
            .filter(p -> p.getName().contains("Metaspace"))
            .findFirst()
            .map(p -> toMb(p.getUsage().getUsed()))
            .orElse(-1L);
    // -- OS / process memory + CPU --
    // instanceof guards ensure GraalVM native safety (no ClassCastException, no reflection).
    OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
    long virtualMemoryMb = -1L;
    long freePhysicalMb = -1L;
    double processCpuLoad = -1.0;
    double systemCpuLoad = -1.0;
    if (osBean instanceof com.sun.management.OperatingSystemMXBean sunOs) {
      virtualMemoryMb = toMb(sunOs.getCommittedVirtualMemorySize());
      freePhysicalMb = toMb(sunOs.getFreeMemorySize());
      processCpuLoad = sunOs.getProcessCpuLoad();
      systemCpuLoad = sunOs.getCpuLoad();
    }
    // -- Threads --
    ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
    // -- GC totals across all collectors --
    long gcCount = 0L;
    long gcTime = 0L;
    for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
      long c = gc.getCollectionCount();
      long t = gc.getCollectionTime();
      if (c > 0) gcCount += c;
      if (t > 0) gcTime += t;
    }
    // -- NIO direct buffers --
    long directUsedMb = -1L;
    long directCount = -1L;
    for (BufferPoolMXBean pool : ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)) {
      if ("direct".equals(pool.getName())) {
        directUsedMb = toMb(pool.getMemoryUsed());
        directCount = pool.getCount();
        break;
      }
    }
    // -- Runtime uptime --
    RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
    return JvmMetricsEntry.builder()
        .timestamp(LocalDateTime.now())
        .heapUsedMb(toMb(heap.getUsed()))
        .heapMaxMb(heapMax < 0 ? -1L : toMb(heapMax))
        .heapCommittedMb(toMb(heap.getCommitted()))
        .nonHeapUsedMb(toMb(nonHeap.getUsed()))
        .metaspaceMb(metaspaceMb)
        .processVirtualMemoryMb(virtualMemoryMb)
        .freePhysicalMemoryMb(freePhysicalMb)
        .processCpuLoad(processCpuLoad)
        .systemCpuLoad(systemCpuLoad)
        .threadCount(threadBean.getThreadCount())
        .peakThreadCount(threadBean.getPeakThreadCount())
        .daemonThreadCount(threadBean.getDaemonThreadCount())
        .gcCollectionCount(gcCount)
        .gcCollectionTimeMs(gcTime)
        .directBufferUsedMb(directUsedMb)
        .directBufferCount(directCount)
        .jvmUptimeSeconds(runtimeBean.getUptime() / 1000L)
        .build();
  }

  private static long toMb(long bytes) {
    if (bytes < 0) return -1L;
    return bytes / (1024L * 1024L);
  }
}
