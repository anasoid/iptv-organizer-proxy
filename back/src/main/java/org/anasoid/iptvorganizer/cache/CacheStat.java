package org.anasoid.iptvorganizer.cache;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * Immutable snapshot of runtime statistics for a single named cache.
 *
 * <p>Instances are created by {@link CacheManager#getCacheStat(String)} and {@link
 * CacheManager#getAllCacheStats()}.
 *
 * <pre>{@code
 * CacheStat stat = cacheManager.getCacheStat("sources").orElseThrow();
 * System.out.println("hit-rate: " + stat.hitRate());
 * }</pre>
 */
@Getter
@Builder
@ToString
public class CacheStat {

  /** Logical name of the cache. */
  private final String cacheName;

  /** Number of successful cache lookups (entry found and not expired). */
  private final long hits;

  /** Number of cache lookups that returned empty (entry absent or expired). */
  private final long misses;

  /** Number of {@code put} calls recorded for this cache. */
  private final long puts;

  /**
   * Number of index-level entries evicted because the cache exceeded its {@code maxSize} limit
   * (LRU / insertion-order eviction). A dual-keyed entry (string + long) may increment this twice.
   */
  private final long sizeEvictions;

  /**
   * Number of index-level entries removed during scheduled TTL expiry cleanup. A dual-keyed entry
   * counts at most once (byString pass removes it from byLong).
   */
  private final long expiredEvictions;

  /**
   * Number of key-based {@code invalidate(key)} calls that actually removed an entry. Each call
   * counts once regardless of how many keys the entry was indexed under.
   */
  private final long invalidations;

  /** Number of {@code invalidateAll} calls that completely cleared this cache. */
  private final long clears;

  /** Current number of live (non-expired) logical entries in the cache. */
  private final long size;

  /** Configured maximum number of entries; {@code 0} means unlimited. */
  private final int maxSize;

  // -------------------------------------------------------------------------
  // Derived metrics
  // -------------------------------------------------------------------------

  /**
   * Ratio of hits to total lookups ({@code hits + misses}). Returns {@code 0.0} when no lookups
   * have occurred yet.
   */
  public double hitRate() {
    long total = hits + misses;
    return total == 0 ? 0.0 : (double) hits / total;
  }

  /** Sum of {@link #sizeEvictions} and {@link #expiredEvictions}. */
  public long totalEvictions() {
    return sizeEvictions + expiredEvictions;
  }
}

