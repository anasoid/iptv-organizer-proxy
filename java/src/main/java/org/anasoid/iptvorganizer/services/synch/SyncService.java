package org.anasoid.iptvorganizer.services.synch;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import org.anasoid.iptvorganizer.models.entity.BaseEntity;
import org.anasoid.iptvorganizer.models.entity.Source;
import org.anasoid.iptvorganizer.models.entity.SyncLog;
import org.anasoid.iptvorganizer.models.entity.SyncLog.SyncLogStatus;
import org.anasoid.iptvorganizer.models.entity.stream.Category;
import org.anasoid.iptvorganizer.models.entity.stream.StreamLike;
import org.anasoid.iptvorganizer.models.entity.stream.StreamType;
import org.anasoid.iptvorganizer.repositories.BaseRepository;
import org.anasoid.iptvorganizer.repositories.stream.CategoryRepository;
import org.anasoid.iptvorganizer.repositories.stream.LiveStreamRepository;
import org.anasoid.iptvorganizer.repositories.stream.SeriesRepository;
import org.anasoid.iptvorganizer.repositories.stream.VodStreamRepository;
import org.anasoid.iptvorganizer.repositories.synch.SourceRepository;
import org.anasoid.iptvorganizer.repositories.synch.SyncLogRepository;
import org.anasoid.iptvorganizer.services.FilterService;
import org.anasoid.iptvorganizer.services.streaming.HttpStreamingService;
import org.anasoid.iptvorganizer.services.synch.mapper.SynchMapper;
import org.anasoid.iptvorganizer.services.synch.mapper.SynchMapper.CategoryMappingResult;

/**
 * Background sync service using Quarkus Scheduler Syncs live streams, VOD, series, and categories
 * from Xtream API
 */
@ApplicationScoped
public class SyncService {

  private static final Logger LOGGER = Logger.getLogger(SyncService.class.getName());

  private static final int GC_THRESHOLD = 1000;

  @Inject SourceRepository sourceRepository;

  @Inject SyncLogRepository syncLogRepository;

  @Inject LiveStreamRepository liveStreamRepository;

  @Inject VodStreamRepository vodStreamRepository;

  @Inject SeriesRepository seriesRepository;

  @Inject CategoryRepository categoryRepository;

  @Inject HttpStreamingService httpStreamingService;

  @Inject FilterService filterService;

  @Inject SynchMapper synchMapper;

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
        // Sync categories first
        .onItem()
        .transformToUni(s -> syncCategories(s))
        // Then sync live streams
        .onItem()
        .transformToUni(s -> syncLiveStreams(s, syncLog))
        // Then sync VOD
        .onItem()
        .transformToUni(s -> syncVod(s, syncLog))
        // Then sync series
        .onItem()
        .transformToUni(s -> syncSeries(s, syncLog))
        // Finally, update sync status
        .onItem()
        .transformToUni(s -> finalizeSyncLog(source, syncLog, syncStartTime, null))
        .onFailure()
        .recoverWithUni(failure -> finalizeSyncLog(source, syncLog, syncStartTime, failure));
  }

  private Uni<Source> syncCategories(Source source) {
    LOGGER.info("Syncing categories for source: " + source.getName());

    // Fetch all categories from three endpoints
    return Uni.createFrom()
        .item("")
        .flatMap(v -> fetchAndSyncCategoryType(source, StreamType.LIVE))
        .flatMap(v -> fetchAndSyncCategoryType(source, StreamType.VOD))
        .flatMap(v -> fetchAndSyncCategoryType(source, StreamType.SERIES))
        .replaceWith(source);
  }

  /** Fetch and sync categories for a single type (live/vod/series) */
  private Uni<Void> fetchAndSyncCategoryType(Source source, StreamType type) {
    String url = buildApiUrl(source, type.getCategoryAction());
    return httpStreamingService
        .streamJson(url, Map.class)
        .collect()
        .asList()
        .flatMap(categoryMaps -> processCategorySync(source, categoryMaps, type.getCategoryType()))
        .onFailure()
        .invoke(
            ex ->
                LOGGER.severe(
                    "Failed to sync "
                        + type.getCategoryType()
                        + " categories: "
                        + ex.getMessage()));
  }

  /** Process category synchronization: insert/update new, delete obsolete */
  private Uni<Void> processCategorySync(
      Source source, List<Map> categoryMaps, String categoryType) {
    CategoryMappingResult mappingResult =
        synchMapper.mapCategoryData(source, categoryMaps, categoryType);

    return categoryRepository
        .findBySourceId(source.getId())
        .collect()
        .asList()
        .flatMap(
            existingCategories ->
                syncAndDeleteCategories(
                    mappingResult.categories,
                    existingCategories,
                    categoryType,
                    mappingResult.fetchedIds));
  }

  /** Sync (insert/update) and delete obsolete categories */
  private Uni<Void> syncAndDeleteCategories(
      List<Category> newCategories,
      List<Category> existingCategories,
      String categoryType,
      Set<Integer> fetchedIds) {
    // Create a map of existing categories by ID for quick lookup
    Map<Integer, Category> existingMap = new HashMap<>();
    for (Category cat : existingCategories) {
      if (cat.getType().equals(categoryType)) {
        existingMap.put(cat.getExternalId(), cat);
      }
    }

    // Insert/update categories
    return Multi.createFrom()
        .iterable(newCategories)
        .onItem()
        .transformToUniAndConcatenate(
            category -> {
              if (existingMap.containsKey(category.getExternalId())) {
                category.setId(existingMap.get(category.getExternalId()).getId());
                return categoryRepository.update(category);
              } else {
                return categoryRepository.insert(category).replaceWithVoid();
              }
            })
        .collect()
        .asList()
        .flatMap(v -> deleteObsoleteCategories(existingCategories, categoryType, fetchedIds));
  }

  /** Delete obsolete categories that are no longer in the API response */
  private Uni<Void> deleteObsoleteCategories(
      List<Category> existingCategories, String categoryType, Set<Integer> fetchedIds) {
    List<Category> toDelete =
        existingCategories.stream()
            .filter(
                c -> c.getType().equals(categoryType) && !fetchedIds.contains(c.getExternalId()))
            .toList();

    if (toDelete.isEmpty()) {
      return Uni.createFrom().voidItem();
    }

    LOGGER.info("Deleting " + toDelete.size() + " obsolete " + categoryType + " categories");
    return Multi.createFrom()
        .iterable(toDelete)
        .onItem()
        .transformToUniAndConcatenate(cat -> categoryRepository.delete(cat.getId()))
        .collect()
        .asList()
        .replaceWithVoid();
  }

  /** Sync live streams for a source with batch upsert and transactions */
  private Uni<Source> syncLiveStreams(Source source, SyncLog syncLog) {
    return syncStreams(
        source,
        syncLog,
        StreamType.LIVE,
        streamData -> synchMapper.mapToLiveStream(source, streamData),
        liveStreamRepository);
  }

  /** Sync VOD streams for a source one-by-one */
  private Uni<Source> syncVod(Source source, SyncLog syncLog) {
    return syncStreams(
        source,
        syncLog,
        StreamType.VOD,
        streamData -> synchMapper.mapToVodStream(source, streamData),
        vodStreamRepository);
  }

  /** Sync series for a source one-by-one */
  private Uni<Source> syncSeries(Source source, SyncLog syncLog) {
    return syncStreams(
        source,
        syncLog,
        StreamType.SERIES,
        streamData -> synchMapper.mapToSeries(source, streamData),
        seriesRepository);
  }

  /** Generic stream synchronization method for all stream types */
  private <T extends BaseEntity & StreamLike> Uni<Source> syncStreams(
      Source source,
      SyncLog syncLog,
      StreamType type,
      java.util.function.Function<Map<?, ?>, T> mapper,
      BaseRepository<T> repository) {
    LOGGER.info("Syncing " + type.getStreamTypeName() + " for source: " + source.getName());

    String url = buildApiUrl(source, type.getStreamAction());

    return httpStreamingService
        .streamJson(url, Map.class)
        .map(mapper::apply)
        .collect()
        .asList()
        .flatMap(
            allStreams -> {
              LOGGER.info(
                  "Fetched " + allStreams.size() + " " + type.getStreamTypeName() + " from API");

              // Assign num ordering
              for (int i = 0; i < allStreams.size(); i++) {
                allStreams.get(i).setNum(i + 1);
              }

              // Handle streams without category - get/create Unknown category
              List<Uni<Void>> categoryProcessing = new ArrayList<>();
              for (T stream : allStreams) {
                if (stream.getCategoryId() == null || stream.getCategoryId() == 0) {
                  categoryProcessing.add(
                      categoryRepository
                          .getOrCreateUnknownCategory(source.getId(), type.getCategoryType())
                          .invoke(
                              unknownCategoryId -> {
                                stream.setCategoryId(unknownCategoryId.intValue());
                                LOGGER.info(
                                    type.getStreamTypeName()
                                        + " assigned to Unknown category: stream_id="
                                        + stream.getExternalId());
                              })
                          .replaceWithVoid());
                }
              }

              // If category processing needed, wait for it; otherwise continue
              Uni<Void> categoryProcessingDone =
                  categoryProcessing.isEmpty()
                      ? Uni.createFrom().voidItem()
                      : Uni.join().all(categoryProcessing).andFailFast().replaceWithVoid();

              return categoryProcessingDone.flatMap(
                  categoryReady -> {
                    Set<Integer> fetchedStreamIds = new HashSet<>();
                    for (T stream : allStreams) {
                      fetchedStreamIds.add(stream.getExternalId());
                    }

                    // Get all existing streams for this source
                    return repository
                        .findAll()
                        .filter(s -> s.getSourceId().equals(source.getId()))
                        .collect()
                        .asList()
                        .flatMap(
                            existingStreams -> {
                              // Create a map of existing streams by stream_id for quick lookup
                              Map<Integer, T> existingMap = new HashMap<>();
                              for (T stream : existingStreams) {
                                existingMap.put(stream.getExternalId(), stream);
                              }

                              // Calculate statistics
                              AtomicInteger added = new AtomicInteger(0);
                              AtomicInteger updated = new AtomicInteger(0);
                              for (T stream : allStreams) {
                                if (existingMap.containsKey(stream.getExternalId())) {
                                  updated.incrementAndGet();
                                } else {
                                  added.incrementAndGet();
                                }
                              }

                              Set<Long> toDeleteIds = new HashSet<>();
                              for (T stream : existingStreams) {
                                if (!fetchedStreamIds.contains(stream.getExternalId())) {
                                  toDeleteIds.add(stream.getId());
                                }
                              }

                              syncLog.setItemsAdded(syncLog.getItemsAdded() + added.get());
                              syncLog.setItemsUpdated(syncLog.getItemsUpdated() + updated.get());
                              syncLog.setItemsDeleted(
                                  syncLog.getItemsDeleted() + toDeleteIds.size());

                              LOGGER.info(
                                  String.format(
                                      "%s - Added: %d, Updated: %d, Deleted: %d",
                                      type.getStreamTypeName(),
                                      added.get(),
                                      updated.get(),
                                      toDeleteIds.size()));

                              // Insert/update streams one-by-one
                              return Multi.createFrom()
                                  .iterable(allStreams)
                                  .onItem()
                                  .transformToUniAndConcatenate(
                                      stream -> {
                                        if (existingMap.containsKey(stream.getExternalId())) {
                                          // Update: set the ID from existing and update
                                          stream.setId(
                                              existingMap.get(stream.getExternalId()).getId());
                                          return repository.update(stream);
                                        } else {
                                          // Insert new stream
                                          return repository.insert(stream).replaceWithVoid();
                                        }
                                      })
                                  .collect()
                                  .asList()
                                  .flatMap(
                                      v -> {
                                        // Delete obsolete streams
                                        if (toDeleteIds.isEmpty()) {
                                          return Uni.createFrom().voidItem();
                                        }
                                        return Multi.createFrom()
                                            .iterable(toDeleteIds)
                                            .onItem()
                                            .transformToUniAndConcatenate(repository::delete)
                                            .collect()
                                            .asList()
                                            .replaceWithVoid();
                                      })
                                  .invoke(
                                      v -> {
                                        // Explicit GC after processing large batches
                                        if (allStreams.size() >= GC_THRESHOLD) {
                                          System.gc();
                                          LOGGER.fine(
                                              "GC called after "
                                                  + allStreams.size()
                                                  + " "
                                                  + type.getStreamTypeName());
                                        }
                                      });
                            });
                  });
            })
        .replaceWith(source);
  }

  /** Finalize sync log and update source */
  private Uni<Void> finalizeSyncLog(
      Source source, SyncLog syncLog, LocalDateTime syncStartTime, Throwable error) {
    LocalDateTime syncEndTime = LocalDateTime.now();

    if (error != null) {
      syncLog.setStatus(SyncLogStatus.FAILED);
      syncLog.setErrorMessage(error.getMessage());
      LOGGER.severe("Sync failed for source " + source.getId() + ": " + error.getMessage());
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

  /** Build Xtream API URL */
  private String buildApiUrl(Source source, String action) {
    String baseUrl = source.getUrl().replaceAll("/$", "");
    return String.format(
        "%s/player_api.php?action=%s&username=%s&password=%s",
        baseUrl, action, source.getUsername(), source.getPassword());
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
                                  fetchAndSyncCategoryType(source, StreamType.LIVE)
                                      .replaceWith(source);
                              case "vod_categories" ->
                                  fetchAndSyncCategoryType(source, StreamType.VOD)
                                      .replaceWith(source);
                              case "series_categories" ->
                                  fetchAndSyncCategoryType(source, StreamType.SERIES)
                                      .replaceWith(source);
                              case "live_streams" -> syncLiveStreams(source, syncLog);
                              case "vod_streams" -> syncVod(source, syncLog);
                              case "series" -> syncSeries(source, syncLog);
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
