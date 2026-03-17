package org.anasoid.iptvorganizer.services.history;

/*
 * Copyright 2023-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * @author : anasoid
 * Date :   3/17/26
 */

import jakarta.enterprise.context.ApplicationScoped;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import lombok.extern.slf4j.Slf4j;
import org.anasoid.iptvorganizer.models.entity.stream.StreamType;
import org.anasoid.iptvorganizer.models.history.StreamHistoryEntry;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * In-memory, per-client stream-watch history service.
 *
 * <p>Each client has an independent, bounded {@link Deque} of {@link StreamHistoryEntry} objects
 * stored in a {@link ConcurrentHashMap}. History is <em>never persisted</em> and is lost on server
 * restart.
 *
 * <h3>Update-window merging</h3>
 *
 * <p>When {@link #recordStreamAccess} is called and the most-recent history entry for that client
 * matches the same {@code streamId} + {@code streamType}, <em>and</em> the call occurs within
 * {@code history.update.window.minutes} of that entry's {@code start} time, the entry's {@code end}
 * timestamp is updated instead of creating a new entry. This collapses rapid repeated accesses
 * (e.g. HLS segment re-fetches) into a single session.
 *
 * <h3>Bounded size</h3>
 *
 * <p>The deque is trimmed to at most {@code history.max.size} entries (default 1000) by evicting
 * the oldest entry whenever the limit is exceeded.
 *
 * <h3>Thread safety</h3>
 *
 * <p>Each {@link ClientHistory} bucket has its own {@link ReentrantLock} so concurrent accesses
 * from different clients never contend.
 */
@ApplicationScoped
@Slf4j
public class ClientHistoryService {

  /** Maximum number of history entries kept per client. */
  @ConfigProperty(name = "history.max.size", defaultValue = "1000")
  int maxHistorySize;

  /**
   * If a repeated call to the same stream occurs within this many minutes of the entry's start
   * time, its {@code end} is updated rather than a new entry being added.
   */
  @ConfigProperty(name = "history.update.window.minutes", defaultValue = "5")
  int updateWindowMinutes;

  // -------------------------------------------------------------------------
  // Internal state
  // -------------------------------------------------------------------------

  /**
   * Per-client history container. Each bucket holds its own lock and deque so contention between
   * different clients is impossible.
   */
  private static final class ClientHistory {
    final ReentrantLock lock = new ReentrantLock();

    /** Most-recent entry at the front (index 0). */
    final Deque<StreamHistoryEntry> entries = new ArrayDeque<>();
  }

  /** clientId → ClientHistory. The map itself is thread-safe; per-entry access uses the lock. */
  private final ConcurrentHashMap<Long, ClientHistory> histories = new ConcurrentHashMap<>();

  // -------------------------------------------------------------------------
  // Public API
  // -------------------------------------------------------------------------

  /**
   * Records a stream access for the given client.
   *
   * <p>If the most-recent history entry for this client references the same {@code streamId} and
   * {@code streamType}, and its {@code start} is within the configured update window, the entry's
   * {@code end} time is set to {@code now} and no new entry is created. Otherwise a new entry is
   * prepended and the deque is trimmed to {@link #maxHistorySize}.
   *
   * @param clientId client whose history is updated
   * @param streamId external stream identifier (as in the request URL)
   * @param streamType LIVE, VOD, or SERIES
   */
  public void recordStreamAccess(long clientId, String streamId, StreamType streamType) {

    ClientHistory bucket = histories.computeIfAbsent(clientId, k -> new ClientHistory());
    LocalDateTime now = LocalDateTime.now();

    bucket.lock.lock();
    try {
      if (!bucket.entries.isEmpty()) {
        StreamHistoryEntry last = bucket.entries.peekFirst();
        if (isSameStream(last, streamId, streamType)) {
          Duration elapsed = Duration.between(last.getStart(), now);
          if (elapsed.toMinutes() < updateWindowMinutes) {
            last.setEnd(now);
            log.trace(
                "History end updated: client={} stream={} type={}", clientId, streamId, streamType);
            return;
          }
        }
      }

      bucket.entries.addFirst(new StreamHistoryEntry(streamId, streamType, now, null));
      log.trace("History entry added: client={} stream={} type={}", clientId, streamId, streamType);

      // Trim oldest entries beyond the configured limit
      while (bucket.entries.size() > maxHistorySize) {
        bucket.entries.removeLast();
      }
    } finally {
      bucket.lock.unlock();
    }
  }

  /**
   * Returns an immutable snapshot of the history for the given client, ordered most-recent-first.
   *
   * @param clientId client whose history is requested
   * @return unmodifiable list; empty if no history exists
   */
  public List<StreamHistoryEntry> getHistory(long clientId) {
    ClientHistory bucket = histories.get(clientId);
    if (bucket == null) {
      return Collections.emptyList();
    }
    bucket.lock.lock();
    try {
      return Collections.unmodifiableList(new ArrayList<>(bucket.entries));
    } finally {
      bucket.lock.unlock();
    }
  }

  /**
   * Returns the number of history entries for the given client.
   *
   * @param clientId client to query
   * @return entry count, 0 if no history exists
   */
  public int getHistorySize(long clientId) {
    ClientHistory bucket = histories.get(clientId);
    if (bucket == null) {
      return 0;
    }
    bucket.lock.lock();
    try {
      return bucket.entries.size();
    } finally {
      bucket.lock.unlock();
    }
  }

  /**
   * Clears all history entries for the specified client.
   *
   * @param clientId client whose history is cleared
   */
  public void clearHistory(long clientId) {
    histories.remove(clientId);
    log.debug("History cleared for client={}", clientId);
  }

  /** Clears history entries for every client (e.g. on demand admin action). */
  public void clearAllHistory() {
    histories.clear();
    log.debug("All client histories cleared");
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private boolean isSameStream(StreamHistoryEntry entry, String streamId, StreamType streamType) {
    return entry.getStreamId().equals(streamId) && entry.getStreamType() == streamType;
  }
}
