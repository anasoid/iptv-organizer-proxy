package org.anasoid.iptvorganizer.cache;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
 * Application-scoped, thread-safe cache manager that supports multiple named caches and per-entry
 * TTL (time-to-live).
 *
 * <p>Each cache entry is indexed by a mandatory {@link Long} key and optionally by a {@link String}
 * key. The dual-index logic is encapsulated in {@link CacheMap}.
 *
 * <p>Each named cache is backed by a {@link CacheMap} guarded by a {@link ReentrantReadWriteLock}.
 * Max-size enforcement is implemented inside {@link CacheMap} via {@link java.util.LinkedHashMap}
 * {@code removeEldestEntry}: the oldest logical entry is evicted automatically and atomically on
 * every {@code put} that would exceed the limit — no separate eviction loop is needed.
 *
 * <p>Usage examples:
 *
 * <pre>{@code
 * @Inject CacheManager cacheManager;
 *
 * Cache sources = cacheManager.getCache("sources", 500);
 * sources.put("src-abc", 42L, value, Duration.ofMinutes(5));
 * Optional<MyType> hit = sources.get("src-abc", MyType.class);
 * }</pre>
 */
@ApplicationScoped
@Slf4j
public class CacheManager {

  /**
   * Per-named-cache store. All {@link CacheMap} access is serialised by a {@link
   * ReentrantReadWriteLock}: multiple readers proceed in parallel; writers are exclusive.
   *
   * <p>Statistics counters are {@link AtomicLong} fields updated without holding the r/w lock.
   */
  private static class CacheStore {
    final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    final Lock readLock = rwLock.readLock();
    final Lock writeLock = rwLock.writeLock();

    // ---- Statistics counters (thread-safe, updated without holding the r/w lock) ----
    final AtomicLong hitCount = new AtomicLong();
    final AtomicLong missCount = new AtomicLong();
    final AtomicLong putCount = new AtomicLong();

    /** Logical entries evicted due to max-size overflow (counted once per entry). */
    final AtomicLong sizeEvictionCount = new AtomicLong();

    /** Logical entries removed during scheduled TTL cleanup (counted once per entry). */
    final AtomicLong expiredEvictionCount = new AtomicLong();

    /** Successful key-based invalidations (one logical entry = one increment). */
    final AtomicLong invalidationCount = new AtomicLong();

    /** Number of invalidateAll calls on this cache. */
    final AtomicLong clearCount = new AtomicLong();

    /**
     * Dual-index store. {@code sizeEvictionCount} is wired in at construction time so that {@link
     * CacheMap} can increment it directly on each size-based eviction. Declared after {@code
     * sizeEvictionCount} so the field initialiser runs in order.
     */
    final CacheMap cache = new CacheMap(0, sizeEvictionCount);
  }

  /** cacheName -> CacheStore */
  private final Map<String, CacheStore> caches = new ConcurrentHashMap<>();

  // -------------------------------------------------------------------------
  // Cache facade
  // -------------------------------------------------------------------------

  /**
   * Returns a {@link Cache} facade bound to the given name with a maximum number of live entries
   * and no default TTL. When the limit is exceeded the oldest entry is evicted (FIFO)
   * automatically.
   *
   * @param cacheName logical name of the cache
   * @param maxSize maximum number of live entries; must be &gt;= 0 (0 = disabled)
   * @return a named {@link Cache} facade
   */
  public <V> Cache<V> getCache(String cacheName, int maxSize) {
    return getCache(cacheName, maxSize, null);
  }

  /**
   * Returns a {@link Cache} facade bound to the given name with a maximum number of live entries
   * and a default TTL. When the limit is exceeded the oldest entry is evicted (FIFO) automatically.
   *
   * @param cacheName logical name of the cache
   * @param maxSize maximum number of live entries; must be &gt;= 0 (0 = disabled)
   * @param ttl default time-to-live; {@code null} means permanent
   * @return a named {@link Cache} facade
   */
  public <V> Cache<V> getCache(String cacheName, int maxSize, Duration ttl) {
    if (maxSize < 0) {
      throw new IllegalArgumentException("maxSize must be >= 0");
    }
    getOrCreateStore(cacheName).cache.setMaxSize(maxSize);
    return new Cache(cacheName, this, maxSize, ttl);
  }

  // -------------------------------------------------------------------------
  // Core operations
  // -------------------------------------------------------------------------

  /**
   * Stores {@code value} under the given keys in the named cache with the specified TTL.
   *
   * @param cacheName logical name of the cache
   * @param stringKey string index key; may be {@code null}
   * @param longKey long index key; must be non-null
   * @param value value to cache (must not be {@code null})
   * @param ttl how long the entry lives; {@code null} for no expiry
   */
  protected void put(String cacheName, String stringKey, Long longKey, Object value, Duration ttl) {
    if (longKey == null) {
      throw new IllegalArgumentException("longKey must be non-null");
    }
    Instant expiresAt = ttl != null ? Instant.now().plus(ttl) : null;
    CacheEntry<Object> entry = new CacheEntry<>(value, expiresAt, stringKey, longKey);
    CacheStore store = getOrCreateStore(cacheName);
    store.putCount.incrementAndGet();

    store.writeLock.lock();
    try {
      store.cache.put(stringKey, longKey, entry);
    } finally {
      store.writeLock.unlock();
    }
    log.trace(
        "Cache [{}] PUT stringKey='{}' longKey={} ttl={}", cacheName, stringKey, longKey, ttl);
  }

  /**
   * Returns the cached value by string key if present and not expired.
   *
   * @param cacheName logical name of the cache
   * @param stringKey string index key
   * @return {@link Optional} containing the value, or empty if absent / expired
   */
  protected <V> Optional<V> get(String cacheName, String stringKey) {
    CacheStore store = caches.get(cacheName);
    if (store == null) {
      return Optional.empty();
    }
    store.readLock.lock();
    try {
      return resolveEntry(cacheName, store, store.cache.getByString(stringKey));
    } finally {
      store.readLock.unlock();
    }
  }

  /**
   * Returns the cached value by long key if present and not expired.
   *
   * @param cacheName logical name of the cache
   * @param longKey long index key
   * @return {@link Optional} containing the value, or empty if absent / expired
   */
  protected <V> Optional<V> get(String cacheName, Long longKey) {
    CacheStore store = caches.get(cacheName);
    if (store == null) {
      return Optional.empty();
    }
    store.readLock.lock();
    try {
      return resolveEntry(cacheName, store, store.cache.getByLong(longKey));
    } finally {
      store.readLock.unlock();
    }
  }

  /**
   * Returns the cached value if present and not expired, otherwise calls {@code loader}, stores the
   * result with the given TTL under both keys, and returns it.
   *
   * <p>Lookup order: string key first (if non-null), then long key (if non-null).
   */
  protected <V> V getOrLoad(
      String cacheName, String stringKey, Long longKey, Supplier<V> loader, Duration ttl) {
    if (stringKey != null) {
      Optional<V> cached = get(cacheName, stringKey);
      if (cached.isPresent()) {
        return cached.get();
      }
    }
    if (longKey != null) {
      Optional<V> cached = get(cacheName, longKey);
      if (cached.isPresent()) {
        return cached.get();
      }
    }
    log.trace("Cache [{}] MISS stringKey='{}' longKey={} – loading", cacheName, stringKey, longKey);
    V value = loader.get();
    if (value != null) {
      put(cacheName, stringKey, longKey, value, ttl);
    }
    return value;
  }

  /** Returns the cached value if present, otherwise loads and stores it permanently. */
  protected <V> V getOrLoad(String cacheName, String stringKey, Long longKey, Supplier<V> loader) {
    return getOrLoad(cacheName, stringKey, longKey, loader, null);
  }

  /** Returns {@code true} if a non-expired entry exists for the given string key. */
  protected boolean contains(String cacheName, String stringKey) {
    return get(cacheName, stringKey).isPresent();
  }

  /** Returns {@code true} if a non-expired entry exists for the given long key. */
  protected boolean contains(String cacheName, Long longKey) {
    return get(cacheName, longKey).isPresent();
  }

  // -------------------------------------------------------------------------
  // Invalidation
  // -------------------------------------------------------------------------

  /**
   * Removes the entry indexed under the given string key, including its long-key index.
   *
   * @return {@code true} if an entry was actually removed
   */
  protected boolean invalidate(String cacheName, String stringKey) {
    CacheStore store = caches.get(cacheName);
    if (store == null) {
      return false;
    }
    store.writeLock.lock();
    try {
      CacheEntry<Object> entry = store.cache.removeByString(stringKey);
      if (entry == null) {
        return false;
      }
      store.invalidationCount.incrementAndGet();
      log.debug("Cache [{}] INVALIDATE stringKey='{}'", cacheName, stringKey);
      return true;
    } finally {
      store.writeLock.unlock();
    }
  }

  /**
   * Removes the entry indexed under the given long key, including its string-key index if present.
   *
   * @return {@code true} if an entry was actually removed
   */
  protected boolean invalidate(String cacheName, Long longKey) {
    CacheStore store = caches.get(cacheName);
    if (store == null) {
      return false;
    }
    store.writeLock.lock();
    try {
      CacheEntry<Object> entry = store.cache.removeByLong(longKey);
      if (entry == null) {
        return false;
      }
      store.invalidationCount.incrementAndGet();
      log.debug("Cache [{}] INVALIDATE longKey={}", cacheName, longKey);
      return true;
    } finally {
      store.writeLock.unlock();
    }
  }

  /**
   * Removes the entry matching either key. At least one key must be non-null. Lookup is attempted
   * by {@code stringKey} first, then by {@code longKey}.
   *
   * @return {@code true} if an entry was actually removed
   */
  protected boolean invalidate(String cacheName, String stringKey, Long longKey) {
    if (stringKey == null && longKey == null) {
      throw new IllegalArgumentException(
          "At least one key (stringKey or longKey) must be non-null");
    }
    if (stringKey != null) {
      return invalidate(cacheName, stringKey);
    }
    return invalidate(cacheName, longKey);
  }

  /** Clears all entries in the named cache. */
  protected void invalidateAll(String cacheName) {
    CacheStore store = caches.get(cacheName);
    if (store != null && !store.cache.isEmpty()) {
      store.writeLock.lock();
      try {
        store.cache.clear();
        store.clearCount.incrementAndGet();
        log.debug("Cache [{}] CLEAR ALL", cacheName);
      } finally {
        store.writeLock.unlock();
      }
    }
  }

  /** Clears every named cache managed by this instance. */
  public void invalidateAll() {
    for (CacheStore store : caches.values()) {
      store.writeLock.lock();
      try {
        store.cache.clear();
        store.clearCount.incrementAndGet();
      } finally {
        store.writeLock.unlock();
      }
    }
    log.debug("ALL caches CLEARED");
  }

  // -------------------------------------------------------------------------
  // Statistics / inspection
  // -------------------------------------------------------------------------

  /** Returns the number of non-expired logical entries in the named cache. */
  protected long size(String cacheName) {
    CacheStore store = caches.get(cacheName);
    if (store == null) {
      return 0L;
    }
    store.readLock.lock();
    try {
      return store.cache.liveSize();
    } finally {
      store.readLock.unlock();
    }
  }

  /** Returns an unmodifiable view of the known cache names. */
  public Set<String> cacheNames() {
    return Collections.unmodifiableSet(caches.keySet());
  }

  // -------------------------------------------------------------------------
  // Scheduled eviction
  // -------------------------------------------------------------------------

  /**
   * Periodically removes expired entries from all caches. Runs every 5 minutes. Delegates to {@link
   * CacheMap#removeExpired()} which performs a single pass over the primary index, cleaning both
   * indexes atomically under the write lock and counting each logical entry exactly once.
   */
  @Scheduled(every = "5m", identity = "cache-eviction")
  void evictExpired() {
    int total = 0;
    for (Map.Entry<String, CacheStore> cacheEntry : caches.entrySet()) {
      String cacheName = cacheEntry.getKey();
      CacheStore store = cacheEntry.getValue();
      store.writeLock.lock();
      try {
        int evicted = store.cache.removeExpired();
        store.expiredEvictionCount.addAndGet(evicted);
        if (evicted > 0) {
          log.debug("Cache [{}] evicted {} expired entries", cacheName, evicted);
          total += evicted;
        }
      } finally {
        store.writeLock.unlock();
      }
    }
    if (total > 0) {
      log.debug("Cache eviction completed – {} entries removed in total", total);
    }
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  /**
   * Resolves a map lookup: returns empty for {@code null} or expired entries, incrementing the
   * appropriate counter on the store. Expiry removal is lazy — actual cleanup is handled by {@link
   * #evictExpired()}.
   */
  private <V> Optional<V> resolveEntry(
      String cacheName, CacheStore store, CacheEntry<Object> entry) {
    if (entry == null || entry.isExpired()) {
      store.missCount.incrementAndGet();
      if (entry != null) {
        log.trace(
            "Cache [{}] EXPIRED stringKey='{}' longKey={}",
            cacheName,
            entry.getStringKey(),
            entry.getLongKey());
      }
      return Optional.empty();
    }
    store.hitCount.incrementAndGet();
    log.trace(
        "Cache [{}] HIT stringKey='{}' longKey={}",
        cacheName,
        entry.getStringKey(),
        entry.getLongKey());
    return (Optional<V>) Optional.of(entry.getValue());
  }

  private CacheStore getOrCreateStore(String cacheName) {
    return caches.computeIfAbsent(cacheName, k -> new CacheStore());
  }

  // -------------------------------------------------------------------------
  // Statistics
  // -------------------------------------------------------------------------

  /**
   * Returns a snapshot of runtime statistics for the named cache, or {@link Optional#empty()} if no
   * cache with that name has ever been used.
   *
   * @param cacheName logical name of the cache
   * @return optional statistics snapshot
   */
  public Optional<CacheStat> getCacheStat(String cacheName) {
    CacheStore store = caches.get(cacheName);
    if (store == null) {
      return Optional.empty();
    }
    return Optional.of(buildStat(cacheName, store));
  }

  /**
   * Returns a snapshot of runtime statistics for every known cache, sorted by cache name.
   *
   * @return list of statistics snapshots (never {@code null}, may be empty)
   */
  public List<CacheStat> getAllCacheStats() {
    return caches.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .map(e -> buildStat(e.getKey(), e.getValue()))
        .collect(Collectors.toList());
  }

  private CacheStat buildStat(String cacheName, CacheStore store) {
    return CacheStat.builder()
        .cacheName(cacheName)
        .hits(store.hitCount.get())
        .misses(store.missCount.get())
        .puts(store.putCount.get())
        .sizeEvictions(store.sizeEvictionCount.get())
        .expiredEvictions(store.expiredEvictionCount.get())
        .invalidations(store.invalidationCount.get())
        .clears(store.clearCount.get())
        .size(size(cacheName))
        .maxSize(store.cache.getMaxSize())
        .build();
  }
}
