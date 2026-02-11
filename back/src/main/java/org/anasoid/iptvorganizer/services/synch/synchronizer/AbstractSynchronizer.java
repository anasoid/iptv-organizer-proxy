package org.anasoid.iptvorganizer.services.synch.synchronizer;

import jakarta.inject.Inject;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.anasoid.iptvorganizer.config.SyncConfig;
import org.anasoid.iptvorganizer.models.entity.Source;
import org.anasoid.iptvorganizer.models.entity.SyncLog;
import org.anasoid.iptvorganizer.models.entity.stream.BaseStream;
import org.anasoid.iptvorganizer.models.entity.stream.Category;
import org.anasoid.iptvorganizer.models.entity.stream.SourcedEntity;
import org.anasoid.iptvorganizer.models.entity.stream.StreamLike;
import org.anasoid.iptvorganizer.models.entity.stream.StreamType;
import org.anasoid.iptvorganizer.repositories.stream.AbstractTypedCategoryRepository;
import org.anasoid.iptvorganizer.repositories.stream.BaseStreamRepository;
import org.anasoid.iptvorganizer.repositories.stream.SynchronizedItemRepository;
import org.anasoid.iptvorganizer.repositories.synch.SourceRepository;
import org.anasoid.iptvorganizer.repositories.synch.SyncLogRepository;
import org.anasoid.iptvorganizer.services.FilterService;
import org.anasoid.iptvorganizer.services.synch.BlackListCleanupService;
import org.anasoid.iptvorganizer.services.synch.SyncLockManager;
import org.anasoid.iptvorganizer.services.synch.mapper.AbstractSyncMapper;
import org.anasoid.iptvorganizer.services.synch.mapper.SynchronizedItemMapParameter;
import org.anasoid.iptvorganizer.utils.streaming.JsonStreamResult;
import org.anasoid.iptvorganizer.utils.xtream.XtreamClient;

/**
 * Background sync service using Quarkus Scheduler Syncs live streams, VOD, series, and categories
 * from Xtream API
 */
@Slf4j
public abstract class AbstractSynchronizer<T extends BaseStream & StreamLike> {

  private static final int LOG_THRESHOLD = 1000;

  @Inject SourceRepository sourceRepository;

  @Inject SyncLogRepository syncLogRepository;

  @Inject XtreamClient xtreamClient;

  @Inject FilterService filterService;

  @Inject SyncLockManager syncLockManager;

  @Inject SyncConfig syncConfig;

  @Inject BlackListCleanupService blackListCleanupService;

  protected BaseStreamRepository<T> streamRepository;
  protected AbstractTypedCategoryRepository typedCategoryRepository;

  protected AbstractSynchronizer() {}

  protected AbstractSynchronizer(
      BaseStreamRepository<T> streamRepository,
      AbstractTypedCategoryRepository typedCategoryRepository) {
    this.streamRepository = streamRepository;
    this.typedCategoryRepository = typedCategoryRepository;
  }

  /** Fetch and sync categories for a single type (live/vod/series) */
  public List<SyncLog> syncAll(Source source) {
    List<SyncLog> result = new ArrayList<>();
    result.add(syncCategories(source));

    // Clean up streams for blacklisted categories after category sync
    int deletedStreams =
        blackListCleanupService.cleanupBlacklistedStreams(
            source, getStreamType(), streamRepository);

    if (deletedStreams > 0) {
      log.info("Pre-sync cleanup: Removed {} streams from blacklisted categories", deletedStreams);
    }

    result.add(syncStreams(source));
    return result;
  }

  /** Fetch and sync categories for a single type (live/vod/series) */
  public SyncLog syncCategories(Source source) {
    return syncItem(source, typedCategoryRepository, getMapper()::mapToCategory, "categories");
  }

  public SyncLog syncStreams(Source source) {
    return syncItem(source, streamRepository, getMapper()::mapToStream, "streams");
  }

  /** Generic stream synchronization method for all stream types */
  public <R extends SourcedEntity> SyncLog syncItem(
      Source source,
      SynchronizedItemRepository<R> synchronizedItemRepository,
      Function<SynchronizedItemMapParameter, R> mapper,
      String itemType) {
    StreamType type = getStreamType();
    log.info("Syncing {} for source: {}", type.getStreamTypeName(), source.getName());

    LocalDateTime syncStartTime = LocalDateTime.now();
    SyncLog syncLog = initializeSyncLog(source.getId(), type.getCategoryType(), itemType);

    int count = 0;
    int added = 0;
    int updated = 0;
    long startTime = System.currentTimeMillis();
    long bytesRead = 0;

    Set<Integer> fetchedIds = new HashSet<>();

    try {
      // Get or create unknown category
      Integer unknownCategoryId =
          typedCategoryRepository.getOrCreateUnknownCategory(source.getId());

      // Build category cache for efficient lookups during stream filtering
      // Only apply blacklist filtering for stream items (not categories)
      Map<Integer, Category> categoryCache = new HashMap<>();
      if (synchronizedItemRepository instanceof BaseStreamRepository) {
        categoryCache =
            typedCategoryRepository.findBySourceAndTypeAsMap(
                source.getId(), type.getCategoryType());
        log.debug("Built category cache with {} categories", categoryCache.size());
      }

      long startStep = System.currentTimeMillis();
      long startByteStep = 0;
      int batchSize = syncConfig.getBatchSize();
      List<R> batch = new ArrayList<>(batchSize);

      // Fetch external data using lazy Iterator-based streaming
      try (JsonStreamResult<Map<?, ?>> streamResult =
          synchronizedItemRepository.fetchExternalData(source)) {
        Iterator<Map<?, ?>> iterator = streamResult.iterator();

        // Process items in batches using transactional batching
        while (iterator.hasNext()) {
          Map data = iterator.next();

          R item =
              mapper.apply(
                  SynchronizedItemMapParameter.builder()
                      .unknownCategoryId(unknownCategoryId)
                      .source(source)
                      .data(data)
                      .build());

          // Assign num ordering
          item.setNum(++count);

          // Capture external ID for deletion comparison
          Integer externalId = item.getExternalId();
          if (externalId != null) {
            fetchedIds.add(externalId);
          }

          log.debug("Processing {} ID: {}", type.getStreamTypeName(), item.getExternalId());

          // Check if stream belongs to blacklisted category (only for BaseStream items)
          if (synchronizedItemRepository instanceof BaseStreamRepository
              && item instanceof BaseStream) {
            BaseStream stream = (BaseStream) item;
            Integer categoryId = stream.getCategoryId();
            Category category = categoryCache.get(categoryId);
            if (category != null
                && category.getBlackList() != null
                && category.getBlackList().isHide()) {
              log.debug(
                  "Skipping {} ID {} - belongs to blacklisted category: {} ({})",
                  type.getStreamTypeName(),
                  item.getExternalId(),
                  category.getName(),
                  category.getBlackList());
              continue; // Skip this stream
            }
          }

          batch.add(item);

          // Flush batch when full (one transaction per batch)
          if (batch.size() >= batchSize) {
            int insertedCount = synchronizedItemRepository.insertOrUpdateByExternalId(batch);
            added += insertedCount;
            updated += (batch.size() - insertedCount);
            batch.clear();

            // Log progress every LOG_THRESHOLD items
            if (count % LOG_THRESHOLD == 0) {
              long duration = System.currentTimeMillis() - startStep;
              long currentByte = streamResult.getBytesRead();
              long bytesReadStep = currentByte - startByteStep;
              startByteStep = currentByte;
              long bandwidthKb = bytesReadStep / (duration == 0 ? 1 : duration);
              startStep = System.currentTimeMillis();
              log.info(
                  "Processed "
                      + count
                      + " items in "
                      + (duration)
                      + "ms for "
                      + type.getStreamTypeName()
                      + ", received:"
                      + currentByte / 1000
                      + "kb"
                      + ", bandwidth:"
                      + bandwidthKb
                      + "kb/s");
            }
          }
        }

        // Flush remaining items in the batch
        if (!batch.isEmpty()) {
          int insertedCount = synchronizedItemRepository.insertOrUpdateByExternalId(batch);
          added += insertedCount;
          updated += (batch.size() - insertedCount);
        }

        bytesRead = streamResult.getBytesRead();
      } catch (IOException e) {
        throw new RuntimeException("Failed to process stream data", e);
      }

      log.info(
          "{} - Fetched {} items from source {} ({}kb read)",
          type.getStreamTypeName(),
          fetchedIds.size(),
          source.getName(),
          bytesRead / 1000);

      // Find items to delete
      List<Integer> existingExternalIds =
          synchronizedItemRepository.findExternalIdsBySourceId(source.getId());
      Set<Integer> toDeleteIds = new HashSet<>();
      for (Integer id : existingExternalIds) {
        if (!fetchedIds.contains(id) && id != 0) {
          toDeleteIds.add(id);
        }
      }

      if (!toDeleteIds.isEmpty()) {
        log.info(
            "Deleting {} items, first ID: {}", toDeleteIds.size(), toDeleteIds.iterator().next());
        for (Integer toDeleteId : toDeleteIds) {
          synchronizedItemRepository.deleteByExternalId(toDeleteId, source.getId());
        }
      }

      // Final cleanup: Remove any streams from blacklisted categories (safety net)
      // Only cleanup streams, not categories
      int blacklistDeleted = 0;
      if (synchronizedItemRepository instanceof BaseStreamRepository) {
        @SuppressWarnings("unchecked")
        BaseStreamRepository<? extends BaseStream> streamRepo =
            (BaseStreamRepository<? extends BaseStream>) synchronizedItemRepository;
        blacklistDeleted =
            blackListCleanupService.cleanupBlacklistedStreams(source, type, streamRepo);
      }

      long duration = (System.currentTimeMillis() - startTime) / 1000;
      log.info(
          "{} - Added: {}, Updated: {}, Deleted: {} (orphaned) + {} (blacklisted), Duration(s): {}, Bytes read: {}kb",
          type.getStreamTypeName(),
          added,
          updated,
          toDeleteIds.size(),
          blacklistDeleted,
          duration,
          bytesRead / 1000);

      syncLog.setItemsAdded(syncLog.getItemsAdded() + added);
      syncLog.setItemsUpdated(syncLog.getItemsUpdated() + updated);
      syncLog.setItemsDeleted(syncLog.getItemsDeleted() + toDeleteIds.size() + blacklistDeleted);

      finalizeSyncLog(syncLog, syncStartTime, null);
    } catch (Exception e) {
      log.error("Error syncing {}: ", type.getStreamTypeName(), e);
      finalizeSyncLog(syncLog, syncStartTime, e);
      throw e;
    }

    return syncLog;
  }

  /** Initialize SyncLog for a sync operation with synchronizer type */
  private SyncLog initializeSyncLog(Long sourceId, String synchronizerType, String itemType) {
    SyncLog syncLog =
        SyncLog.builder()
            .sourceId(sourceId)
            .syncType(synchronizerType + "_" + itemType)
            .startedAt(LocalDateTime.now())
            .status(SyncLog.SyncLogStatus.RUNNING)
            .itemsAdded(0)
            .itemsUpdated(0)
            .itemsDeleted(0)
            .build();

    syncLogRepository.insert(syncLog);
    return syncLog;
  }

  /** Finalize SyncLog after sync operation */
  private void finalizeSyncLog(SyncLog syncLog, LocalDateTime syncStartTime, Throwable error) {
    if (syncLog == null) {
      return;
    }

    LocalDateTime syncEndTime = LocalDateTime.now();

    if (error != null) {
      syncLog.setStatus(SyncLog.SyncLogStatus.FAILED);
      syncLog.setErrorMessage(error.getMessage());
    } else {
      syncLog.setStatus(SyncLog.SyncLogStatus.COMPLETED);
    }

    syncLog.setCompletedAt(syncEndTime);
    long durationSeconds = ChronoUnit.SECONDS.between(syncStartTime, syncEndTime);
    syncLog.setDurationSeconds((int) durationSeconds);

    syncLogRepository.update(syncLog);
  }

  abstract AbstractSyncMapper<T> getMapper();

  abstract StreamType getStreamType();
}
