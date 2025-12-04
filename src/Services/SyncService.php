<?php

declare(strict_types=1);

namespace App\Services;

use App\Models\Source;
use App\Models\Category;
use App\Models\LiveStream;
use App\Models\VodStream;
use App\Models\Series;
use App\Models\SyncLog;
use App\Services\Xtream\XtreamClient;
use Monolog\Logger;
use Monolog\Handler\StreamHandler;
use Exception;
use PDO;

/**
 * Synchronization Service
 *
 * Handles synchronization of categories and streams from upstream Xtream servers
 */
class SyncService
{
    private Source $source;
    private XtreamClient $client;
    private Logger $logger;
    private PDO $db;

    /**
     * Constructor
     *
     * @param Source $source Source to sync from
     * @param XtreamClient $client Xtream API client
     * @param Logger|null $logger Optional logger instance
     */
    public function __construct(Source $source, XtreamClient $client, ?Logger $logger = null)
    {
        $this->source = $source;
        $this->client = $client;
        $this->logger = $logger ?? $this->createDefaultLogger();
        $this->db = \App\Database\Connection::getConnection();
    }

    /**
     * Create default logger
     *
     * @return Logger
     */
    private function createDefaultLogger(): Logger
    {
        $logger = new Logger('SyncService');
        $logger->pushHandler(new StreamHandler('php://stderr', Logger::INFO));
        return $logger;
    }

    /**
     * Check if sync is already running for this source and type
     *
     * @param string $syncType
     * @return bool
     */
    private function isSyncRunning(string $syncType): bool
    {
        $runningSyncs = SyncLog::findAll([
            'source_id' => $this->source->id,
            'sync_type' => $syncType,
            'status' => 'running',
        ]);

        if (!empty($runningSyncs)) {
            $this->logger->warning('Sync already running', [
                'source_id' => $this->source->id,
                'sync_type' => $syncType,
            ]);
            return true;
        }

        return false;
    }

    /**
     * Start sync log
     *
     * @param string $syncType
     * @return SyncLog
     */
    private function startSyncLog(string $syncType): SyncLog
    {
        return SyncLog::logSyncStart($this->source->id, $syncType);
    }

    /**
     * Sync categories by type
     *
     * @param string $syncType Type of sync (live_categories, vod_categories, series_categories)
     * @param string $categoryType Category type (live, vod, series)
     * @param string $labelType Label type (live, movie, series)
     * @param callable $getFn Callback to fetch categories from client
     * @return array Statistics (added, updated, deleted)
     */
    private function syncCategories(
        string $syncType,
        string $categoryType,
        string $labelType,
        callable $getFn
    ): array {
        if ($this->isSyncRunning($syncType)) {
            return ['error' => 'Sync already running'];
        }

        $syncLog = $this->startSyncLog($syncType);
        $stats = ['added' => 0, 'updated' => 0, 'deleted' => 0];

        try {
            $this->db->beginTransaction();

            $categories = $getFn();
            $fetchedCategoryIds = [];

            foreach ($categories as $categoryData) {
                $categoryId = $categoryData['category_id'];
                $fetchedCategoryIds[] = $categoryId;

                $existingCategories = Category::findAll([
                    'source_id' => $this->source->id,
                    'category_id' => $categoryId,
                    'category_type' => $categoryType,
                ]);

                $labels = LabelExtractor::extractLabels(
                    $categoryData['category_name'] ?? '',
                    $labelType
                );

                if (!empty($existingCategories)) {
                    $existing = $existingCategories[0];
                    $existing->category_name = $categoryData['category_name'];
                    $existing->parent_id = $categoryData['parent_id'] ?? null;
                    $existing->labels = $labels;
                    $existing->save();
                    $stats['updated']++;
                } else {
                    $category = new Category();
                    $category->source_id = $this->source->id;
                    $category->category_id = $categoryId;
                    $category->category_name = $categoryData['category_name'];
                    $category->category_type = $categoryType;
                    $category->parent_id = $categoryData['parent_id'] ?? null;
                    $category->labels = $labels;
                    $category->save();
                    $stats['added']++;
                }
            }

            // Delete missing categories
            $allCategories = Category::findAll([
                'source_id' => $this->source->id,
                'category_type' => $categoryType,
            ]);
            foreach ($allCategories as $category) {
                if (!in_array($category->category_id, $fetchedCategoryIds)) {
                    $category->delete();
                    $stats['deleted']++;
                }
            }

            $this->db->commit();

            $syncLog->logSyncComplete(SyncLog::STATUS_COMPLETED, $stats);

            $this->logger->info(ucfirst(str_replace('_', ' ', $syncType)) . ' completed', $stats);

            return $stats;
        } catch (Exception $e) {
            if ($this->db->inTransaction()) {
                $this->db->rollBack();
            }

            $syncLog->logSyncComplete(SyncLog::STATUS_FAILED, $stats, $e->getMessage());

            $this->logger->error(ucfirst(str_replace('_', ' ', $syncType)) . ' failed', [
                'error' => $e->getMessage(),
            ]);

            throw $e;
        }
    }

    /**
     * Sync live categories
     *
     * @return array Statistics (added, updated, deleted)
     */
    public function syncLiveCategories(): array
    {
        return $this->syncCategories(
            'live_categories',
            'live',
            'live',
            fn() => $this->client->getLiveCategories()
        );
    }

    /**
     * Sync VOD categories
     *
     * @return array Statistics (added, updated, deleted)
     */
    public function syncVodCategories(): array
    {
        return $this->syncCategories(
            'vod_categories',
            'vod',
            'movie',
            fn() => $this->client->getVodCategories()
        );
    }

    /**
     * Sync series categories
     *
     * @return array Statistics (added, updated, deleted)
     */
    public function syncSeriesCategories(): array
    {
        return $this->syncCategories(
            'series_categories',
            'series',
            'series',
            fn() => $this->client->getSeriesCategories()
        );
    }

    /**
     * Sync live streams
     *
     * @return array Statistics (added, updated, deleted)
     */
    public function syncLiveStreams(): array
    {
        $syncType = 'live_streams';

        if ($this->isSyncRunning($syncType)) {
            return ['error' => 'Sync already running'];
        }

        $syncLog = $this->startSyncLog($syncType);
        $stats = ['added' => 0, 'updated' => 0, 'deleted' => 0, 'missing_category_id' => []];

        try {
            $this->db->beginTransaction();

            $streams = $this->client->getLiveStreams();
            $fetchedStreamIds = [];

            foreach ($streams as $streamData) {
                $streamId = $streamData['stream_id'] ?? $streamData['num'] ?? null;
                
                // Skip if stream_id is missing
                if (!$streamId) {
                    $this->logger->warning('Live stream missing stream_id', [
                        'num' => $streamData['num'] ?? null,
                        'name' => $streamData['name'] ?? 'Unknown',
                    ]);
                    continue;
                }
                
                $fetchedStreamIds[] = $streamId;

                // Check if category_id is provided
                if (!isset($streamData['category_id'])) {
                    $missingInfo = [
                        'stream_id' => $streamId,
                        'num' => $streamData['num'] ?? null,
                        'name' => $streamData['name'] ?? 'Unknown',
                    ];
                    $stats['missing_category_id'][] = $missingInfo;
                    $this->logger->warning('Live stream missing category_id', $missingInfo);
                    continue;
                }

                $labels = LabelExtractor::extractLabels(
                    $streamData['name'] ?? '',
                    'live'
                );

                $isAdult = LabelExtractor::isAdultContent($streamData['name'] ?? '');

                $existingStreams = LiveStream::findAll([
                    'source_id' => $this->source->id,
                    'stream_id' => $streamId,
                ]);

                $categoryIds = isset($streamData['category_ids']) && is_array($streamData['category_ids'])
                    ? json_encode($streamData['category_ids'])
                    : null;

                if (!empty($existingStreams)) {
                    // Update existing
                    $existing = $existingStreams[0];
                    $existing->name = $streamData['name'] ?? null;
                    $existing->category_id = $streamData['category_id'] ?? null;
                    $existing->category_ids = $categoryIds;
                    $existing->is_adult = $isAdult ? 1 : 0;
                    $existing->labels = $labels;
                    $existing->data = json_encode($streamData);
                    $existing->save();
                    $stats['updated']++;
                } else {
                    // Insert new
                    $stream = new LiveStream();
                    $stream->source_id = $this->source->id;
                    $stream->stream_id = $streamId;
                    $stream->name = $streamData['name'] ?? null;
                    $stream->category_id = $streamData['category_id'] ?? null;
                    $stream->category_ids = $categoryIds;
                    $stream->is_adult = $isAdult ? 1 : 0;
                    $stream->labels = $labels;
                    $stream->data = json_encode($streamData);
                    $stream->save();
                    $stats['added']++;
                }
            }

            // Delete missing streams
            $allStreams = LiveStream::findAll(['source_id' => $this->source->id]);
            foreach ($allStreams as $stream) {
                if (!in_array($stream->stream_id, $fetchedStreamIds)) {
                    $stream->delete();
                    $stats['deleted']++;
                }
            }

            $this->db->commit();

            $syncLog->logSyncComplete(SyncLog::STATUS_COMPLETED, $stats);

            $this->logger->info('Live streams sync completed', $stats);

            return $stats;
        } catch (Exception $e) {
            if ($this->db->inTransaction()) {
                $this->db->rollBack();
            }

            $syncLog->logSyncComplete(SyncLog::STATUS_FAILED, $stats, $e->getMessage());

            $this->logger->error('Live streams sync failed', [
                'error' => $e->getMessage(),
            ]);

            throw $e;
        }
    }

    /**
     * Sync VOD streams
     *
     * @return array Statistics (added, updated, deleted)
     */
    public function syncVodStreams(): array
    {
        $syncType = 'vod_streams';

        if ($this->isSyncRunning($syncType)) {
            return ['error' => 'Sync already running'];
        }

        $syncLog = $this->startSyncLog($syncType);
        $stats = ['added' => 0, 'updated' => 0, 'deleted' => 0, 'missing_category_id' => []];

        try {
            $this->db->beginTransaction();

            $streams = $this->client->getVodStreams();
            $fetchedStreamIds = [];

            foreach ($streams as $streamData) {
                $streamId = $streamData['stream_id'] ?? $streamData['num'] ?? null;
                
                // Skip if stream_id is missing
                if (!$streamId) {
                    $this->logger->warning('VOD stream missing stream_id', [
                        'num' => $streamData['num'] ?? null,
                        'name' => $streamData['name'] ?? 'Unknown',
                    ]);
                    continue;
                }
                
                $fetchedStreamIds[] = $streamId;

                // Check if category_id is provided
                if (!isset($streamData['category_id'])) {
                    $missingInfo = [
                        'stream_id' => $streamId,
                        'num' => $streamData['num'] ?? null,
                        'name' => $streamData['name'] ?? 'Unknown',
                    ];
                    $stats['missing_category_id'][] = $missingInfo;
                    $this->logger->warning('VOD stream missing category_id', $missingInfo);
                    continue;
                }

                $labels = LabelExtractor::extractLabels(
                    $streamData['name'] ?? '',
                    'movie'
                );

                $isAdult = LabelExtractor::isAdultContent($streamData['name'] ?? '');

                $existingStreams = VodStream::findAll([
                    'source_id' => $this->source->id,
                    'stream_id' => $streamId,
                ]);

                $categoryIds = isset($streamData['category_ids']) && is_array($streamData['category_ids'])
                    ? json_encode($streamData['category_ids'])
                    : null;

                if (!empty($existingStreams)) {
                    $existing = $existingStreams[0];
                    $existing->name = $streamData['name'] ?? null;
                    $existing->category_id = $streamData['category_id'] ?? null;
                    $existing->category_ids = $categoryIds;
                    $existing->is_adult = $isAdult ? 1 : 0;
                    $existing->labels = $labels;
                    $existing->data = json_encode($streamData);
                    $existing->save();
                    $stats['updated']++;
                } else {
                    $stream = new VodStream();
                    $stream->source_id = $this->source->id;
                    $stream->stream_id = $streamId;
                    $stream->name = $streamData['name'] ?? null;
                    $stream->category_id = $streamData['category_id'] ?? null;
                    $stream->category_ids = $categoryIds;
                    $stream->is_adult = $isAdult ? 1 : 0;
                    $stream->labels = $labels;
                    $stream->data = json_encode($streamData);
                    $stream->save();
                    $stats['added']++;
                }
            }

            // Delete missing streams
            $allStreams = VodStream::findAll(['source_id' => $this->source->id]);
            foreach ($allStreams as $stream) {
                if (!in_array($stream->stream_id, $fetchedStreamIds)) {
                    $stream->delete();
                    $stats['deleted']++;
                }
            }

            $this->db->commit();

            $syncLog->logSyncComplete(SyncLog::STATUS_COMPLETED, $stats);

            $this->logger->info('VOD streams sync completed', $stats);

            return $stats;
        } catch (Exception $e) {
            if ($this->db->inTransaction()) {
                $this->db->rollBack();
            }

            $syncLog->logSyncComplete(SyncLog::STATUS_FAILED, $stats, $e->getMessage());

            $this->logger->error('VOD streams sync failed', [
                'error' => $e->getMessage(),
            ]);

            throw $e;
        }
    }

    /**
     * Sync series
     *
     * @return array Statistics (added, updated, deleted)
     */
    public function syncSeries(): array
    {
        $syncType = 'series';

        if ($this->isSyncRunning($syncType)) {
            return ['error' => 'Sync already running'];
        }

        $syncLog = $this->startSyncLog($syncType);
        $stats = ['added' => 0, 'updated' => 0, 'deleted' => 0, 'missing_category_id' => []];

        try {
            $this->db->beginTransaction();

            $seriesList = $this->client->getSeries();
            $fetchedStreamIds = [];

            foreach ($seriesList as $streamData) {
                $streamId = $streamData['series_id'] ?? $streamData['num'] ?? null;
                
                // Skip if stream_id is missing
                if (!$streamId) {
                    $this->logger->warning('Series missing stream_id', [
                        'num' => $streamData['num'] ?? null,
                        'name' => $streamData['name'] ?? 'Unknown',
                    ]);
                    continue;
                }
                
                $fetchedStreamIds[] = $streamId;

                // Check if category_id is provided
                if (!isset($streamData['category_id'])) {
                    $missingInfo = [
                        'stream_id' => $streamId,
                        'num' => $streamData['num'] ?? null,
                        'name' => $streamData['name'] ?? 'Unknown',
                    ];
                    $stats['missing_category_id'][] = $missingInfo;
                    $this->logger->warning('Series missing category_id', $missingInfo);
                    continue;
                }

                $labels = LabelExtractor::extractLabels(
                    $streamData['name'] ?? '',
                    'series'
                );

                $isAdult = LabelExtractor::isAdultContent($streamData['name'] ?? '');

                $existingStreams = Series::findAll([
                    'source_id' => $this->source->id,
                    'stream_id' => $streamId,
                ]);

                $categoryIds = isset($streamData['category_ids']) && is_array($streamData['category_ids'])
                    ? json_encode($streamData['category_ids'])
                    : null;

                if (!empty($existingStreams)) {
                    $existing = $existingStreams[0];
                    $existing->name = $streamData['name'] ?? null;
                    $existing->category_id = $streamData['category_id'] ?? null;
                    $existing->category_ids = $categoryIds;
                    $existing->is_adult = $isAdult ? 1 : 0;
                    $existing->labels = $labels;
                    $existing->data = json_encode($streamData);
                    $existing->save();
                    $stats['updated']++;
                } else {
                    $stream = new Series();
                    $stream->source_id = $this->source->id;
                    $stream->stream_id = $streamId;
                    $stream->name = $streamData['name'] ?? null;
                    $stream->category_id = $streamData['category_id'] ?? null;
                    $stream->category_ids = $categoryIds;
                    $stream->is_adult = $isAdult ? 1 : 0;
                    $stream->labels = $labels;
                    $stream->data = json_encode($streamData);
                    $stream->save();
                    $stats['added']++;
                }
            }

            // Delete missing streams
            $allStreams = Series::findAll(['source_id' => $this->source->id]);
            foreach ($allStreams as $stream) {
                if (!in_array($stream->stream_id, $fetchedStreamIds)) {
                    $stream->delete();
                    $stats['deleted']++;
                }
            }

            $this->db->commit();

            $syncLog->logSyncComplete(SyncLog::STATUS_COMPLETED, $stats);

            $this->logger->info('Series sync completed', $stats);

            return $stats;
        } catch (Exception $e) {
            if ($this->db->inTransaction()) {
                $this->db->rollBack();
            }

            $syncLog->logSyncComplete(SyncLog::STATUS_FAILED, $stats, $e->getMessage());

            $this->logger->error('Series sync failed', [
                'error' => $e->getMessage(),
            ]);

            throw $e;
        }
    }
}
