package org.anasoid.iptvorganizer.services.synch.synchronizer;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
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

                        // Get all existing streams for this source
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
                  .map(item -> item.getExternalId())
                  .collect()
                  .asList()
                  .onItem()
                  .call(
                      fetchedStreamIds -> {
                        LOGGER.info(
                            String.format(
                                "%s - Fetched %d items from source %s",
                                type.getStreamTypeName(),
                                fetchedStreamIds.size(),
                                source.getName()));
                        return synchronizedItemRepository
                            .findExternalIdsBySourceId(source.getId())
                            .collect()
                            .asList()
                            .flatMap(
                                existingExternalIds -> {
                                  Set<Integer> toDeleteIds = new HashSet<>();
                                  for (Integer id : existingExternalIds) {
                                    if (!fetchedStreamIds.contains(id) && id != 0) {
                                      toDeleteIds.add(id);
                                    }
                                  }
                                  if (toDeleteIds.size() > 0) {
                                    LOGGER.info("IDs to delete: " + toDeleteIds.iterator().next());
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
                                  // Insert/update streams one-by-one
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
