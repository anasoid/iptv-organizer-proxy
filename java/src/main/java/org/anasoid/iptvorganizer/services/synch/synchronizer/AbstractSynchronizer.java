package org.anasoid.iptvorganizer.services.synch.synchronizer;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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
  public Uni<Source> syncCategories(Source source, SyncLog syncLog) {
    Multi<Map> categoriesStream = typedCategoryRepository.fetchExternalData(source);

    return syncItem(source, syncLog, typedCategoryRepository, getMapper()::mapToCategory, "stream");
  }

  public Uni<Source> syncStreams(Source source, SyncLog syncLog) {

    return syncItem(source, syncLog, streamRepository, getMapper()::mapToStream, "stream");
  }

  /** Generic stream synchronization method for all stream types */
  public <R extends SourcedEntity> Uni<Source> syncItem(
      Source source,
      SyncLog syncLog,
      SynchronizedItemRepository<R> synchronizedItemRepository,
      Function<SynchronizedItemMapParameter, R> mapper,
      String itemType) {
    StreamType type = getStreamType();
    LOGGER.info("Syncing " + type.getStreamTypeName() + " for source: " + source.getName());

    AtomicInteger count = new AtomicInteger(0);
    AtomicInteger added = new AtomicInteger(0);
    AtomicInteger updated = new AtomicInteger(0);

    // Thread-safe set to accumulate external IDs incrementally during processing
    // This prevents holding all BaseStream objects in memory until collection completes
    AtomicReference<Set<Integer>> fetchedStreamIds =
        new AtomicReference<>(Collections.synchronizedSet(new HashSet<>()));

    return typedCategoryRepository
        .getOrCreateUnknownCategory(source.getId())
        .flatMap(
            unknownCategoryId -> {
              Multi<Map> streamsData = synchronizedItemRepository.fetchExternalData(source);
              return streamsData
                  .map(
                      m ->
                          mapper.apply(
                              SynchronizedItemMapParameter.builder()
                                  .unknownCategoryId(unknownCategoryId)
                                  .source(source)
                                  .data(m)
                                  .build()))
                  .onItem()
                  .call(
                      item -> {
                        // Assign num ordering
                        item.setNum(count.incrementAndGet());

                        // Capture external ID immediately for deletion comparison
                        // This happens DURING processing, not AFTER, preventing memory accumulation
                        Integer externalId = item.getExternalId();
                        if (externalId != null) {
                          fetchedStreamIds.get().add(externalId);
                        }

                        // Process the item with database operations
                        return Uni.createFrom()
                            .item(item)
                            .onItem()
                            .call(
                                stream -> {
                                  LOGGER.fine(
                                      () ->
                                          "Processing "
                                              + type.getStreamTypeName()
                                              + " ID: "
                                              + stream.getExternalId());
                                  return synchronizedItemRepository
                                      .insertOrUpdateByExternalId(stream)
                                      .onItem()
                                      .invoke(
                                          inserted -> {
                                            if (inserted) {
                                              updated.incrementAndGet();
                                            } else {
                                              added.incrementAndGet();
                                            }
                                          });
                                });
                      })
                  // Collect to complete - this waits for all items to be processed
                  // Objects are released for GC immediately after processing (no retention)
                  // Memory: Only the Set<Integer> of IDs is retained (~800KB for 100K items)
                  .collect()
                  .last()
                  .onItem()
                  .ifNull()
                  .continueWith(() -> null)
                  .onItem()
                  .transformToUni(
                      ignored -> {
                        // All items processed, all IDs captured in the Set
                        Set<Integer> fetchedIds = fetchedStreamIds.get();

                        LOGGER.info(
                            String.format(
                                "%s - Fetched %d items from source %s",
                                type.getStreamTypeName(), fetchedIds.size(), source.getName()));

                        // Find items to delete by comparing with existing database IDs
                        return synchronizedItemRepository
                            .findExternalIdsBySourceId(source.getId())
                            .collect()
                            .asList()
                            .flatMap(
                                existingExternalIds -> {
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
                                  }

                                  LOGGER.info(
                                      String.format(
                                          "%s - Added: %d, Updated: %d, Deleted: %d",
                                          type.getStreamTypeName(),
                                          added.get(),
                                          updated.get(),
                                          toDeleteIds.size()));

                                  syncLog.setItemsAdded(syncLog.getItemsAdded() + added.get());
                                  syncLog.setItemsUpdated(
                                      syncLog.getItemsUpdated() + updated.get());
                                  syncLog.setItemsDeleted(
                                      syncLog.getItemsDeleted() + toDeleteIds.size());

                                  if (toDeleteIds.isEmpty()) {
                                    return Uni.createFrom().item(Collections.emptyList());
                                  }

                                  // Delete obsolete items
                                  return Multi.createFrom()
                                      .iterable(toDeleteIds)
                                      .onItem()
                                      .transformToUni(
                                          toDeleteId ->
                                              synchronizedItemRepository.deleteByExternalId(
                                                  toDeleteId, source.getId()))
                                      .merge()
                                      .collect()
                                      .asList();
                                });
                      });
            })
        .replaceWith(source);
  }

  abstract AbstractSyncMapper<T> getMapper();

  abstract StreamType getStreamType();
}
