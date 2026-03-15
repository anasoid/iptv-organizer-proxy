package org.anasoid.iptvorganizer.cache;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import lombok.Getter;

/**
 * Named cache facade that delegates to {@link CacheManager} without requiring the caller to pass a
 * cache name on every operation.
 *
 * <pre>{@code
 * @Inject CacheManager cacheManager;
 *
 * Cache sources = cacheManager.getCache("sources");
 * sources.put("src-abc", 42L, value, Duration.ofMinutes(5));
 * Optional<MyType> hit = sources.get("src-abc", MyType.class);
 * }</pre>
 */
public class Cache {

  /** Logical name of this cache. */
  @Getter private final String name;

  private final CacheManager manager;

  /** Maximum number of live entries; 0 means unlimited. */
  @Getter private final int maxSize;

  /**
   * Default TTL applied to every {@code put} and {@code getOrLoad} call that does not supply an
   * explicit duration. {@code null} means entries are stored permanently by default.
   */
  @Getter private final Duration ttl;

  Cache(String name, CacheManager manager, int maxSize, Duration ttl) {
    this.name = name;
    this.manager = manager;
    this.maxSize = maxSize;
    this.ttl = ttl;
  }

  // -------------------------------------------------------------------------
  // Put
  // -------------------------------------------------------------------------

  /**
   * Stores {@code value} under the given keys using the cache's default TTL (permanent if none). At
   * least one of {@code stringKey} / {@code longKey} must be non-null.
   */
  public void put(String stringKey, Long longKey, Object value) {
    manager.put(name, stringKey, longKey, value, ttl);
  }

  // -------------------------------------------------------------------------
  // Get
  // -------------------------------------------------------------------------

  /** Returns the cached value by string key if present and not expired. */
  public <V> Optional<V> get(String stringKey, Class<V> type) {
    return manager.get(name, stringKey, type);
  }

  /** Returns the cached value by long key if present and not expired. */
  public <V> Optional<V> get(Long longKey, Class<V> type) {
    return manager.get(name, longKey, type);
  }

  // -------------------------------------------------------------------------
  // Get-or-load
  // -------------------------------------------------------------------------

  /**
   * Returns the cached value if present and not expired, otherwise invokes {@code loader}, stores
   * the result using the cache's default TTL (permanent if none), and returns it.
   */
  public <V> V getOrLoad(String stringKey, Long longKey, Class<V> type, Supplier<V> loader) {
    return manager.getOrLoad(name, stringKey, longKey, type, loader, ttl);
  }

  // -------------------------------------------------------------------------
  // Contains
  // -------------------------------------------------------------------------

  /** Returns {@code true} if a non-expired entry exists for the given string key. */
  public boolean contains(String stringKey) {
    return manager.contains(name, stringKey);
  }

  /** Returns {@code true} if a non-expired entry exists for the given long key. */
  public boolean contains(Long longKey) {
    return manager.contains(name, longKey);
  }

  // -------------------------------------------------------------------------
  // Invalidation
  // -------------------------------------------------------------------------

  /**
   * Removes the entry indexed under the given string key, including its long-key index if any.
   *
   * @return {@code true} if an entry was actually removed
   */
  public boolean invalidate(String stringKey) {
    return manager.invalidate(name, stringKey);
  }

  /**
   * Removes the entry indexed under the given long key, including its string-key index if any.
   *
   * @return {@code true} if an entry was actually removed
   */
  public boolean invalidate(Long longKey) {
    return manager.invalidate(name, longKey);
  }

  /**
   * Removes the entry matching either key. At least one of {@code stringKey} / {@code longKey} must
   * be non-null.
   *
   * @return {@code true} if an entry was actually removed
   */
  public boolean invalidate(String stringKey, Long longKey) {
    return manager.invalidate(name, stringKey, longKey);
  }

  /** Clears all entries in this cache. */
  public void invalidateAll() {
    manager.invalidateAll(name);
  }

  // -------------------------------------------------------------------------
  // Statistics
  // -------------------------------------------------------------------------

  /**
   * Returns the number of unique, non-expired logical entries in this cache. An entry stored under
   * both a string and a long key counts as one.
   */
  public long size() {
    return manager.size(name);
  }

  // -------------------------------------------------------------------------
  // Object
  // -------------------------------------------------------------------------

  @Override
  public String toString() {
    return "Cache{name='"
        + name
        + "', size="
        + size()
        + ", maxSize="
        + (maxSize == 0 ? "unlimited" : maxSize)
        + ", ttl="
        + (ttl == null ? "permanent" : ttl)
        + '}';
  }

  /** Returns all known cache names managed by the underlying {@link CacheManager}. */
  public Set<String> allCacheNames() {
    return manager.cacheNames();
  }
}
