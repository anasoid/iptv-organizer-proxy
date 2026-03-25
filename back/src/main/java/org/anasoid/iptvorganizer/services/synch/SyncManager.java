package org.anasoid.iptvorganizer.services.synch;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.anasoid.iptvorganizer.migrations.SimpleMigrator;
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
@Slf4j
@ApplicationScoped
public class SyncManager {

  @Inject SourceRepository sourceRepository;

  @Inject SimpleMigrator simpleMigrator;

  @Inject LiveSynchronizer liveSynchronizer;
  @Inject VodSynchronizer vodSynchronizer;
  @Inject SeriesSynchronizer seriesSynchronizer;

  @Inject SyncLockManager syncLockManager;

  public void scheduledSync() {
    if (!waitForMigration()) {
      return;
    }
    log.info("Starting scheduled sync check");

    try {
      List<Source> sources = sourceRepository.findSourcesNeedingSync();
      int processed = 0;

      for (Source source : sources) {
        try {
          syncSource(source);
          processed++;
        } catch (Exception e) {
          log.error("Failed to sync source {}", source.getId(), e);
        }
      }

      log.info("Scheduled sync completed: {} sources processed", processed);
    } catch (Exception e) {
      log.error("Scheduled sync failed: ", e);
    }
  }

  /** Sync a single source with concurrent sync prevention */
  protected List<SyncLog> syncSource(Source source) {
    // Try to acquire lock first
    boolean lockAcquired = syncLockManager.tryAcquireLock(source.getId(), "full");
    if (!lockAcquired) {
      log.info("Source {} is already being synced, skipping", source.getId());
      return new ArrayList<>();
    }

    LocalDateTime syncStartTime = LocalDateTime.now();
    source.setLastSync(syncStartTime);

    try {
      return performFullSync(source);
    } catch (Exception e) {
      log.error("Error during sync for source {}: {}", source.getId(), e.getMessage());
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
    log.info("Manual sync triggered for source: {}", source.getName());

    boolean lockAcquired = syncLockManager.tryAcquireLock(source.getId(), "manual_full");
    if (!lockAcquired) {
      log.warn("Source {} is already syncing, cannot start manual sync", source.getId());
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
    log.info("Manual sync triggered for source: {}, task type: {}", source.getName(), taskType);

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
      log.warn("Source {} is already syncing", source.getId());
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

  /**
   * Waits up to 60 seconds for database migration to complete, checking every 5 seconds.
   *
   * @return true if migration is done, false if timed out or interrupted
   */
  private boolean waitForMigration() {
    if (simpleMigrator.isMigrationDone()) {
      return true;
    }
    log.info("Scheduled sync: waiting for database migration to complete...");
    int waited = 0;
    int maxWaitMs = 60_000;
    int intervalMs = 5_000;
    while (!simpleMigrator.isMigrationDone() && waited < maxWaitMs) {
      try {
        Thread.sleep(intervalMs);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        log.warn("Scheduled sync interrupted while waiting for migration");
        return false;
      }
      waited += intervalMs;
      log.info("Scheduled sync: still waiting for migration... ({}s elapsed)", waited / 1000);
    }
    if (!simpleMigrator.isMigrationDone()) {
      log.error(
          "Scheduled sync: migration did not complete within {}s, skipping this run",
          maxWaitMs / 1000);
      return false;
    }
    log.info("Scheduled sync: migration complete, proceeding");
    return true;
  }
}
