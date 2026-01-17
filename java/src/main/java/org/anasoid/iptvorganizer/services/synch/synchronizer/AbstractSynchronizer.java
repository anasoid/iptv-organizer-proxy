package org.anasoid.iptvorganizer.services.synch.synchronizer;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.*;
import java.util.function.Function;
import java.util.logging.Logger;
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
import org.anasoid.iptvorganizer.services.synch.mapper.SynchMapper;
import org.anasoid.iptvorganizer.services.synch.mapper.SynchronizedItemMapParameter;
import org.anasoid.iptvorganizer.utils.xtream.XtreamClient;

/**
 * Background sync service using Quarkus Scheduler Syncs live streams, VOD, series, and categories
 * from Xtream API
 */
public abstract class AbstractSynchronizer<T extends BaseStream & StreamLike> {

  private static final Logger LOGGER = Logger.getLogger(AbstractSynchronizer.class.getName());

  private static final int GC_THRESHOLD = 1000;

  @Inject SourceRepository sourceRepository;

  @Inject SyncLogRepository syncLogRepository;

  @Inject XtreamClient xtreamClient;

  @Inject FilterService filterService;

  @Inject SynchMapper synchMapper;

  @Inject SyncLockManager syncLockManager;

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
  @Transactional
  public Source syncCategories(Source source, SyncLog syncLog) {
    return syncItem(source, syncLog, typedCategoryRepository, getMapper()::mapToCategory, "stream");
  }

  @Transactional
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
    LOGGER.info("Syncing " + type.getStreamTypeName() + " for source: " + source.getName());

    int count = 0;
    int added = 0;
    int updated = 0;
    long startTime = System.currentTimeMillis();

    Set<Integer> fetchedIds = new HashSet<>();

    try {
      // Get or create unknown category
      Integer unknownCategoryId =
          typedCategoryRepository.getOrCreateUnknownCategory(source.getId());
      long startStep = System.currentTimeMillis();
      // Fetch external data
      List<Map> streamsData = synchronizedItemRepository.fetchExternalData(source);

      // Process items one by one
      for (Map data : streamsData) {
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

        LOGGER.fine(
            () -> "Processing " + type.getStreamTypeName() + " ID: " + item.getExternalId());

        // Insert or update item
        boolean wasInserted = synchronizedItemRepository.insertOrUpdateByExternalId(item);
        if (wasInserted) {
          added++;
        } else {
          updated++;
        }

        // Trigger explicit GC every 1000 items
        if (count % GC_THRESHOLD == 0) {
          long duration = System.currentTimeMillis() - startStep;
          startStep = System.currentTimeMillis();
          LOGGER.info(
              "Processed "
                  + count
                  + " items in "
                  + (duration)
                  + "ms for "
                  + type.getStreamTypeName());
          System.gc();
        }
      }

      LOGGER.info(
          String.format(
              "%s - Fetched %d items from source %s",
              type.getStreamTypeName(), fetchedIds.size(), source.getName()));

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
        LOGGER.info(
            "Deleting "
                + toDeleteIds.size()
                + " items, first ID: "
                + toDeleteIds.iterator().next());
        for (Integer toDeleteId : toDeleteIds) {
          synchronizedItemRepository.deleteByExternalId(toDeleteId, source.getId());
        }
      }

      long duration = (System.currentTimeMillis() - startTime) / 1000;
      LOGGER.info(
          String.format(
              "%s - Added: %d, Updated: %d, Deleted: %d, Duration(s): %d",
              type.getStreamTypeName(), added, updated, toDeleteIds.size(), duration));

      syncLog.setItemsAdded(syncLog.getItemsAdded() + added);
      syncLog.setItemsUpdated(syncLog.getItemsUpdated() + updated);
      syncLog.setItemsDeleted(syncLog.getItemsDeleted() + toDeleteIds.size());

    } catch (Exception e) {
      LOGGER.severe("Error syncing " + type.getStreamTypeName() + ": " + e.getMessage());
      throw e;
    }

    return source;
  }

  abstract AbstractSyncMapper<T> getMapper();

  abstract StreamType getStreamType();
}
