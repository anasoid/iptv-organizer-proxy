package org.anasoid.iptvorganizer.services;

import org.anasoid.iptvorganizer.models.*;
import org.anasoid.iptvorganizer.models.http.HttpOptions;
import org.anasoid.iptvorganizer.repositories.*;
import org.anasoid.iptvorganizer.services.streaming.HttpStreamingService;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * Background sync service using Quarkus Scheduler
 * Syncs live streams, VOD, series, and categories from Xtream API
 */
@ApplicationScoped
public class SyncService {

    private static final Logger LOGGER = Logger.getLogger(SyncService.class.getName());
    private static final int BATCH_SIZE = 100;
    private static final int GC_THRESHOLD = 1000;

    @Inject
    SourceRepository sourceRepository;

    @Inject
    SyncScheduleRepository syncScheduleRepository;

    @Inject
    SyncLogRepository syncLogRepository;

    @Inject
    LiveStreamRepository liveStreamRepository;

    @Inject
    VodStreamRepository vodStreamRepository;

    @Inject
    SeriesRepository seriesRepository;

    @Inject
    CategoryRepository categoryRepository;

    @Inject
    HttpStreamingService httpStreamingService;

    @Inject
    FilterService filterService;

    @Inject
    LabelExtractor labelExtractor;

    /**
     * Scheduled task to check for sources needing sync
     * Runs every 5 minutes by default, configurable via sync.check.interval
     */
    @Scheduled(cron = "0 */5 * * * ?", identity = "sync-daemon")
    public void scheduledSync() {
        LOGGER.info("Starting scheduled sync check");

        sourceRepository.findSourcesNeedingSync()
            .onItem()
            .transformToUni(source -> syncSource(source)
                .onFailure()
                .recoverWithUni(failure -> {
                    LOGGER.severe("Failed to sync source " + source.getId() + ": " + failure.getMessage());
                    return Uni.createFrom().voidItem();
                })
            )
            .merge()
            .collect()
            .asList()
            .subscribe()
            .with(
                items -> LOGGER.info("Scheduled sync completed: " + items.size() + " sources processed"),
                failure -> LOGGER.severe("Scheduled sync failed: " + failure.getMessage())
            );
    }

    /**
     * Sync a single source with concurrent sync prevention
     */
    private Uni<Void> syncSource(Source source) {
        // Try to acquire lock first
        return sourceRepository.acquireSyncLock(source.getId())
            .onItem()
            .transformToUni(lockAcquired -> {
                if (!lockAcquired) {
                    LOGGER.info("Source " + source.getId() + " is already being synced, skipping");
                    return Uni.createFrom().voidItem();
                }

                LocalDateTime syncStartTime = LocalDateTime.now();
                SyncLog syncLog = SyncLog.builder()
                    .sourceId(source.getId())
                    .syncType("full")
                    .startedAt(syncStartTime)
                    .status("running")
                    .itemsAdded(0)
                    .itemsUpdated(0)
                    .itemsDeleted(0)
                    .build();

                return syncLogRepository.insert(syncLog)
                    .onItem()
                    .transformToUni(logId -> {
                        syncLog.setId(logId);
                        source.setLastSync(syncStartTime);

                        return performFullSync(source, syncLog, syncStartTime)
                            .eventually(() -> sourceRepository.releaseSyncLock(source.getId()));
                    })
                    .onFailure()
                    .recoverWithUni(failure -> {
                        // Release lock on failure
                        return sourceRepository.releaseSyncLock(source.getId())
                            .onItem()
                            .transformToUni(v -> Uni.createFrom().failure(failure));
                    });
            });
    }

    /**
     * Perform full sync of a source: categories, live streams, VOD, series
     */
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

    /**
     * Sync categories for a source
     */
    private Uni<Source> syncCategories(Source source) {
        LOGGER.info("Syncing categories for source: " + source.getName());

        // Build category API URLs for live, VOD, and series
        String liveUrl = buildApiUrl(source, "get_live_categories");
        String vodUrl = buildApiUrl(source, "get_vod_categories");
        String seriesUrl = buildApiUrl(source, "get_series_categories");

        HttpOptions httpOptions = new HttpOptions();
        httpOptions.setTimeout(30);
        httpOptions.setMaxRetries(3);

        // Concatenate all category streams
        Multi<Map> allCategories = Multi.createBy().concatenating().streams(
            httpStreamingService.streamJson(liveUrl, Map.class, httpOptions)
                .onItem().invoke(cat -> LOGGER.fine("Received live category: " + cat)),
            httpStreamingService.streamJson(vodUrl, Map.class, httpOptions)
                .onItem().invoke(cat -> LOGGER.fine("Received VOD category: " + cat)),
            httpStreamingService.streamJson(seriesUrl, Map.class, httpOptions)
                .onItem().invoke(cat -> LOGGER.fine("Received series category: " + cat))
        );

        return allCategories
            .collect()
            .asList()
            .replaceWith(source);
    }

    /**
     * Sync live streams for a source
     */
    private Uni<Source> syncLiveStreams(Source source, SyncLog syncLog) {
        LOGGER.info("Syncing live streams for source: " + source.getName());

        String url = buildApiUrl(source, "get_live_streams");
        HttpOptions httpOptions = new HttpOptions();
        httpOptions.setTimeout(30);
        httpOptions.setMaxRetries(3);

        AtomicInteger itemCount = new AtomicInteger(0);
        List<LiveStream> batch = new ArrayList<>();

        return httpStreamingService.streamJson(url, Map.class, httpOptions)
            .onItem()
            .invoke(streamData -> {
                LiveStream stream = mapToLiveStream(source, streamData);
                batch.add(stream);

                if (batch.size() >= BATCH_SIZE) {
                    processBatch(batch, "live", syncLog);
                    batch.clear();

                    // Explicit GC every BATCH_SIZE items
                    if (itemCount.addAndGet(BATCH_SIZE) % GC_THRESHOLD == 0) {
                        System.gc();
                        LOGGER.fine("GC called after " + itemCount.get() + " live streams");
                    }
                }
            })
            .collect()
            .asList()
            .onItem()
            .invoke(items -> {
                // Process remaining items
                if (!batch.isEmpty()) {
                    processBatch(batch, "live", syncLog);
                }
                LOGGER.info("Synced " + (itemCount.get() + batch.size()) + " live streams");
            })
            .replaceWith(source);
    }

    /**
     * Sync VOD streams for a source
     */
    private Uni<Source> syncVod(Source source, SyncLog syncLog) {
        LOGGER.info("Syncing VOD for source: " + source.getName());

        String url = buildApiUrl(source, "get_vod_streams");
        HttpOptions httpOptions = new HttpOptions();
        httpOptions.setTimeout(30);
        httpOptions.setMaxRetries(3);

        AtomicInteger itemCount = new AtomicInteger(0);
        List<VodStream> batch = new ArrayList<>();

        return httpStreamingService.streamJson(url, Map.class, httpOptions)
            .onItem()
            .invoke(streamData -> {
                VodStream stream = mapToVodStream(source, streamData);
                batch.add(stream);

                if (batch.size() >= BATCH_SIZE) {
                    processBatch(batch, "vod", syncLog);
                    batch.clear();

                    if (itemCount.addAndGet(BATCH_SIZE) % GC_THRESHOLD == 0) {
                        System.gc();
                        LOGGER.fine("GC called after " + itemCount.get() + " VOD streams");
                    }
                }
            })
            .collect()
            .asList()
            .onItem()
            .invoke(items -> {
                if (!batch.isEmpty()) {
                    processBatch(batch, "vod", syncLog);
                }
                LOGGER.info("Synced " + (itemCount.get() + batch.size()) + " VOD streams");
            })
            .replaceWith(source);
    }

    /**
     * Sync series for a source
     */
    private Uni<Source> syncSeries(Source source, SyncLog syncLog) {
        LOGGER.info("Syncing series for source: " + source.getName());

        String url = buildApiUrl(source, "get_series");
        HttpOptions httpOptions = new HttpOptions();
        httpOptions.setTimeout(30);
        httpOptions.setMaxRetries(3);

        AtomicInteger itemCount = new AtomicInteger(0);
        List<Series> batch = new ArrayList<>();

        return httpStreamingService.streamJson(url, Map.class, httpOptions)
            .onItem()
            .invoke(seriesData -> {
                Series series = mapToSeries(source, seriesData);
                batch.add(series);

                if (batch.size() >= BATCH_SIZE) {
                    processBatch(batch, "series", syncLog);
                    batch.clear();

                    if (itemCount.addAndGet(BATCH_SIZE) % GC_THRESHOLD == 0) {
                        System.gc();
                        LOGGER.fine("GC called after " + itemCount.get() + " series");
                    }
                }
            })
            .collect()
            .asList()
            .onItem()
            .invoke(items -> {
                if (!batch.isEmpty()) {
                    processBatch(batch, "series", syncLog);
                }
                LOGGER.info("Synced " + (itemCount.get() + batch.size()) + " series");
            })
            .replaceWith(source);
    }

    /**
     * Process a batch of items - insert into database
     */
    @SuppressWarnings("unchecked")
    private <T> void processBatch(List<T> batch, String type, SyncLog syncLog) {
        // This is synchronous batch processing
        // In production, this should be wrapped in a Panache transaction
        batch.forEach(item -> {
            if (item instanceof LiveStream) {
                // Save to database
                liveStreamRepository.insert((LiveStream) item)
                    .subscribe()
                    .with(
                        id -> syncLog.setItemsAdded(syncLog.getItemsAdded() + 1),
                        error -> LOGGER.warning("Failed to insert live stream: " + error.getMessage())
                    );
            } else if (item instanceof VodStream) {
                vodStreamRepository.insert((VodStream) item)
                    .subscribe()
                    .with(
                        id -> syncLog.setItemsAdded(syncLog.getItemsAdded() + 1),
                        error -> LOGGER.warning("Failed to insert VOD stream: " + error.getMessage())
                    );
            } else if (item instanceof Series) {
                seriesRepository.insert((Series) item)
                    .subscribe()
                    .with(
                        id -> syncLog.setItemsAdded(syncLog.getItemsAdded() + 1),
                        error -> LOGGER.warning("Failed to insert series: " + error.getMessage())
                    );
            }
        });
    }

    /**
     * Finalize sync log and update source
     */
    private Uni<Void> finalizeSyncLog(Source source, SyncLog syncLog, LocalDateTime syncStartTime, Throwable error) {
        LocalDateTime syncEndTime = LocalDateTime.now();

        if (error != null) {
            syncLog.setStatus("failed");
            syncLog.setErrorMessage(error.getMessage());
            LOGGER.severe("Sync failed for source " + source.getId() + ": " + error.getMessage());
        } else {
            syncLog.setStatus("completed");
            LOGGER.info("Sync completed for source " + source.getId());
        }

        syncLog.setCompletedAt(syncEndTime);
        long durationSeconds = java.time.temporal.ChronoUnit.SECONDS.between(syncStartTime, syncEndTime);
        syncLog.setDurationSeconds((int) durationSeconds);

        // Update source with next sync time (lock will be released separately)
        source.setNextSync(LocalDateTime.now().plusMinutes(source.getSyncInterval() != null ? source.getSyncInterval() : 5));

        return syncLogRepository.update(syncLog)
            .onItem()
            .transformToUni(v -> sourceRepository.update(source));
    }

    /**
     * Build Xtream API URL
     */
    private String buildApiUrl(Source source, String action) {
        String baseUrl = source.getUrl().replaceAll("/$", "");
        return String.format("%s/player_api.php?action=%s&username=%s&password=%s",
            baseUrl, action, source.getUsername(), source.getPassword());
    }

    /**
     * Map API response to LiveStream entity
     */
    private LiveStream mapToLiveStream(Source source, Map<?, ?> data) {
        LiveStream stream = new LiveStream();
        stream.setSourceId(source.getId());
        stream.setStreamId(getIntValue(data, "stream_id"));
        stream.setNum(getIntValue(data, "num"));
        stream.setName(getStringValue(data, "name"));
        stream.setCategoryId(getIntValue(data, "category_id"));
        stream.setIsAdult(getBooleanValue(data, "is_adult"));

        // Extract and set labels from stream name
        List<String> labels = labelExtractor.extractLabels(stream.getName());
        stream.setLabels(labelExtractor.labelsToString(labels));

        stream.setData(convertMapToJson(data));
        return stream;
    }

    /**
     * Map API response to VodStream entity
     */
    private VodStream mapToVodStream(Source source, Map<?, ?> data) {
        VodStream stream = new VodStream();
        stream.setSourceId(source.getId());
        stream.setStreamId(getIntValue(data, "stream_id"));
        stream.setNum(getIntValue(data, "num"));
        stream.setName(getStringValue(data, "name"));
        stream.setCategoryId(getIntValue(data, "category_id"));
        stream.setIsAdult(getBooleanValue(data, "is_adult"));

        // Extract and set labels
        List<String> labels = labelExtractor.extractLabels(stream.getName());
        stream.setLabels(labelExtractor.labelsToString(labels));

        stream.setData(convertMapToJson(data));
        return stream;
    }

    /**
     * Map API response to Series entity
     */
    private Series mapToSeries(Source source, Map<?, ?> data) {
        Series series = new Series();
        series.setSourceId(source.getId());
        series.setStreamId(getIntValue(data, "series_id"));
        series.setNum(getIntValue(data, "num"));
        series.setName(getStringValue(data, "name"));
        series.setCategoryId(getIntValue(data, "category_id"));
        series.setIsAdult(getBooleanValue(data, "is_adult"));

        // Extract and set labels
        List<String> labels = labelExtractor.extractLabels(series.getName());
        series.setLabels(labelExtractor.labelsToString(labels));

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
}
