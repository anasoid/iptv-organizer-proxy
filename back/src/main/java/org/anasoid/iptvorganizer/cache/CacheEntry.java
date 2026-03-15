package org.anasoid.iptvorganizer.cache;

import java.time.Instant;
import lombok.Getter;

/**
 * Holds a single cached value together with its optional expiry timestamp and the keys under which
 * it is indexed (one String key, one Long key – at least one must be non-null).
 *
 * @param <V> type of the cached value
 */
@Getter
public class CacheEntry<V> {

  private final V value;
  /** Absolute instant at which this entry expires, or {@code null} for no expiry. */
  private final Instant expiresAt;
  /** String index key – may be {@code null} if the entry was stored with a Long key only. */
  private final String stringKey;
  /** Long index key – may be {@code null} if the entry was stored with a String key only. */
  private final Long longKey;

  public CacheEntry(V value, Instant expiresAt, String stringKey, Long longKey) {
    this.value = value;
    this.expiresAt = expiresAt;
    this.stringKey = stringKey;
    this.longKey = longKey;
  }

  /** Returns {@code true} if this entry has passed its expiry time. */
  public boolean isExpired() {
    return expiresAt != null && Instant.now().isAfter(expiresAt);
  }

  /** Returns {@code true} if this entry never expires. */
  public boolean isPermanent() {
    return expiresAt == null;
  }
}
