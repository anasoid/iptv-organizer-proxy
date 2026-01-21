package org.anasoid.iptvorganizer.services.synch.synchronizer;

import jakarta.inject.Inject;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.logging.Level;
import lombok.extern.java.Log;
import org.anasoid.iptvorganizer.config.SyncConfig;
import org.anasoid.iptvorganizer.models.entity.Source;
import org.anasoid.iptvorganizer.models.entity.SyncLog;
import org.anasoid.iptvorganizer.models.entity.stream.BaseStream;
import org.anasoid.iptvorganizer.models.entity.stream.SourcedEntity;
import org.anasoid.iptvorganizer.models.entity.stream.StreamLike;
import org.anasoid.iptvorganizer.models.entity.stream.StreamType;
import org.anasoid.iptvorganizer.repositories.stream.AbstractTypedCategoryRepository;
import org.anasoid.iptvorganizer.repositories.stream.BaseStreamRepository;
import org.anasoid.iptvorganizer.repositories.stream.SynchronizedItemRepository;
import org.anasoid.iptvorganizer.repositories.synch.SourceRepository;
import org.anasoid.iptvorganizer.repositories.synch.SyncLogRepository;
import org.anasoid.iptvorganizer.services.FilterService;
import org.anasoid.iptvorganizer.services.synch.SyncLockManager;
import org.anasoid.iptvorganizer.services.synch.mapper.AbstractSyncMapper;
import org.anasoid.iptvorganizer.services.synch.mapper.SynchronizedItemMapParameter;
import org.anasoid.iptvorganizer.utils.streaming.JsonStreamResult;
import org.anasoid.iptvorganizer.utils.xtream.XtreamClient;

/**
 * Background sync service using Quarkus Scheduler Syncs live streams, VOD, series, and categories
 * from Xtream API
 */
@Log
public abstract class AbstractSynchronizer<T extends BaseStream & StreamLike> {

  private static final int LOG_THRESHOLD = 1000;

  @Inject SourceRepository sourceRepository;

  @Inject SyncLogRepository syncLogRepository;

  @Inject XtreamClient xtreamClient;

  @Inject FilterService filterService;

  @Inject SyncLockManager syncLockManager;

  @Inject SyncConfig syncConfig;

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
    log.info("Syncing " + type.getStreamTypeName() + " for source: " + source.getName());

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

          log.fine(() -> "Processing " + type.getStreamTypeName() + " ID: " + item.getExternalId());

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
          String.format(
              "%s - Fetched %d items from source %s (%dkb read)",
              type.getStreamTypeName(), fetchedIds.size(), source.getName(), bytesRead / 1000));

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
            "Deleting "
                + toDeleteIds.size()
                + " items, first ID: "
                + toDeleteIds.iterator().next());
        for (Integer toDeleteId : toDeleteIds) {
          synchronizedItemRepository.deleteByExternalId(toDeleteId, source.getId());
        }
      }

      long duration = (System.currentTimeMillis() - startTime) / 1000;
      log.info(
          String.format(
              "%s - Added: %d, Updated: %d, Deleted: %d, Duration(s): %d, Bytes read: %dkb",
              type.getStreamTypeName(),
              added,
              updated,
              toDeleteIds.size(),
              duration,
              bytesRead / 1000));

      syncLog.setItemsAdded(syncLog.getItemsAdded() + added);
      syncLog.setItemsUpdated(syncLog.getItemsUpdated() + updated);
      syncLog.setItemsDeleted(syncLog.getItemsDeleted() + toDeleteIds.size());

      finalizeSyncLog(syncLog, syncStartTime, null);
    } catch (Exception e) {
      log.log(Level.SEVERE, "Error syncing " + type.getStreamTypeName() + ": ", e);
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
