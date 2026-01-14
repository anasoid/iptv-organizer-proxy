package org.anasoid.iptvorganizer.services.synch.synchronizer;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import org.anasoid.iptvorganizer.models.entity.Source;
import org.anasoid.iptvorganizer.models.entity.SyncLog;
import org.anasoid.iptvorganizer.models.entity.stream.BaseStream;
import org.anasoid.iptvorganizer.models.entity.stream.Category;
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
import org.anasoid.iptvorganizer.services.synch.mapper.SynchMapper.CategoryMappingResult;
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
  public Uni<Void> syncCategories(Source source) {
    Multi<Map> categoriesStream = typedCategoryRepository.fetchExternalData(source);

    return categoriesStream
        .collect()
        .asList()
        .flatMap(categoryMaps -> processCategorySync(source, categoryMaps, typedCategoryRepository))
        .onFailure()
        .invoke(
            ex ->
                LOGGER.severe(
                    "Failed to sync "
                        + typedCategoryRepository.getType().getCategoryType()
                        + " categories: "
                        + ex.getMessage()));
  }

  /** Process category synchronization: insert/update new, delete obsolete */
  private Uni<Void> processCategorySync(
      Source source,
      List<Map> categoryMaps,
      AbstractTypedCategoryRepository typedCategoryRepository) {
    CategoryMappingResult mappingResult =
        synchMapper.mapCategoryData(
            source, categoryMaps, typedCategoryRepository.getType().getCategoryType());

    return typedCategoryRepository
        .findBySourceId(source.getId())
        .collect()
        .asList()
        .flatMap(
            existingCategories ->
                syncAndDeleteCategories(
                    mappingResult.categories,
                    existingCategories,
                    typedCategoryRepository,
                    mappingResult.fetchedIds));
  }

  /** Sync (insert/update) and delete obsolete categories */
  private Uni<Void> syncAndDeleteCategories(
      List<Category> newCategories,
      List<Category> existingCategories,
      AbstractTypedCategoryRepository typedCategoryRepository,
      Set<Integer> fetchedIds) {
    // Create a map of existing categories by ID for quick lookup
    Map<Integer, Category> existingMap = new HashMap<>();
    for (Category cat : existingCategories) {
      if (cat.getType().equals(typedCategoryRepository.getType().getCategoryType())) {
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
                return typedCategoryRepository.update(category);
              } else {
                return typedCategoryRepository.insert(category).replaceWithVoid();
              }
            })
        .collect()
        .asList()
        .flatMap(
            v -> deleteObsoleteCategories(existingCategories, typedCategoryRepository, fetchedIds));
  }

  /** Delete obsolete categories that are no longer in the API response */
  private Uni<Void> deleteObsoleteCategories(
      List<Category> existingCategories,
      AbstractTypedCategoryRepository typedCategoryRepository,
      Set<Integer> fetchedIds) {
    List<Category> toDelete =
        existingCategories.stream()
            .filter(
                c ->
                    c.getType().equals(typedCategoryRepository.getType().getCategoryType())
                        && !fetchedIds.contains(c.getExternalId()))
            .toList();

    if (toDelete.isEmpty()) {
      return Uni.createFrom().voidItem();
    }

    LOGGER.info(
        "Deleting "
            + toDelete.size()
            + " obsolete "
            + typedCategoryRepository.getType().getCategoryType()
            + " categories");
    return Multi.createFrom()
        .iterable(toDelete)
        .onItem()
        .transformToUniAndConcatenate(cat -> typedCategoryRepository.delete(cat.getId()))
        .collect()
        .asList()
        .replaceWithVoid();
  }

  /** Generic stream synchronization method for all stream types */
  public Uni<Source> syncStreams(Source source, SyncLog syncLog) {
    StreamType type = getStreamType();
    LOGGER.info("Syncing " + type.getStreamTypeName() + " for source: " + source.getName());

    Integer unknownCategoryIdd = 0;
    /**
     * typedCategoryRepository .getOrCreateUnknownCategory(source.getId()) .await()
     * .atMost(Duration.ofSeconds(10));
     */
    Multi<Map> streamsData = streamRepository.fetchExternalData(source);

    return streamsData
        .map(
            m ->
                getMapper()
                    .mapToStream(
                        SynchronizedItemMapParameter.builder()
                            .unknownCategoryId(unknownCategoryIdd)
                            .source(source)
                            .data(m)
                            .build()))
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

              return Uni.createFrom()
                  .voidItem()
                  .flatMap(
                      ignored -> {
                        Set<Integer> fetchedStreamIds = new HashSet<>();
                        for (T stream : allStreams) {
                          fetchedStreamIds.add(stream.getExternalId());
                        }

                        // Get all existing streams for this source
                        return streamRepository
                            .findExternalIdsBySourceId(source.getId())
                            .collect()
                            .asList()
                            .flatMap(
                                existingExternalIds -> {

                                  // Calculate statistics
                                  AtomicInteger added = new AtomicInteger(0);
                                  AtomicInteger updated = new AtomicInteger(0);
                                  for (T stream : allStreams) {
                                    if (existingExternalIds.contains(stream.getExternalId())) {
                                      updated.incrementAndGet();
                                    } else {
                                      added.incrementAndGet();
                                    }
                                  }

                                  Set<Integer> toDeleteIds = new HashSet<>();
                                  for (Integer id : existingExternalIds) {
                                    if (!fetchedStreamIds.contains(id)) {
                                      toDeleteIds.add(id);
                                    }
                                  }

                                  syncLog.setItemsAdded(syncLog.getItemsAdded() + added.get());
                                  syncLog.setItemsUpdated(
                                      syncLog.getItemsUpdated() + updated.get());
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
                                            return ((SynchronizedItemRepository<T>)
                                                    streamRepository)
                                                .insertOrUpdateByExternalId(stream);
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
                                                .transformToUniAndConcatenate(
                                                    id ->
                                                        streamRepository.deleteByExternalId(
                                                            id, source.getId()))
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

  abstract AbstractSyncMapper<T> getMapper();

  abstract StreamType getStreamType();
}
