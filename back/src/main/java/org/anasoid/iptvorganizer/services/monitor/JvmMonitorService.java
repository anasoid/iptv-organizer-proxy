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
import java.time.LocalDateTime;
import java.util.ArrayDeque;
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
   * Returns entries filtered by date range, sorted oldest-first.
   *
   * @param start inclusive lower bound; null means no lower bound
   * @param end inclusive upper bound; null means no upper bound
   */
  public List<JvmMetricsEntry> getMetrics(LocalDateTime start, LocalDateTime end) {
    rwLock.readLock().lock();
    try {
      return metrics.stream()
          .filter(
              e ->
                  (start == null || !e.getTimestamp().isBefore(start))
                      && (end == null || !e.getTimestamp().isAfter(end)))
          .sorted(Comparator.comparing(JvmMetricsEntry::getTimestamp))
          .collect(Collectors.toList());
    } finally {
      rwLock.readLock().unlock();
    }
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
