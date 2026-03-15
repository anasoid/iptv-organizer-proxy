package org.anasoid.iptvorganizer.cache;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

/**
 * Application-scoped, thread-safe cache manager that supports multiple named caches and per-entry
 * TTL (time-to-live).
 *
 * <p>Each cache entry can be indexed by a {@link String} key, a {@link Long} key, or both. At least
 * one key must be provided when storing an entry.
 *
 * <p>Each named cache is backed by a {@link LinkedHashMap} guarded by a {@link
 * ReentrantReadWriteLock}. Max-size enforcement is implemented via {@link LinkedHashMap} {@code
 * removeEldestEntry}: the oldest logical entry is evicted automatically and atomically on every
 * {@code put} that would exceed the limit — no separate eviction loop is needed.
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
   * Per-named-cache store. All map access is serialised by a {@link ReentrantReadWriteLock}:
   * multiple readers proceed in parallel; writers are exclusive.
   *
   * <p>Both {@code byString} and {@code byLong} are anonymous {@link LinkedHashMap} subclasses
   * whose {@code removeEldestEntry} overrides enforce {@code maxSize} independently. When a map
   * evicts its eldest entry it cross-removes the matching index from its sibling map. Because
   * {@link CacheEntry} has no {@code equals}/{@code hashCode} override, sibling removals use
   * identity-safe {@code Map.remove(key, value)}.
   */
  private static class CacheStore {
    final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    final Lock readLock = rwLock.readLock();
    final Lock writeLock = rwLock.writeLock();

    /** 0 = unlimited. Declared first so both anonymous classes below can read it. */
    volatile int maxSize;

    final LinkedHashMap<String, CacheEntry<Object>> byString =
        new LinkedHashMap<>() {
          @Override
          protected boolean removeEldestEntry(Map.Entry<String, CacheEntry<Object>> eldest) {
            if (CacheStore.this.maxSize > 0 && size() > CacheStore.this.maxSize) {
              Long lk = eldest.getValue().getLongKey();
              if (lk != null) {
                CacheStore.this.byLong.remove(lk, eldest.getValue());
              }
              return true;
            }
            return false;
          }
        };

    final LinkedHashMap<Long, CacheEntry<Object>> byLong =
        new LinkedHashMap<>() {
          @Override
          protected boolean removeEldestEntry(Map.Entry<Long, CacheEntry<Object>> eldest) {
            if (CacheStore.this.maxSize > 0 && size() > CacheStore.this.maxSize) {
              String sk = eldest.getValue().getStringKey();
              if (sk != null) {
                CacheStore.this.byString.remove(sk, eldest.getValue());
              }
              return true;
            }
            return false;
          }
        };
  }

  /** cacheName -> CacheStore */
  private final Map<String, CacheStore> caches = new ConcurrentHashMap<>();

  // -------------------------------------------------------------------------
  // Cache facade
  // -------------------------------------------------------------------------

  /**
   * Returns a {@link Cache} facade bound to the given name with a maximum number of live entries
   * and no default TTL. When the limit is exceeded the oldest entry is evicted (FIFO) automatically
   * via {@link LinkedHashMap} {@code removeEldestEntry}.
   *
   * @param cacheName logical name of the cache
   * @param maxSize maximum number of live entries; must be &gt; 0
   * @return a named {@link Cache} facade
   */
  public <V> Cache<V> getCache(String cacheName, int maxSize) {
    return getCache(cacheName, maxSize, null);
  }

  /**
   * Returns a {@link Cache} facade bound to the given name with a maximum number of live entries
   * and a default TTL. When the limit is exceeded the oldest entry is evicted (FIFO) automatically
   * via {@link LinkedHashMap} {@code removeEldestEntry}.
   *
   * @param cacheName logical name of the cache
   * @param maxSize maximum number of live entries; must be &gt; 0
   * @param ttl default time-to-live; {@code null} means permanent
   * @return a named {@link Cache} facade
   */
  public <V> Cache<V> getCache(String cacheName, int maxSize, Duration ttl) {
    if (maxSize < 0) {
      throw new IllegalArgumentException("maxSize must be >= 0");
    }
    if (maxSize == 0) {
      getOrCreateStore(cacheName).maxSize = maxSize;
    }
    return new Cache(cacheName, this, maxSize, ttl);
  }

  // -------------------------------------------------------------------------
  // Core operations
  // -------------------------------------------------------------------------

  /**
   * Stores {@code value} under the given keys in the named cache with the specified TTL. At least
   * one of {@code stringKey} / {@code longKey} must be non-null.
   *
   * @param cacheName logical name of the cache
   * @param stringKey string index key; may be {@code null} if {@code longKey} is provided
   * @param longKey long index key; may be {@code null} if {@code stringKey} is provided
   * @param value value to cache (must not be {@code null})
   * @param ttl how long the entry lives; {@code null} for no expiry
   */
  public void put(String cacheName, String stringKey, Long longKey, Object value, Duration ttl) {
    if (stringKey == null && longKey == null) {
      throw new IllegalArgumentException(
          "At least one key (stringKey or longKey) must be non-null");
    }
    Instant expiresAt = ttl != null ? Instant.now().plus(ttl) : null;
    CacheEntry<Object> entry = new CacheEntry<>(value, expiresAt, stringKey, longKey);
    CacheStore store = getOrCreateStore(cacheName);

    store.writeLock.lock();
    try {
      if (stringKey != null) {
        CacheEntry<Object> old = store.byString.put(stringKey, entry);
        if (old != null) {
          // Clean up stale cross-index when the long key changed
          if (old.getLongKey() != null && !old.getLongKey().equals(longKey)) {
            store.byLong.remove(old.getLongKey());
          }
        }
        // removeEldestEntry fires automatically if byString.size() > maxSize,
        // cross-removing the eldest entry's long-key index from byLong.
      }
      if (longKey != null) {
        CacheEntry<Object> old = store.byLong.put(longKey, entry);
        if (old != null) {
          // Clean up stale cross-index when the string key changed
          if (old.getStringKey() != null && !old.getStringKey().equals(stringKey)) {
            store.byString.remove(old.getStringKey());
          }
        }
        // removeEldestEntry fires automatically if byLong.size() > maxSize,
        // cross-removing the eldest entry's string-key index from byString.
      }
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
  public <V> Optional<V> get(String cacheName, String stringKey) {
    CacheStore store = caches.get(cacheName);
    if (store == null) {
      return Optional.empty();
    }
    store.readLock.lock();
    try {
      return resolveEntry(cacheName, store.byString.get(stringKey));
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
  public <V> Optional<V> get(String cacheName, Long longKey) {
    CacheStore store = caches.get(cacheName);
    if (store == null) {
      return Optional.empty();
    }
    store.readLock.lock();
    try {
      return resolveEntry(cacheName, store.byLong.get(longKey));
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
  public <V> V getOrLoad(
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
  public <V> V getOrLoad(String cacheName, String stringKey, Long longKey, Supplier<V> loader) {
    return getOrLoad(cacheName, stringKey, longKey, loader, null);
  }

  /** Returns {@code true} if a non-expired entry exists for the given string key. */
  public boolean contains(String cacheName, String stringKey) {
    return get(cacheName, stringKey).isPresent();
  }

  /** Returns {@code true} if a non-expired entry exists for the given long key. */
  public boolean contains(String cacheName, Long longKey) {
    return get(cacheName, longKey).isPresent();
  }

  // -------------------------------------------------------------------------
  // Invalidation
  // -------------------------------------------------------------------------

  /**
   * Removes the entry indexed under the given string key, including its long-key index if any.
   *
   * @return {@code true} if an entry was actually removed
   */
  public boolean invalidate(String cacheName, String stringKey) {
    CacheStore store = caches.get(cacheName);
    if (store == null) {
      return false;
    }
    store.writeLock.lock();
    try {
      CacheEntry<Object> entry = store.byString.remove(stringKey);
      if (entry == null) {
        return false;
      }
      if (entry.getLongKey() != null) {
        store.byLong.remove(entry.getLongKey());
      }
      log.debug("Cache [{}] INVALIDATE stringKey='{}'", cacheName, stringKey);
      return true;
    } finally {
      store.writeLock.unlock();
    }
  }

  /**
   * Removes the entry indexed under the given long key, including its string-key index if any.
   *
   * @return {@code true} if an entry was actually removed
   */
  public boolean invalidate(String cacheName, Long longKey) {
    CacheStore store = caches.get(cacheName);
    if (store == null) {
      return false;
    }
    store.writeLock.lock();
    try {
      CacheEntry<Object> entry = store.byLong.remove(longKey);
      if (entry == null) {
        return false;
      }
      if (entry.getStringKey() != null) {
        store.byString.remove(entry.getStringKey());
      }
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
  public boolean invalidate(String cacheName, String stringKey, Long longKey) {
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
  public void invalidateAll(String cacheName) {
    CacheStore store = caches.get(cacheName);
    if (store != null && (store.byString.size() > 0 || store.byLong.size() > 0)) {
      store.writeLock.lock();
      try {
        store.byString.clear();
        store.byLong.clear();
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
        store.byString.clear();
        store.byLong.clear();
      } finally {
        store.writeLock.unlock();
      }
    }
    log.debug("ALL caches CLEARED");
  }

  // -------------------------------------------------------------------------
  // Statistics / inspection
  // -------------------------------------------------------------------------

  /**
   * Returns the number of unique, non-expired logical entries in the named cache. An entry stored
   * under both a string and a long key counts as one.
   */
  public long size(String cacheName) {
    CacheStore store = caches.get(cacheName);
    if (store == null) {
      return 0L;
    }
    store.readLock.lock();
    try {
      // Deduplicate via identity: an entry stored under both keys counts once.
      Set<CacheEntry<Object>> unique = Collections.newSetFromMap(new IdentityHashMap<>());
      store.byString.values().stream().filter(e -> !e.isExpired()).forEach(unique::add);
      store.byLong.values().stream().filter(e -> !e.isExpired()).forEach(unique::add);
      return unique.size();
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
   * Periodically removes expired entries from all caches. Runs every 5 minutes. A single pass over
   * {@code insertionOrder} cleans both index maps atomically under the write lock.
   */
  @Scheduled(every = "5m", identity = "cache-eviction")
  void evictExpired() {
    int total = 0;
    for (Map.Entry<String, CacheStore> cacheEntry : caches.entrySet()) {
      String cacheName = cacheEntry.getKey();
      CacheStore store = cacheEntry.getValue();
      store.writeLock.lock();
      try {
        int before = store.byString.size() + store.byLong.size();
        // Pass 1 – remove expired from byString and cross-remove from byLong.
        store
            .byString
            .entrySet()
            .removeIf(
                e -> {
                  if (e.getValue().isExpired()) {
                    Long lk = e.getValue().getLongKey();
                    if (lk != null) {
                      store.byLong.remove(lk, e.getValue());
                    }
                    return true;
                  }
                  return false;
                });
        // Pass 2 – remove any remaining expired from byLong
        // (entries whose stringKey was null, or already removed in pass 1).
        store.byLong.entrySet().removeIf(e -> e.getValue().isExpired());
        int evicted = before - (store.byString.size() + store.byLong.size());
        if (evicted > 0) {
          log.debug("Cache [{}] evicted {} expired index entries", cacheName, evicted);
          total += evicted;
        }
      } finally {
        store.writeLock.unlock();
      }
    }
    if (total > 0) {
      log.debug("Cache eviction completed – {} index entries removed in total", total);
    }
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  /**
   * Resolves a map lookup: returns empty for {@code null} or expired entries. Expiry removal is
   * lazy — actual cleanup is handled by {@link #evictExpired()}.
   */
  private <V> Optional<V> resolveEntry(String cacheName, CacheEntry<Object> entry) {
    if (entry == null || entry.isExpired()) {
      if (entry != null) {
        log.trace(
            "Cache [{}] EXPIRED stringKey='{}' longKey={}",
            cacheName,
            entry.getStringKey(),
            entry.getLongKey());
      }
      return Optional.empty();
    }
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
}
