package org.anasoid.iptvorganizer.services.synch;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.anasoid.iptvorganizer.models.entity.Source;
import org.anasoid.iptvorganizer.models.entity.SyncLog;
import org.anasoid.iptvorganizer.repositories.synch.SourceRepository;
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
          LOGGER.log(Level.SEVERE, "Failed to sync source " + source.getId(), e);
        }
      }

      LOGGER.info("Scheduled sync completed: " + processed + " sources processed");
    } catch (Exception e) {
      LOGGER.log(Level.SEVERE, "Scheduled sync failed: ", e);
    }
  }

  /** Sync a single source with concurrent sync prevention */
  protected List<SyncLog> syncSource(Source source) {
    // Try to acquire lock first
    boolean lockAcquired = syncLockManager.tryAcquireLock(source.getId(), "full");
    if (!lockAcquired) {
      LOGGER.info("Source " + source.getId() + " is already being synced, skipping");
      return new ArrayList<>();
    }

    LocalDateTime syncStartTime = LocalDateTime.now();
    source.setLastSync(syncStartTime);

    try {
      return performFullSync(source);
    } catch (Exception e) {
      LOGGER.severe("Error during sync for source " + source.getId() + ": " + e.getMessage());
      throw e;
    } finally {
      syncLockManager.releaseLock(source.getId());
      sourceRepository.update(source);
    }
  }

  /** Perform full sync of a source: categories, live streams, VOD, series */
  private List<SyncLog> performFullSync(Source source) {
    // Sync categories for all types (sequential)
    List<SyncLog> result = new ArrayList<>();
    result.addAll(liveSynchronizer.syncAll(source));
    result.addAll(seriesSynchronizer.syncAll(source));
    result.addAll(vodSynchronizer.syncAll(source));

    // Update source with next sync time
    source.setNextSync(
        LocalDateTime.now()
            .plusDays(source.getSyncInterval() != null ? source.getSyncInterval() : 1));
    return result;
  }

  /** Trigger manual full sync for a source from admin panel */
  public void triggerManualSync(Source source) {
    LOGGER.info("Manual sync triggered for source: " + source.getName());

    boolean lockAcquired = syncLockManager.tryAcquireLock(source.getId(), "manual_full");
    if (!lockAcquired) {
      LOGGER.warning("Source " + source.getId() + " is already syncing, cannot start manual sync");
      throw new RuntimeException("Source is already syncing");
    }

    LocalDateTime syncStartTime = LocalDateTime.now();
    source.setLastSync(syncStartTime);

    try {
      performFullSync(source);
    } finally {
      syncLockManager.releaseLock(source.getId());
      sourceRepository.update(source);
    }
  }

  /**
   * Trigger sync for a specific task type (granular sync) Valid task types: live_categories,
   * live_streams, vod_categories, vod_streams, series_categories, series
   */
  public SyncLog triggerManualSyncTask(Source source, String taskType) {
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
    source.setLastSync(syncStartTime);
    SyncLog syncLog = null;

    try {
      // Execute the specific task
      switch (taskType) {
        case "live_categories":
          syncLog = liveSynchronizer.syncCategories(source);
          break;
        case "vod_categories":
          syncLog = vodSynchronizer.syncCategories(source);
          break;
        case "series_categories":
          syncLog = seriesSynchronizer.syncCategories(source);
          break;
        case "live_streams":
          syncLog = liveSynchronizer.syncStreams(source);
          break;
        case "vod_streams":
          syncLog = vodSynchronizer.syncStreams(source);
          break;
        case "series":
          syncLog = seriesSynchronizer.syncStreams(source);
          break;
        default:
          throw new IllegalArgumentException("Unknown task type: " + taskType);
      }
    } finally {
      syncLockManager.releaseLock(source.getId());
      sourceRepository.update(source);
    }
    return syncLog;
  }

  /** Trigger full sync (alias for triggerManualSync) */
  public void triggerFullSync(Source source) {
    triggerManualSync(source);
  }
}
