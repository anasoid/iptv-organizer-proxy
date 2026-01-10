package org.anasoid.iptvorganizer.services;

import io.quarkus.scheduler.Scheduled;
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
import org.anasoid.iptvorganizer.models.BaseEntity;
import org.anasoid.iptvorganizer.models.Source;
import org.anasoid.iptvorganizer.models.SyncLog;
import org.anasoid.iptvorganizer.models.SyncLogStatus;
import org.anasoid.iptvorganizer.models.http.HttpOptions;
import org.anasoid.iptvorganizer.models.stream.Category;
import org.anasoid.iptvorganizer.models.stream.LiveStream;
import org.anasoid.iptvorganizer.models.stream.Series;
import org.anasoid.iptvorganizer.models.stream.StreamLike;
import org.anasoid.iptvorganizer.models.stream.VodStream;
import org.anasoid.iptvorganizer.repositories.BaseRepository;
import org.anasoid.iptvorganizer.repositories.SourceRepository;
import org.anasoid.iptvorganizer.repositories.SyncLogRepository;
import org.anasoid.iptvorganizer.repositories.SyncScheduleRepository;
import org.anasoid.iptvorganizer.repositories.stream.CategoryRepository;
import org.anasoid.iptvorganizer.repositories.stream.LiveStreamRepository;
import org.anasoid.iptvorganizer.repositories.stream.SeriesRepository;
import org.anasoid.iptvorganizer.repositories.stream.VodStreamRepository;
import org.anasoid.iptvorganizer.services.streaming.HttpStreamingService;

/**
 * Background sync service using Quarkus Scheduler Syncs live streams, VOD, series, and categories
 * from Xtream API
 */
@ApplicationScoped
public class SyncService {

  private static final Logger LOGGER = Logger.getLogger(SyncService.class.getName());

  private static final int GC_THRESHOLD = 1000;

  @Inject SourceRepository sourceRepository;

  @Inject SyncScheduleRepository syncScheduleRepository;

  @Inject SyncLogRepository syncLogRepository;

  @Inject LiveStreamRepository liveStreamRepository;

  @Inject VodStreamRepository vodStreamRepository;

  @Inject SeriesRepository seriesRepository;

  @Inject CategoryRepository categoryRepository;

  @Inject HttpStreamingService httpStreamingService;

  @Inject FilterService filterService;

  @Inject LabelExtractor labelExtractor;

  @Inject SyncLockManager syncLockManager;

  /**
   * Scheduled task to check for sources needing sync Runs every 5 minutes by default, configurable
   * via sync.check.interval
   */
  @Scheduled(cron = "0 */5 * * * ?", identity = "sync-daemon")
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

  /** Sync categories for a source (live, VOD, series) */
  private Uni<Source> syncCategories(Source source) {
    LOGGER.info("Syncing categories for source: " + source.getName());

    String liveUrl = buildApiUrl(source, "get_live_categories");
    String vodUrl = buildApiUrl(source, "get_vod_categories");
    String seriesUrl = buildApiUrl(source, "get_series_categories");

    HttpOptions httpOptions = new HttpOptions();
    httpOptions.setTimeout(30);
    httpOptions.setMaxRetries(3);

    // Fetch all categories from three endpoints
    return Uni.createFrom()
        .item("")
        .flatMap(v -> fetchAndSyncCategoryType(source, liveUrl, "live", httpOptions))
        .flatMap(v -> fetchAndSyncCategoryType(source, vodUrl, "vod", httpOptions))
        .flatMap(v -> fetchAndSyncCategoryType(source, seriesUrl, "series", httpOptions))
        .replaceWith(source);
  }

  /** Fetch and sync categories for a single type (live/vod/series) */
  private Uni<Void> fetchAndSyncCategoryType(
      Source source, String url, String categoryType, HttpOptions httpOptions) {
    return httpStreamingService
        .streamJson(url, Map.class, httpOptions)
        .collect()
        .asList()
        .flatMap(categoryMaps -> processCategorySync(source, categoryMaps, categoryType))
        .onFailure()
        .invoke(
            ex ->
                LOGGER.severe(
                    "Failed to sync " + categoryType + " categories: " + ex.getMessage()));
  }

  /** Map API response data to Category objects */
  private CategoryMappingResult mapCategoryData(
      Source source, List<Map> categoryMaps, String categoryType) {
    List<Category> categories = new ArrayList<>();
    Set<Integer> fetchedCategoryIds = new HashSet<>();
    AtomicInteger num = new AtomicInteger(1);

    for (Map catData : categoryMaps) {
      Category category = new Category();
      category.setSourceId(source.getId());
      category.setCategoryId(getIntValue(catData, "category_id"));
      category.setCategoryName(getStringValue(catData, "category_name"));
      category.setCategoryType(categoryType);
      category.setNum(num.getAndIncrement());
      category.setParentId(getIntValue(catData, "parent_id"));
      category.setLabels(labelExtractor.extractLabels(category.getCategoryName()));

      categories.add(category);
      fetchedCategoryIds.add(category.getCategoryId());
    }

    LOGGER.info("Fetched " + categories.size() + " " + categoryType + " categories from API");
    return new CategoryMappingResult(categories, fetchedCategoryIds);
  }

  /** Helper class to hold category mapping results */
  private static class CategoryMappingResult {
    List<Category> categories;
    Set<Integer> fetchedIds;

    CategoryMappingResult(List<Category> categories, Set<Integer> fetchedIds) {
      this.categories = categories;
      this.fetchedIds = fetchedIds;
    }
  }

  /** Process category synchronization: insert/update new, delete obsolete */
  private Uni<Void> processCategorySync(
      Source source, List<Map> categoryMaps, String categoryType) {
    CategoryMappingResult mappingResult = mapCategoryData(source, categoryMaps, categoryType);

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
      if (cat.getCategoryType().equals(categoryType)) {
        existingMap.put(cat.getCategoryId(), cat);
      }
    }

    // Insert/update categories
    return Multi.createFrom()
        .iterable(newCategories)
        .onItem()
        .transformToUniAndConcatenate(
            category -> {
              if (existingMap.containsKey(category.getCategoryId())) {
                category.setId(existingMap.get(category.getCategoryId()).getId());
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
                c ->
                    c.getCategoryType().equals(categoryType)
                        && !fetchedIds.contains(c.getCategoryId()))
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
        "live streams",
        "get_live_streams",
        "live",
        streamData -> mapToLiveStream(source, streamData),
        liveStreamRepository);
  }

  /** Sync VOD streams for a source one-by-one */
  private Uni<Source> syncVod(Source source, SyncLog syncLog) {
    return syncStreams(
        source,
        syncLog,
        "VOD streams",
        "get_vod_streams",
        "vod",
        streamData -> mapToVodStream(source, streamData),
        vodStreamRepository);
  }

  /** Sync series for a source one-by-one */
  private Uni<Source> syncSeries(Source source, SyncLog syncLog) {
    return syncStreams(
        source,
        syncLog,
        "series",
        "get_series",
        "series",
        streamData -> mapToSeries(source, streamData),
        seriesRepository);
  }

  /** Generic stream synchronization method for all stream types */
  private <T extends BaseEntity & StreamLike> Uni<Source> syncStreams(
      Source source,
      SyncLog syncLog,
      String streamTypeName,
      String apiAction,
      String categoryType,
      java.util.function.Function<Map<?, ?>, T> mapper,
      BaseRepository<T> repository) {
    LOGGER.info("Syncing " + streamTypeName + " for source: " + source.getName());

    String url = buildApiUrl(source, apiAction);
    HttpOptions httpOptions = createHttpOptions();

    return httpStreamingService
        .streamJson(url, Map.class, httpOptions)
        .map(mapper::apply)
        .collect()
        .asList()
        .flatMap(
            allStreams -> {
              LOGGER.info("Fetched " + allStreams.size() + " " + streamTypeName + " from API");

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
                          .getOrCreateUnknownCategory(source.getId(), categoryType)
                          .invoke(
                              unknownCategoryId -> {
                                stream.setCategoryId(unknownCategoryId.intValue());
                                LOGGER.info(
                                    streamTypeName
                                        + " assigned to Unknown category: stream_id="
                                        + stream.getStreamId());
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
                      fetchedStreamIds.add(stream.getStreamId());
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
                                existingMap.put(stream.getStreamId(), stream);
                              }

                              // Calculate statistics
                              AtomicInteger added = new AtomicInteger(0);
                              AtomicInteger updated = new AtomicInteger(0);
                              for (T stream : allStreams) {
                                if (existingMap.containsKey(stream.getStreamId())) {
                                  updated.incrementAndGet();
                                } else {
                                  added.incrementAndGet();
                                }
                              }

                              Set<Long> toDeleteIds = new HashSet<>();
                              for (T stream : existingStreams) {
                                if (!fetchedStreamIds.contains(stream.getStreamId())) {
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
                                      streamTypeName,
                                      added.get(),
                                      updated.get(),
                                      toDeleteIds.size()));

                              // Insert/update streams one-by-one
                              return Multi.createFrom()
                                  .iterable(allStreams)
                                  .onItem()
                                  .transformToUniAndConcatenate(
                                      stream -> {
                                        if (existingMap.containsKey(stream.getStreamId())) {
                                          // Update: set the ID from existing and update
                                          stream.setId(
                                              existingMap.get(stream.getStreamId()).getId());
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
                                                  + streamTypeName);
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
            .plusMinutes(source.getSyncInterval() != null ? source.getSyncInterval() : 5));

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

  /** Map API response to LiveStream entity */
  private LiveStream mapToLiveStream(Source source, Map<?, ?> data) {
    LiveStream stream = new LiveStream();
    stream.setSourceId(source.getId());
    stream.setStreamId(getIntValue(data, "stream_id"));
    stream.setNum(getIntValue(data, "num"));
    stream.setName(getStringValue(data, "name"));
    stream.setCategoryId(getIntValue(data, "category_id"));
    stream.setIsAdult(getBooleanValue(data, "is_adult"));

    // Extract and set labels from stream name
    stream.setLabels(labelExtractor.extractLabels(stream.getName()));

    stream.setData(convertMapToJson(data));
    return stream;
  }

  /** Map API response to VodStream entity */
  private VodStream mapToVodStream(Source source, Map<?, ?> data) {
    VodStream stream = new VodStream();
    stream.setSourceId(source.getId());
    stream.setStreamId(getIntValue(data, "stream_id"));
    stream.setNum(getIntValue(data, "num"));
    stream.setName(getStringValue(data, "name"));
    stream.setCategoryId(getIntValue(data, "category_id"));
    stream.setIsAdult(getBooleanValue(data, "is_adult"));

    // Extract and set labels
    stream.setLabels(labelExtractor.extractLabels(stream.getName()));

    stream.setData(convertMapToJson(data));
    return stream;
  }

  /** Map API response to Series entity */
  private Series mapToSeries(Source source, Map<?, ?> data) {
    Series series = new Series();
    series.setSourceId(source.getId());
    series.setStreamId(getIntValue(data, "series_id"));
    series.setNum(getIntValue(data, "num"));
    series.setName(getStringValue(data, "name"));
    series.setCategoryId(getIntValue(data, "category_id"));
    series.setIsAdult(getBooleanValue(data, "is_adult"));

    // Extract and set labels
    series.setLabels(labelExtractor.extractLabels(series.getName()));

    series.setData(convertMapToJson(data));
    return series;
  }

  private Integer getIntValue(Map<?, ?> data, String key) {
    Object value = data.get(key);
    if (value instanceof Integer) {
      return (Integer) value;
    } else if (value instanceof Number) {
      return ((Number) value).intValue();
    } else if (value instanceof String) {
      try {
        return Integer.parseInt((String) value);
      } catch (NumberFormatException e) {
        return null;
      }
    }
    return null;
  }

  private String getStringValue(Map<?, ?> data, String key) {
    Object value = data.get(key);
    return value != null ? value.toString() : null;
  }

  private Boolean getBooleanValue(Map<?, ?> data, String key) {
    Object value = data.get(key);
    if (value instanceof Boolean) {
      return (Boolean) value;
    } else if (value instanceof String) {
      return Boolean.parseBoolean((String) value);
    } else if (value instanceof Number) {
      return ((Number) value).intValue() == 1;
    }
    return false;
  }

  private String convertMapToJson(Map<?, ?> data) {
    try {
      return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(data);
    } catch (Exception e) {
      return "{}";
    }
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
                                  fetchAndSyncCategoryType(
                                          source,
                                          buildApiUrl(source, "get_live_categories"),
                                          "live",
                                          createHttpOptions())
                                      .replaceWith(source);
                              case "vod_categories" ->
                                  fetchAndSyncCategoryType(
                                          source,
                                          buildApiUrl(source, "get_vod_categories"),
                                          "vod",
                                          createHttpOptions())
                                      .replaceWith(source);
                              case "series_categories" ->
                                  fetchAndSyncCategoryType(
                                          source,
                                          buildApiUrl(source, "get_series_categories"),
                                          "series",
                                          createHttpOptions())
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

  /** Create HTTP options for API calls */
  private HttpOptions createHttpOptions() {
    HttpOptions httpOptions = new HttpOptions();
    httpOptions.setTimeout(30);
    httpOptions.setMaxRetries(3);
    return httpOptions;
  }
}
