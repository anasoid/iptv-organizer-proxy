package org.anasoid.iptvorganizer.services.synch;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.LocalDateTime;
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

    sourceRepository
        .findSourcesNeedingSync()
        .onItem()
        .transformToUni(
            source ->
                syncSource(source)
                    .onFailure()
                    .recoverWithUni(
                        failure -> {
                          LOGGER.severe(
                              "Failed to sync source "
                                  + source.getId()
                                  + ": "
                                  + failure.getMessage());
                          return Uni.createFrom().voidItem();
                        }))
        .merge()
        .collect()
        .asList()
        .subscribe()
        .with(
            items ->
                LOGGER.info("Scheduled sync completed: " + items.size() + " sources processed"),
            failure -> LOGGER.severe("Scheduled sync failed: " + failure.getMessage()));
  }

  /** Sync a single source with concurrent sync prevention */
  private Uni<Void> syncSource(Source source) {
    // Try to acquire lock first
    return Uni.createFrom()
        .item(() -> syncLockManager.tryAcquireLock(source.getId(), "full"))
        .onItem()
        .transformToUni(
            lockAcquired -> {
              if (!lockAcquired) {
                LOGGER.info("Source " + source.getId() + " is already being synced, skipping");
                return Uni.createFrom().voidItem();
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

              return syncLogRepository
                  .insert(syncLog)
                  .onItem()
                  .transformToUni(
                      logId -> {
                        syncLog.setId(logId);
                        source.setLastSync(syncStartTime);

                        return performFullSync(source, syncLog, syncStartTime)
                            .eventually(
                                () -> {
                                  syncLockManager.releaseLock(source.getId());
                                  return Uni.createFrom().voidItem();
                                });
                      })
                  .onFailure()
                  .recoverWithUni(
                      failure -> {
                        // Release lock on failure
                        syncLockManager.releaseLock(source.getId());
                        return Uni.createFrom().failure(failure);
                      });
            });
  }

  /** Perform full sync of a source: categories, live streams, VOD, series */
  private Uni<Void> performFullSync(Source source, SyncLog syncLog, LocalDateTime syncStartTime) {
    return Uni.createFrom()
        .item(source)
        // Sync categories streams
        .onItem()
        .transformToUni(s -> liveSynchronizer.syncCategories(s, syncLog))
        // Sync categories series
        .onItem()
        .transformToUni(s -> seriesSynchronizer.syncCategories(s, syncLog))
        // Sync categories VOD
        .onItem()
        .transformToUni(s -> vodSynchronizer.syncCategories(s, syncLog))
        // Then sync live streams
        .onItem()
        .transformToUni(s -> liveSynchronizer.syncStreams(s, syncLog))
        // Then sync VOD
        .onItem()
        .transformToUni(s -> vodSynchronizer.syncStreams(s, syncLog))
        // Then sync series
        .onItem()
        .transformToUni(s -> seriesSynchronizer.syncStreams(s, syncLog))
        // Finally, update sync status
        .onItem()
        .transformToUni(s -> finalizeSyncLog(source, syncLog, syncStartTime, null))
        .onFailure()
        .recoverWithUni(failure -> finalizeSyncLog(source, syncLog, syncStartTime, failure));
  }

  /** Finalize sync log and update source */
  private Uni<Void> finalizeSyncLog(
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

    // Update source with next sync time (lock will be released separately)
    source.setNextSync(
        LocalDateTime.now()
            .plusDays(source.getSyncInterval() != null ? source.getSyncInterval() : 1));

    return syncLogRepository
        .update(syncLog)
        .onItem()
        .transformToUni(v -> sourceRepository.update(source));
  }

  /** Trigger manual full sync for a source from admin panel */
  public Uni<Void> triggerManualSync(Source source) {
    LOGGER.info("Manual sync triggered for source: " + source.getName());

    return Uni.createFrom()
        .item(() -> syncLockManager.tryAcquireLock(source.getId(), "manual_full"))
        .flatMap(
            lockAcquired -> {
              if (!lockAcquired) {
                LOGGER.warning(
                    "Source " + source.getId() + " is already syncing, cannot start manual sync");
                return Uni.createFrom().failure(new RuntimeException("Source is already syncing"));
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

              return syncLogRepository
                  .insert(syncLog)
                  .flatMap(
                      logId -> {
                        syncLog.setId(logId);
                        source.setLastSync(syncStartTime);

                        return performFullSync(source, syncLog, syncStartTime)
                            .eventually(
                                () -> {
                                  syncLockManager.releaseLock(source.getId());
                                  return Uni.createFrom().voidItem();
                                });
                      })
                  .onFailure()
                  .recoverWithUni(
                      failure -> {
                        // Release lock on failure and propagate error
                        syncLockManager.releaseLock(source.getId());
                        return Uni.createFrom().failure(failure);
                      });
            });
  }

  /**
   * Trigger sync for a specific task type (granular sync) Valid task types: live_categories,
   * live_streams, vod_categories, vod_streams, series_categories, series
   */
  public Uni<Void> triggerManualSyncTask(Source source, String taskType) {
    LOGGER.info(
        "Manual sync triggered for source: " + source.getName() + ", task type: " + taskType);

    // Validate task type
    Set<String> validTaskTypes =
        Set.of(
            "live_categories", "live_streams",
            "vod_categories", "vod_streams",
            "series_categories", "series");

    if (!validTaskTypes.contains(taskType)) {
      return Uni.createFrom()
          .failure(new IllegalArgumentException("Invalid task type: " + taskType));
    }

    return Uni.createFrom()
        .item(() -> syncLockManager.tryAcquireLock(source.getId(), "manual_" + taskType))
        .flatMap(
            lockAcquired -> {
              if (!lockAcquired) {
                LOGGER.warning("Source " + source.getId() + " is already syncing");
                return Uni.createFrom().failure(new RuntimeException("Source is already syncing"));
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

              return syncLogRepository
                  .insert(syncLog)
                  .flatMap(
                      logId -> {
                        syncLog.setId(logId);
                        source.setLastSync(syncStartTime);

                        // Execute the specific task
                        Uni<Source> taskResult =
                            switch (taskType) {
                              case "live_categories" ->
                                  liveSynchronizer.syncCategories(source, syncLog);
                              case "vod_categories" ->
                                  vodSynchronizer.syncCategories(source, syncLog);
                              case "series_categories" ->
                                  seriesSynchronizer.syncCategories(source, syncLog);
                              case "live_streams" -> liveSynchronizer.syncStreams(source, syncLog);
                              case "vod_streams" -> vodSynchronizer.syncStreams(source, syncLog);
                              case "series" -> seriesSynchronizer.syncStreams(source, syncLog);
                              default ->
                                  Uni.createFrom()
                                      .failure(
                                          new IllegalArgumentException(
                                              "Unknown task type: " + taskType));
                            };

                        return taskResult
                            .flatMap(s -> finalizeSyncLog(source, syncLog, syncStartTime, null))
                            .eventually(
                                () -> {
                                  syncLockManager.releaseLock(source.getId());
                                  return Uni.createFrom().voidItem();
                                })
                            .onFailure()
                            .recoverWithUni(
                                failure -> {
                                  // Finalize as failed and release lock
                                  syncLockManager.releaseLock(source.getId());
                                  return finalizeSyncLog(source, syncLog, syncStartTime, failure)
                                      .onItem()
                                      .transformToUni(v -> Uni.createFrom().failure(failure));
                                });
                      });
            });
  }

  /** Trigger full sync (alias for triggerManualSync) */
  public Uni<Void> triggerFullSync(Source source) {
    return triggerManualSync(source);
  }
}
