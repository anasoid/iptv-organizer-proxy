package org.anasoid.iptvorganizer.services.synch;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import org.anasoid.iptvorganizer.models.entity.Source;
import org.anasoid.iptvorganizer.models.entity.SyncLog;
import org.anasoid.iptvorganizer.models.entity.SyncLog.SyncLogStatus;
import org.anasoid.iptvorganizer.repositories.synch.SourceRepository;
import org.anasoid.iptvorganizer.repositories.synch.SyncLogRepository;
import org.anasoid.iptvorganizer.services.synch.synchronizer.LiveSynchronizer;
import org.anasoid.iptvorganizer.services.synch.synchronizer.SeriesSynchronizer;
import org.anasoid.iptvorganizer.services.synch.synchronizer.VodSynchronizer;

/**
 * Background sync service using Quarkus Scheduler Syncs live streams, VOD, series, and categories
 * from Xtream API
 */
@ApplicationScoped
public class SyncManager {

  private static final Logger LOGGER = Logger.getLogger(SyncManager.class.getName());

  @Inject SourceRepository sourceRepository;

  @Inject SyncLogRepository syncLogRepository;

  @Inject LiveSynchronizer liveSynchronizer;
  @Inject VodSynchronizer vodSynchronizer;
  @Inject SeriesSynchronizer seriesSynchronizer;

  @Inject SyncLockManager syncLockManager;

  public void scheduledSync() {
    LOGGER.info("Starting scheduled sync check");

    try {
      List<Source> sources = sourceRepository.findSourcesNeedingSync();
      int processed = 0;

      for (Source source : sources) {
        try {
          syncSource(source);
          processed++;
        } catch (Exception e) {
          LOGGER.severe("Failed to sync source " + source.getId() + ": " + e.getMessage());
        }
      }

      LOGGER.info("Scheduled sync completed: " + processed + " sources processed");
    } catch (Exception e) {
      LOGGER.severe("Scheduled sync failed: " + e.getMessage());
    }
  }

  /** Sync a single source with concurrent sync prevention */
  @Transactional
  protected void syncSource(Source source) {
    // Try to acquire lock first
    boolean lockAcquired = syncLockManager.tryAcquireLock(source.getId(), "full");
    if (!lockAcquired) {
      LOGGER.info("Source " + source.getId() + " is already being synced, skipping");
      return;
    }

    LocalDateTime syncStartTime = LocalDateTime.now();
    SyncLog syncLog =
        SyncLog.builder()
            .sourceId(source.getId())
            .syncType("full")
            .startedAt(syncStartTime)
            .status(SyncLogStatus.RUNNING)
            .itemsAdded(0)
            .itemsUpdated(0)
            .itemsDeleted(0)
            .build();

    try {
      Long logId = syncLogRepository.insert(syncLog);
      syncLog.setId(logId);
      source.setLastSync(syncStartTime);

      performFullSync(source, syncLog, syncStartTime);
    } catch (Exception e) {
      LOGGER.severe("Error during sync for source " + source.getId() + ": " + e.getMessage());
      throw e;
    } finally {
      syncLockManager.releaseLock(source.getId());
    }
  }

  /** Perform full sync of a source: categories, live streams, VOD, series */
  private void performFullSync(Source source, SyncLog syncLog, LocalDateTime syncStartTime) {
    try {
      // Sync categories for all types (sequential)
      source = liveSynchronizer.syncCategories(source, syncLog);
      source = seriesSynchronizer.syncCategories(source, syncLog);
      source = vodSynchronizer.syncCategories(source, syncLog);

      // Sync streams for all types (sequential)
      source = liveSynchronizer.syncStreams(source, syncLog);
      source = vodSynchronizer.syncStreams(source, syncLog);
      source = seriesSynchronizer.syncStreams(source, syncLog);

      // Finalize with success
      finalizeSyncLog(source, syncLog, syncStartTime, null);
    } catch (Exception failure) {
      // Finalize with error
      finalizeSyncLog(source, syncLog, syncStartTime, failure);
      throw failure;
    }
  }

  /** Finalize sync log and update source */
  private void finalizeSyncLog(
      Source source, SyncLog syncLog, LocalDateTime syncStartTime, Throwable error) {
    LocalDateTime syncEndTime = LocalDateTime.now();

    if (error != null) {
      syncLog.setStatus(SyncLogStatus.FAILED);
      syncLog.setErrorMessage(error.getMessage());
      LOGGER.severe(
          "Sync failed for source "
              + source.getId()
              + ": "
              + error.getClass().getName()
              + "-> "
              + error.getMessage());
    } else {
      syncLog.setStatus(SyncLogStatus.COMPLETED);
      LOGGER.info("Sync completed for source " + source.getId());
    }

    syncLog.setCompletedAt(syncEndTime);
    long durationSeconds =
        java.time.temporal.ChronoUnit.SECONDS.between(syncStartTime, syncEndTime);
    syncLog.setDurationSeconds((int) durationSeconds);

    // Update source with next sync time
    source.setNextSync(
        LocalDateTime.now()
            .plusDays(source.getSyncInterval() != null ? source.getSyncInterval() : 1));

    syncLogRepository.update(syncLog);
    sourceRepository.update(source);
  }

  /** Trigger manual full sync for a source from admin panel */
  @Transactional
  public void triggerManualSync(Source source) {
    LOGGER.info("Manual sync triggered for source: " + source.getName());

    boolean lockAcquired = syncLockManager.tryAcquireLock(source.getId(), "manual_full");
    if (!lockAcquired) {
      LOGGER.warning("Source " + source.getId() + " is already syncing, cannot start manual sync");
      throw new RuntimeException("Source is already syncing");
    }

    LocalDateTime syncStartTime = LocalDateTime.now();
    SyncLog syncLog =
        SyncLog.builder()
            .sourceId(source.getId())
            .syncType("manual_full")
            .startedAt(syncStartTime)
            .status(SyncLogStatus.RUNNING)
            .itemsAdded(0)
            .itemsUpdated(0)
            .itemsDeleted(0)
            .build();

    try {
      Long logId = syncLogRepository.insert(syncLog);
      syncLog.setId(logId);
      source.setLastSync(syncStartTime);

      performFullSync(source, syncLog, syncStartTime);
    } finally {
      syncLockManager.releaseLock(source.getId());
    }
  }

  /**
   * Trigger sync for a specific task type (granular sync) Valid task types: live_categories,
   * live_streams, vod_categories, vod_streams, series_categories, series
   */
  @Transactional
  public void triggerManualSyncTask(Source source, String taskType) {
    LOGGER.info(
        "Manual sync triggered for source: " + source.getName() + ", task type: " + taskType);

    // Validate task type
    Set<String> validTaskTypes =
        Set.of(
            "live_categories", "live_streams",
            "vod_categories", "vod_streams",
            "series_categories", "series");

    if (!validTaskTypes.contains(taskType)) {
      throw new IllegalArgumentException("Invalid task type: " + taskType);
    }

    boolean lockAcquired = syncLockManager.tryAcquireLock(source.getId(), "manual_" + taskType);
    if (!lockAcquired) {
      LOGGER.warning("Source " + source.getId() + " is already syncing");
      throw new RuntimeException("Source is already syncing");
    }

    LocalDateTime syncStartTime = LocalDateTime.now();
    SyncLog syncLog =
        SyncLog.builder()
            .sourceId(source.getId())
            .syncType("manual_" + taskType)
            .startedAt(syncStartTime)
            .status(SyncLogStatus.RUNNING)
            .itemsAdded(0)
            .itemsUpdated(0)
            .itemsDeleted(0)
            .build();

    try {
      Long logId = syncLogRepository.insert(syncLog);
      syncLog.setId(logId);
      source.setLastSync(syncStartTime);

      // Execute the specific task
      switch (taskType) {
        case "live_categories":
          liveSynchronizer.syncCategories(source, syncLog);
          break;
        case "vod_categories":
          vodSynchronizer.syncCategories(source, syncLog);
          break;
        case "series_categories":
          seriesSynchronizer.syncCategories(source, syncLog);
          break;
        case "live_streams":
          liveSynchronizer.syncStreams(source, syncLog);
          break;
        case "vod_streams":
          vodSynchronizer.syncStreams(source, syncLog);
          break;
        case "series":
          seriesSynchronizer.syncStreams(source, syncLog);
          break;
        default:
          throw new IllegalArgumentException("Unknown task type: " + taskType);
      }

      finalizeSyncLog(source, syncLog, syncStartTime, null);
    } catch (Exception failure) {
      finalizeSyncLog(source, syncLog, syncStartTime, failure);
      throw failure;
    } finally {
      syncLockManager.releaseLock(source.getId());
    }
  }

  /** Trigger full sync (alias for triggerManualSync) */
  public void triggerFullSync(Source source) {
    triggerManualSync(source);
  }
}
