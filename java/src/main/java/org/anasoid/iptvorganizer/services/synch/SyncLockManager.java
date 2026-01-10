package org.anasoid.iptvorganizer.services.synch;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;
import lombok.Builder;
import lombok.Data;

@ApplicationScoped
public class SyncLockManager {

  private static final Logger LOGGER = Logger.getLogger(SyncLockManager.class.getName());

  // Lock storage: sourceId -> ReentrantLock
  private final ConcurrentHashMap<Long, ReentrantLock> locks = new ConcurrentHashMap<>();

  // Active sync metadata: sourceId -> SyncMetadata
  private final ConcurrentHashMap<Long, SyncMetadata> activeSyncs = new ConcurrentHashMap<>();

  /** Metadata for tracking active sync operations */
  @Data
  @Builder
  public static class SyncMetadata {
    private Long sourceId;
    private String syncType;
    private LocalDateTime startTime;
    private String threadName;
  }

  /**
   * Try to acquire lock for a source (non-blocking) Returns true if lock acquired, false if already
   * locked
   */
  public boolean tryAcquireLock(Long sourceId, String syncType) {
    ReentrantLock lock = locks.computeIfAbsent(sourceId, k -> new ReentrantLock());

    if (lock.tryLock()) {
      // Successfully acquired lock - record metadata
      SyncMetadata metadata =
          SyncMetadata.builder()
              .sourceId(sourceId)
              .syncType(syncType)
              .startTime(LocalDateTime.now())
              .threadName(Thread.currentThread().getName())
              .build();

      activeSyncs.put(sourceId, metadata);
      LOGGER.info("Lock acquired for source: " + sourceId + ", sync type: " + syncType);
      return true;
    }

    LOGGER.info("Failed to acquire lock for source: " + sourceId + " (already locked)");
    return false;
  }

  /** Release lock for a source */
  public void releaseLock(Long sourceId) {
    ReentrantLock lock = locks.get(sourceId);

    if (lock != null && lock.isHeldByCurrentThread()) {
      activeSyncs.remove(sourceId);
      lock.unlock();
      LOGGER.info("Lock released for source: " + sourceId);

      // Clean up lock if no threads are waiting
      if (!lock.hasQueuedThreads()) {
        locks.remove(sourceId);
      }
    } else {
      LOGGER.warning(
          "Attempted to release lock for source "
              + sourceId
              + " but lock not held by current thread");
    }
  }

  /** Check if a source is currently locked */
  public boolean isLocked(Long sourceId) {
    ReentrantLock lock = locks.get(sourceId);
    return lock != null && lock.isLocked();
  }

  /** Get all active sync operations */
  public List<SyncMetadata> getActiveSyncs() {
    return new ArrayList<>(activeSyncs.values());
  }

  /** Get metadata for a specific source sync */
  public Optional<SyncMetadata> getSyncMetadata(Long sourceId) {
    return Optional.ofNullable(activeSyncs.get(sourceId));
  }

  /** Cleanup on shutdown (called by @PreDestroy) */
  @PreDestroy
  public void shutdown() {
    LOGGER.info("SyncLockManager shutting down, releasing all locks...");

    // Release all held locks
    locks.forEach(
        (sourceId, lock) -> {
          if (lock.isLocked()) {
            LOGGER.warning("Force releasing lock for source: " + sourceId + " on shutdown");
            try {
              if (lock.isHeldByCurrentThread()) {
                lock.unlock();
              }
            } catch (Exception e) {
              LOGGER.severe("Error releasing lock on shutdown: " + e.getMessage());
            }
          }
        });

    locks.clear();
    activeSyncs.clear();
  }
}
