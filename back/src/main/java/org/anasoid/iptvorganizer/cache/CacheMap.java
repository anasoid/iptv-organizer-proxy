package org.anasoid.iptvorganizer.cache;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Dual-index map backing a single named cache.
 *
 * <p>Every entry is indexed by a mandatory {@link Long} key (<em>primary index</em>) and optionally
 * by a {@link String} key (<em>secondary index</em>).
 *
 * <ul>
 *   <li>The <strong>primary index</strong> ({@code byLong}) contains <em>all</em> entries and is
 *       the sole authority for {@code maxSize} enforcement via {@link
 *       LinkedHashMap#removeEldestEntry}. On eviction it cross-removes the matching string-key
 *       entry from the secondary index and increments the supplied {@code sizeEvictionCount}
 *       counter — so each logical eviction is counted exactly once.
 *   <li>The <strong>secondary index</strong> ({@code byString}) is a strict subset of the primary
 *       index and does not enforce {@code maxSize} independently.
 * </ul>
 *
 * <p>Because {@link CacheEntry} has no {@code equals}/{@code hashCode} override, all
 * identity-sensitive cross-removals use {@link Map#remove(Object, Object)} (value must match by
 * identity).
 *
 * <p><strong>Thread-safety:</strong> all methods are <em>not</em> thread-safe; callers must hold
 * the surrounding read/write lock.
 */
public class CacheMap {

  private volatile int maxSize;
  private final AtomicLong sizeEvictionCount;

  /** Secondary index – subset of {@code byLong}; no maxSize enforcement. */
  private final LinkedHashMap<String, CacheEntry<Object>> byString = new LinkedHashMap<>();

  /** Primary index – contains ALL entries; sole authority for maxSize enforcement. */
  private final LinkedHashMap<Long, CacheEntry<Object>> byLong;

  /**
   * Creates a new {@code CacheMap}.
   *
   * @param maxSize maximum number of entries; {@code 0} means unlimited
   * @param sizeEvictionCount counter incremented once per logical size-based eviction
   */
  CacheMap(int maxSize, AtomicLong sizeEvictionCount) {
    this.maxSize = maxSize;
    this.sizeEvictionCount = sizeEvictionCount;
    this.byLong =
        new LinkedHashMap<>() {
          @Override
          protected boolean removeEldestEntry(Map.Entry<Long, CacheEntry<Object>> eldest) {
            if (CacheMap.this.maxSize > 0 && size() > CacheMap.this.maxSize) {
              CacheMap.this.sizeEvictionCount.incrementAndGet();
              String sk = eldest.getValue().getStringKey();
              if (sk != null) {
                CacheMap.this.byString.remove(sk, eldest.getValue());
              }
              return true;
            }
            return false;
          }
        };
  }

  // -------------------------------------------------------------------------
  // Configuration
  // -------------------------------------------------------------------------

  /** Sets (or updates) the maximum number of entries. {@code 0} means unlimited. */
  public void setMaxSize(int maxSize) {
    this.maxSize = maxSize;
  }

  /** Returns the configured {@code maxSize} ({@code 0} = unlimited). */
  public int getMaxSize() {
    return maxSize;
  }

  // -------------------------------------------------------------------------
  // Write operations
  // -------------------------------------------------------------------------

  /**
   * Stores {@code entry} under both keys. {@code longKey} is mandatory; {@code stringKey} may be
   * {@code null}. Handles stale cross-index cleanup automatically. {@link
   * LinkedHashMap#removeEldestEntry} fires automatically on the primary index if the insertion
   * would exceed {@code maxSize}.
   *
   * @param stringKey optional string index key
   * @param longKey mandatory long index key
   * @param entry entry to store
   * @return the previous entry mapped to {@code longKey}, or {@code null}
   */
  public CacheEntry<Object> put(String stringKey, Long longKey, CacheEntry<Object> entry) {
    // Primary index first – enforces maxSize, may cross-remove the eldest entry's stringKey.
    CacheEntry<Object> oldLong = byLong.put(longKey, entry);
    if (oldLong != null
        && oldLong.getStringKey() != null
        && !oldLong.getStringKey().equals(stringKey)) {
      // Replaced entry had a different stringKey — remove stale secondary index entry.
      byString.remove(oldLong.getStringKey(), oldLong);
    }
    // Secondary index – populated only when stringKey is provided.
    if (stringKey != null) {
      CacheEntry<Object> oldString = byString.put(stringKey, entry);
      if (oldString != null && !oldString.getLongKey().equals(longKey)) {
        // Replaced entry had a different longKey — remove stale primary index entry.
        byLong.remove(oldString.getLongKey(), oldString);
      }
    }
    return oldLong;
  }

  /**
   * Removes and returns the entry indexed under {@code stringKey}, also removing its primary-index
   * entry.
   *
   * @return the removed entry, or {@code null} if not present
   */
  public CacheEntry<Object> removeByString(String stringKey) {
    CacheEntry<Object> entry = byString.remove(stringKey);
    if (entry != null) {
      byLong.remove(entry.getLongKey());
    }
    return entry;
  }

  /**
   * Removes and returns the entry indexed under {@code longKey}, also removing its secondary-index
   * entry when present.
   *
   * @return the removed entry, or {@code null} if not present
   */
  public CacheEntry<Object> removeByLong(Long longKey) {
    CacheEntry<Object> entry = byLong.remove(longKey);
    if (entry != null && entry.getStringKey() != null) {
      byString.remove(entry.getStringKey());
    }
    return entry;
  }

  /**
   * Single-pass TTL eviction over the primary index. For each expired entry the corresponding
   * secondary-index entry is also removed. Each logical entry is counted exactly once.
   *
   * @return number of logical entries removed
   */
  public int removeExpired() {
    int[] evicted = {0};
    byLong
        .entrySet()
        .removeIf(
            e -> {
              if (e.getValue().isExpired()) {
                String sk = e.getValue().getStringKey();
                if (sk != null) {
                  byString.remove(sk, e.getValue());
                }
                evicted[0]++;
                return true;
              }
              return false;
            });
    return evicted[0];
  }

  /** Removes all entries from both indexes. */
  public void clear() {
    byString.clear();
    byLong.clear();
  }

  // -------------------------------------------------------------------------
  // Read operations
  // -------------------------------------------------------------------------

  /** Returns the entry for the given string key, or {@code null} if not present. */
  public CacheEntry<Object> getByString(String stringKey) {
    return byString.get(stringKey);
  }

  /** Returns the entry for the given long key, or {@code null} if not present. */
  public CacheEntry<Object> getByLong(Long longKey) {
    return byLong.get(longKey);
  }

  // -------------------------------------------------------------------------
  // Size / emptiness
  // -------------------------------------------------------------------------

  /** Returns the total number of entries in the primary index (including expired ones). */
  public int size() {
    return byLong.size();
  }

  /** Returns the number of <em>non-expired</em> entries in the primary index. */
  public long liveSize() {
    return byLong.values().stream().filter(e -> !e.isExpired()).count();
  }

  /** Returns {@code true} if the primary index contains no entries. */
  public boolean isEmpty() {
    return byLong.isEmpty();
  }
}
