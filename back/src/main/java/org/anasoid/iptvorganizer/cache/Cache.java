package org.anasoid.iptvorganizer.cache;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
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
public class Cache<V> {

  /** Logical name of this cache. */
  @Getter private final String name;

  private final CacheManager manager;

  /** Maximum number of live entries; 0 means cache is disabled (all operations are skipped). */
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
  // Enabled check
  // -------------------------------------------------------------------------

  /**
   * Returns {@code true} if this cache is active. When {@code maxSize == 0} the cache is disabled
   * and all operations are skipped.
   */
  public boolean isEnabled() {
    return maxSize != 0;
  }

  // -------------------------------------------------------------------------
  // Put
  // -------------------------------------------------------------------------

  /**
   * Stores {@code value} under the given keys using the cache's default TTL (permanent if none). At
   * least one of {@code stringKey} / {@code longKey} must be non-null.
   *
   * <p>No-op when the cache is disabled ({@code maxSize == 0}).
   */
  public void put(String stringKey, Long longKey, Object value) {
    if (!isEnabled()) return;
    manager.put(name, stringKey, longKey, value, ttl);
  }

  /**
   * Stores {@code value} under the given keys using the cache's default TTL (permanent if none). At
   * least one of {@code stringKey} / {@code longKey} must be non-null.
   *
   * <p>No-op when the cache is disabled ({@code maxSize == 0}).
   */
  public void putNull(String stringKey,  Object value) {
    if (!isEnabled()) return;
    manager.put(name, stringKey, -Math.abs(ThreadLocalRandom.current().nextLong()), value, ttl);
  }

  // -------------------------------------------------------------------------
  // Get
  // -------------------------------------------------------------------------

  /**
   * Returns the cached value by string key if present and not expired, or {@link Optional#empty()}
   * when disabled.
   */
  public <V> Optional<V> get(String stringKey) {
    if (!isEnabled()) return Optional.empty();
    return manager.get(name, stringKey);
  }

  /**
   * Returns the cached value by long key if present and not expired, or {@link Optional#empty()}
   * when disabled.
   */
  public <V> Optional<V> get(Long longKey) {
    if (!isEnabled()) return Optional.empty();
    return manager.get(name, longKey);
  }

  // -------------------------------------------------------------------------
  // Get-or-load
  // -------------------------------------------------------------------------

  /**
   * Returns the cached value if present and not expired, otherwise invokes {@code loader}, stores
   * the result using the cache's default TTL (permanent if none), and returns it.
   *
   * <p>When the cache is disabled ({@code maxSize == 0}), {@code loader} is always called and the
   * result is never stored.
   */
  public <V> V getOrLoad(String stringKey, Long longKey, Supplier<V> loader) {
    if (!isEnabled()) return loader.get();
    return manager.getOrLoad(name, stringKey, longKey, loader, ttl);
  }

  // -------------------------------------------------------------------------
  // Contains
  // -------------------------------------------------------------------------

  /**
   * Returns {@code true} if a non-expired entry exists for the given string key, or {@code false}
   * when disabled.
   */
  public boolean contains(String stringKey) {
    if (!isEnabled()) return false;
    return manager.contains(name, stringKey);
  }

  /**
   * Returns {@code true} if a non-expired entry exists for the given long key, or {@code false}
   * when disabled.
   */
  public boolean contains(Long longKey) {
    if (!isEnabled()) return false;
    return manager.contains(name, longKey);
  }

  // -------------------------------------------------------------------------
  // Invalidation
  // -------------------------------------------------------------------------

  /**
   * Removes the entry indexed under the given string key, including its long-key index if any.
   *
   * @return {@code true} if an entry was actually removed, always {@code false} when disabled
   */
  public boolean invalidate(String stringKey) {
    if (!isEnabled()) return false;
    return manager.invalidate(name, stringKey);
  }

  /**
   * Removes the entry indexed under the given long key, including its string-key index if any.
   *
   * @return {@code true} if an entry was actually removed, always {@code false} when disabled
   */
  public boolean invalidate(Long longKey) {
    if (!isEnabled()) return false;
    return manager.invalidate(name, longKey);
  }

  /**
   * Removes the entry matching either key. At least one of {@code stringKey} / {@code longKey} must
   * be non-null.
   *
   * @return {@code true} if an entry was actually removed, always {@code false} when disabled
   */
  public boolean invalidate(String stringKey, Long longKey) {
    if (!isEnabled()) return false;
    return manager.invalidate(name, stringKey, longKey);
  }

  /** Clears all entries in this cache. No-op when the cache is disabled. */
  public void invalidateAll() {
    if (!isEnabled()) return;
    manager.invalidateAll(name);
  }

  // -------------------------------------------------------------------------
  // Statistics
  // -------------------------------------------------------------------------

  /**
   * Returns the number of unique, non-expired logical entries in this cache. An entry stored under
   * both a string and a long key counts as one. Returns {@code 0} when disabled.
   */
  public long size() {
    if (!isEnabled()) return 0;
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
        + (maxSize == 0 ? "disabled" : maxSize)
        + ", ttl="
        + (ttl == null ? "permanent" : ttl)
        + '}';
  }

  /** Returns all known cache names managed by the underlying {@link CacheManager}. */
  public Set<String> allCacheNames() {
    return manager.cacheNames();
  }
}
