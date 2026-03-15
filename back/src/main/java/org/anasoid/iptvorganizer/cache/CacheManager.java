package org.anasoid.iptvorganizer.cache;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

/**
 * Application-scoped, thread-safe cache manager that supports multiple named caches and per-entry
 * TTL (time-to-live).
 *
 * <p>Each cache entry can be indexed by a {@link String} key, a {@link Long} key, or both. At least
 * one key must be provided when storing an entry.
 *
 * <p>Usage examples:
 *
 * <pre>{@code
 * @Inject CacheManager cacheManager;
 *
 * // Store with both keys and a 5-minute TTL
 * cacheManager.put("sources", "src-abc", 42L, value, Duration.ofMinutes(5));
 *
 * // Store with string key only
 * cacheManager.put("sources", "src-abc", null, value, Duration.ofMinutes(5));
 *
 * // Store with long key only
 * cacheManager.put("sources", null, 42L, value, Duration.ofMinutes(5));
 *
 * // Retrieve by string key
 * Optional<MyType> byString = cacheManager.get("sources", "src-abc", MyType.class);
 *
 * // Retrieve by long key
 * Optional<MyType> byLong = cacheManager.get("sources", 42L, MyType.class);
 *
 * // Cache-aside with dual keys
 * MyType result = cacheManager.getOrLoad("sources", "src-abc", 42L, MyType.class,
 *                     () -> loadFromDb(), Duration.ofMinutes(5));
 * }</pre>
 */
@ApplicationScoped
@Slf4j
public class CacheManager {

  /** Per-named-cache pair of indexes. */
  private static class CacheStore {
    final ConcurrentHashMap<String, CacheEntry<Object>> byString = new ConcurrentHashMap<>();
    final ConcurrentHashMap<Long, CacheEntry<Object>> byLong = new ConcurrentHashMap<>();
    /** FIFO insertion-order tracking used for max-size eviction. */
    final Deque<CacheEntry<Object>> insertionOrder = new ConcurrentLinkedDeque<>();
    /** Maximum number of live entries; 0 means unlimited. */
    volatile int maxSize;
  }

  /** cacheName -> CacheStore */
  private final Map<String, CacheStore> caches = new ConcurrentHashMap<>();

  // -------------------------------------------------------------------------
  // Cache facade
  // -------------------------------------------------------------------------


  /**
   * Returns a {@link Cache} facade bound to the given name with a maximum number of live entries.
   * When the limit is exceeded the oldest entry is evicted (FIFO).
   *
   * @param cacheName logical name of the cache
   * @param maxSize   maximum number of live entries; must be &gt; 0
   * @return a named {@link Cache} facade
   */
  public Cache getCache(String cacheName, int maxSize) {
    if (maxSize <= 0) {
      throw new IllegalArgumentException("maxSize must be > 0");
    }
    getOrCreateStore(cacheName).maxSize = maxSize;
    return new Cache(cacheName, this, maxSize);
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
    if (value == null) {
      throw new IllegalArgumentException("Cached value must not be null");
    }
    Instant expiresAt = (ttl != null) ? Instant.now().plus(ttl) : null;
    CacheEntry<Object> entry = new CacheEntry<>(value, expiresAt, stringKey, longKey);
    CacheStore store = getOrCreateStore(cacheName);

    if (stringKey != null) {
      CacheEntry<Object> old = store.byString.put(stringKey, entry);
      if (old != null) {
        store.insertionOrder.remove(old);
        // Clean up the stale long-key index if the key changed
        if (old.getLongKey() != null && !old.getLongKey().equals(longKey)) {
          store.byLong.remove(old.getLongKey(), old);
        }
      }
    }
    if (longKey != null) {
      CacheEntry<Object> old = store.byLong.put(longKey, entry);
      if (old != null) {
        store.insertionOrder.remove(old);
        // Clean up the stale string-key index if the key changed
        if (old.getStringKey() != null && !old.getStringKey().equals(stringKey)) {
          store.byString.remove(old.getStringKey(), old);
        }
      }
    }
    store.insertionOrder.addLast(entry);

    // Enforce max size via FIFO eviction
    if (store.maxSize > 0) {
      while (store.insertionOrder.size() > store.maxSize) {
        evictOldest(cacheName, store);
      }
    }
    log.trace("Cache [{}] PUT stringKey='{}' longKey={} ttl={}", cacheName, stringKey, longKey, ttl);
  }

  /**
   * Stores {@code value} permanently (no expiry) under the given keys. At least one of {@code
   * stringKey} / {@code longKey} must be non-null.
   *
   * @param cacheName logical name of the cache
   * @param stringKey string index key; may be {@code null} if {@code longKey} is provided
   * @param longKey long index key; may be {@code null} if {@code stringKey} is provided
   * @param value value to cache
   */
  public void put(String cacheName, String stringKey, Long longKey, Object value) {
    put(cacheName, stringKey, longKey, value, null);
  }

  /**
   * Returns the cached value by string key if present and not expired.
   *
   * @param cacheName logical name of the cache
   * @param stringKey string index key
   * @param type expected type of the value
   * @return {@link Optional} containing the value, or empty if absent / expired
   */
  public <V> Optional<V> get(String cacheName, String stringKey, Class<V> type) {
    CacheStore store = caches.get(cacheName);
    if (store == null) {
      return Optional.empty();
    }
    CacheEntry<Object> entry = store.byString.get(stringKey);
    return resolveEntry(cacheName, entry, store, type);
  }

  /**
   * Returns the cached value by long key if present and not expired.
   *
   * @param cacheName logical name of the cache
   * @param longKey long index key
   * @param type expected type of the value
   * @return {@link Optional} containing the value, or empty if absent / expired
   */
  public <V> Optional<V> get(String cacheName, Long longKey, Class<V> type) {
    CacheStore store = caches.get(cacheName);
    if (store == null) {
      return Optional.empty();
    }
    CacheEntry<Object> entry = store.byLong.get(longKey);
    return resolveEntry(cacheName, entry, store, type);
  }

  /**
   * Returns the cached value if present and not expired, otherwise calls {@code loader}, stores the
   * result with the given TTL under both keys, and returns it.
   *
   * <p>Lookup order: string key first (if non-null), then long key (if non-null).
   *
   * @param cacheName logical name of the cache
   * @param stringKey string index key; may be {@code null} if {@code longKey} is provided
   * @param longKey long index key; may be {@code null} if {@code stringKey} is provided
   * @param type expected type of the value
   * @param loader supplier invoked on a cache miss
   * @param ttl TTL for the loaded value; {@code null} for no expiry
   * @return cached or freshly loaded value
   */
  public <V> V getOrLoad(
      String cacheName,
      String stringKey,
      Long longKey,
      Class<V> type,
      Supplier<V> loader,
      Duration ttl) {
    if (stringKey != null) {
      Optional<V> cached = get(cacheName, stringKey, type);
      if (cached.isPresent()) {
        return cached.get();
      }
    }
    if (longKey != null) {
      Optional<V> cached = get(cacheName, longKey, type);
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

  /**
   * Returns the cached value if present and not expired, otherwise calls {@code loader} and stores
   * the result permanently under both keys.
   *
   * @param cacheName logical name of the cache
   * @param stringKey string index key; may be {@code null} if {@code longKey} is provided
   * @param longKey long index key; may be {@code null} if {@code stringKey} is provided
   * @param type expected type of the value
   * @param loader supplier invoked on a cache miss
   * @return cached or freshly loaded value
   */
  public <V> V getOrLoad(
      String cacheName, String stringKey, Long longKey, Class<V> type, Supplier<V> loader) {
    return getOrLoad(cacheName, stringKey, longKey, type, loader, null);
  }

  /**
   * Checks whether a non-expired entry exists for the given string key.
   *
   * @param cacheName logical name of the cache
   * @param stringKey string index key
   * @return {@code true} if a valid (non-expired) entry exists
   */
  public boolean contains(String cacheName, String stringKey) {
    return get(cacheName, stringKey, Object.class).isPresent();
  }

  /**
   * Checks whether a non-expired entry exists for the given long key.
   *
   * @param cacheName logical name of the cache
   * @param longKey long index key
   * @return {@code true} if a valid (non-expired) entry exists
   */
  public boolean contains(String cacheName, Long longKey) {
    return get(cacheName, longKey, Object.class).isPresent();
  }

  // -------------------------------------------------------------------------
  // Invalidation
  // -------------------------------------------------------------------------

  /**
   * Removes the entry indexed under the given string key, including its long-key index if any.
   *
   * @param cacheName logical name of the cache
   * @param stringKey string index key to remove
   * @return {@code true} if an entry was actually removed
   */
  public boolean invalidate(String cacheName, String stringKey) {
    CacheStore store = caches.get(cacheName);
    if (store == null) {
      return false;
    }
    CacheEntry<Object> entry = store.byString.remove(stringKey);
    if (entry == null) {
      return false;
    }
    if (entry.getLongKey() != null) {
      store.byLong.remove(entry.getLongKey());
    }
    if (entry.getStringKey() != null) {
      store.byString.remove(entry.getStringKey());
    }
    log.debug("Cache [{}] INVALIDATE stringKey='{}'", cacheName, stringKey);
    return true;
  }

  /**
   * Removes the entry indexed under the given long key, including its string-key index if any.
   *
   * @param cacheName logical name of the cache
   * @param longKey long index key to remove
   * @return {@code true} if an entry was actually removed
   */
  public boolean invalidate(String cacheName, Long longKey) {
    CacheStore store = caches.get(cacheName);
    if (store == null) {
      return false;
    }
    CacheEntry<Object> entry = store.byLong.remove(longKey);
    if (entry == null) {
      return false;
    }
    if (entry.getLongKey() != null) {
      store.byLong.remove(entry.getLongKey());
    }
    if (entry.getStringKey() != null) {
      store.byString.remove(entry.getStringKey());
    }
    log.debug("Cache [{}] INVALIDATE longKey={}", cacheName, longKey);
    return true;
  }

  /**
   * Removes the entry matching either the given string key or long key.
   * At least one key must be non-null. Lookup is attempted by {@code stringKey} first, then by
   * {@code longKey}. Whichever index locates the entry, <em>both</em> indexes are removed.
   *
   * @param cacheName logical name of the cache
   * @param stringKey string index key; may be {@code null} if {@code longKey} is provided
   * @param longKey   long index key; may be {@code null} if {@code stringKey} is provided
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

  /**
   * Clears all entries in the named cache.
   *
   * @param cacheName logical name of the cache
   */
  public void invalidateAll(String cacheName) {
    CacheStore store = caches.get(cacheName);
    if (store != null) {
      store.byString.clear();
      store.byLong.clear();
      store.insertionOrder.clear();
      log.debug("Cache [{}] CLEAR ALL", cacheName);
    }
  }

  /** Clears every named cache managed by this instance. */
  public void invalidateAll() {
    caches
        .values()
        .forEach(
            store -> {
              store.byString.clear();
              store.byLong.clear();
              store.insertionOrder.clear();
            });
    log.debug("ALL caches CLEARED");
  }

  // -------------------------------------------------------------------------
  // Statistics / inspection
  // -------------------------------------------------------------------------

  /**
   * Returns the number of unique, non-expired logical entries in the named cache. An entry stored
   * under both a string and a long key counts as one.
   *
   * @param cacheName logical name of the cache
   * @return live entry count
   */
  public long size(String cacheName) {
    CacheStore store = caches.get(cacheName);
    if (store == null) {
      return 0L;
    }
    // Use identity set to deduplicate entries shared across both indexes.
    Set<CacheEntry<Object>> unique = Collections.newSetFromMap(new IdentityHashMap<>());
    store.byString.values().stream().filter(e -> !e.isExpired()).forEach(unique::add);
    store.byLong.values().stream().filter(e -> !e.isExpired()).forEach(unique::add);
    return unique.size();
  }

  /**
   * Returns an unmodifiable view of the known cache names.
   *
   * @return set of cache names
   */
  public Set<String> cacheNames() {
    return Collections.unmodifiableSet(caches.keySet());
  }

  // -------------------------------------------------------------------------
  // Scheduled eviction
  // -------------------------------------------------------------------------

  /**
   * Periodically removes expired entries from all caches. Runs every 5 minutes by default. When an
   * entry is expired it is removed from both indexes simultaneously.
   */
  @Scheduled(every = "5m", identity = "cache-eviction")
  void evictExpired() {
    int total = 0;
    for (Map.Entry<String, CacheStore> cacheEntry : caches.entrySet()) {
      String cacheName = cacheEntry.getKey();
      CacheStore store = cacheEntry.getValue();
      int before = store.byString.size() + store.byLong.size();
      // Remove from byString; also remove the paired longKey index entry.
      store
          .byString
          .entrySet()
          .removeIf(
              e -> {
                if (e.getValue().isExpired()) {
                  Long lk = e.getValue().getLongKey();
                  if (lk != null) {
                    store.byLong.remove(lk);
                  }
                  return true;
                }
                return false;
              });
      // Remove any remaining expired entries in byLong
      // (entries with no stringKey, or whose stringKey was removed independently).
      store.byLong.entrySet().removeIf(e -> e.getValue().isExpired());
      // Keep insertionOrder deque in sync with the maps
      store.insertionOrder.removeIf(CacheEntry::isExpired);
      int evicted = before - (store.byString.size() + store.byLong.size());
      if (evicted > 0) {
        log.debug("Cache [{}] evicted {} expired index entries", cacheName, evicted);
        total += evicted;
      }
    }
    if (total > 0) {
      log.debug("Cache eviction completed – {} index entries removed in total", total);
    }
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private <V> Optional<V> resolveEntry(
      String cacheName, CacheEntry<Object> entry, CacheStore store, Class<V> type) {
    if (entry == null) {
      return Optional.empty();
    }
    if (entry.isExpired()) {
      removeFromStore(store, entry);
      log.trace("Cache [{}] EXPIRED stringKey='{}' longKey={}", cacheName, entry.getStringKey(), entry.getLongKey());
      return Optional.empty();
    }
    log.trace("Cache [{}] HIT stringKey='{}' longKey={}", cacheName, entry.getStringKey(), entry.getLongKey());
    return Optional.of(type.cast(entry.getValue()));
  }

  private void removeFromStore(CacheStore store, CacheEntry<Object> entry) {
    if (entry.getStringKey() != null) {
      store.byString.remove(entry.getStringKey());
    }
    if (entry.getLongKey() != null) {
      store.byLong.remove(entry.getLongKey());
    }
  }

  private CacheStore getOrCreateStore(String cacheName) {
    return caches.computeIfAbsent(cacheName, k -> new CacheStore());
  }

  /**
   * Evicts the oldest live entry from the store (FIFO). Stale deque entries left by prior
   * overwrites or expiry are skipped automatically.
   */
  private void evictOldest(String cacheName, CacheStore store) {
    CacheEntry<Object> oldest;
    while ((oldest = store.insertionOrder.pollFirst()) != null) {
      // Expired entries: remove from maps if still present, then skip (don't count as eviction)
      if (oldest.isExpired()) {
        if (oldest.getStringKey() != null) {
          store.byString.remove(oldest.getStringKey(), oldest);
        }
        if (oldest.getLongKey() != null) {
          store.byLong.remove(oldest.getLongKey(), oldest);
        }
        continue;
      }
      // Attempt atomic removal using identity comparison (remove(k,v) uses equals = identity here)
      boolean removed = false;
      if (oldest.getStringKey() != null) {
        removed = store.byString.remove(oldest.getStringKey(), oldest);
        if (removed && oldest.getLongKey() != null) {
          store.byLong.remove(oldest.getLongKey());
        }
      }
      if (!removed && oldest.getLongKey() != null) {
        removed = store.byLong.remove(oldest.getLongKey(), oldest);
        if (removed && oldest.getStringKey() != null) {
          store.byString.remove(oldest.getStringKey());
        }
      }
      if (removed) {
        log.debug("Cache [{}] evicted oldest entry (maxSize={})", cacheName, store.maxSize);
        return;
      }
      // Entry was already replaced by a newer put for the same key; try next candidate
    }
  }
}
