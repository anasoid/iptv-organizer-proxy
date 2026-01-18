package org.anasoid.iptvorganizer.services.synch.synchronizer;

import jakarta.inject.Inject;
import java.io.IOException;
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
  public Source syncCategories(Source source, SyncLog syncLog) {
    return syncItem(source, syncLog, typedCategoryRepository, getMapper()::mapToCategory, "stream");
  }

  public Source syncStreams(Source source, SyncLog syncLog) {
    return syncItem(source, syncLog, streamRepository, getMapper()::mapToStream, "stream");
  }

  /** Generic stream synchronization method for all stream types */
  public <R extends SourcedEntity> Source syncItem(
      Source source,
      SyncLog syncLog,
      SynchronizedItemRepository<R> synchronizedItemRepository,
      Function<SynchronizedItemMapParameter, R> mapper,
      String itemType) {
    StreamType type = getStreamType();
    log.info("Syncing " + type.getStreamTypeName() + " for source: " + source.getName());

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
              "%s - Fetched %d items from source %s (%d bytes read)",
              type.getStreamTypeName(), fetchedIds.size(), source.getName(), bytesRead));

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
              "%s - Added: %d, Updated: %d, Deleted: %d, Duration(s): %d, Bytes read: %d",
              type.getStreamTypeName(), added, updated, toDeleteIds.size(), duration, bytesRead));

      syncLog.setItemsAdded(syncLog.getItemsAdded() + added);
      syncLog.setItemsUpdated(syncLog.getItemsUpdated() + updated);
      syncLog.setItemsDeleted(syncLog.getItemsDeleted() + toDeleteIds.size());

    } catch (Exception e) {
      log.log(Level.SEVERE, "Error syncing " + type.getStreamTypeName() + ": ", e);
      throw e;
    }

    return source;
  }

  abstract AbstractSyncMapper<T> getMapper();

  abstract StreamType getStreamType();
}
