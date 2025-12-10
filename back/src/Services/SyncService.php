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
use Generator;

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
                $fetchedCategoryIds[$categoryId] = true;

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
                    
                    // Only update if values changed
                    $hasChanges = false;
                    if ($existing->category_name !== $categoryData['category_name']) {
                        $existing->category_name = $categoryData['category_name'];
                        $hasChanges = true;
                    }
                    if ($existing->parent_id !== ($categoryData['parent_id'] ?? null)) {
                        $existing->parent_id = $categoryData['parent_id'] ?? null;
                        $hasChanges = true;
                    }
                    if ($existing->labels !== $labels) {
                        $existing->labels = $labels;
                        $hasChanges = true;
                    }
                    
                    if ($hasChanges) {
                        $existing->save();
                        $stats['updated']++;
                    }
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
                
                unset($categoryData, $existingCategories, $labels);
            }

            unset($categories);

            // Load existing category IDs and delete those not in fetched list
            $dbCategoryIds = Category::getIdsBySourceAndType($this->source->id, $categoryType);
            foreach ($dbCategoryIds as $id => $true) {
                if (!isset($fetchedCategoryIds[$id])) {
                    $category = new Category();
                    $category->id = $id;
                    $category->delete();
                    $stats['deleted']++;
                }
            }

            unset($dbCategoryIds, $fetchedCategoryIds);

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
            fn() => $this->client->streamLiveCategories()
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
            fn() => $this->client->streamVodCategories()
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
            fn() => $this->client->streamSeriesCategories()
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

            $streams = $this->client->streamLiveStreams();
            $fetchedStreamIds = [];

            foreach ($streams as $streamData) {
                $streamId = $streamData['stream_id'] ?? $streamData['num'] ?? null;
                
                // Skip if stream_id is missing
                if (!$streamId) {
                    $this->logger->warning('Live stream missing stream_id', [
                        'num' => $streamData['num'] ?? null,
                        'name' => $streamData['name'] ?? 'Unknown',
                    ]);
                    unset($streamData);
                    continue;
                }
                
                $fetchedStreamIds[$streamId] = true;

                // Handle category_id - if null, use Unknown category
                $categoryId = null;
                if (array_key_exists('category_id', $streamData)) {
                    $categoryId = $streamData['category_id'];
                }

                // If category_id is null, get/create Unknown category
                if ($categoryId === null) {
                    $unknownCatId = Category::getOrCreateUnknownCategory($this->source->id, 'live');
                    $categoryId = $unknownCatId;
                    $this->logger->info('Live stream assigned to Unknown category', [
                        'stream_id' => $streamId,
                        'name' => $streamData['name'] ?? 'Unknown',
                    ]);
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
                $streamDataJson = json_encode($streamData);

                if (!empty($existingStreams)) {
                    // Update existing - only if values changed
                    $existing = $existingStreams[0];
                    $hasChanges = false;

                    if ($existing->name !== ($streamData['name'] ?? null)) {
                        $existing->name = $streamData['name'] ?? null;
                        $hasChanges = true;
                    }
                    if ($existing->category_id !== $categoryId) {
                        $existing->category_id = $categoryId;
                        $hasChanges = true;
                    }
                    if ($existing->category_ids !== $categoryIds) {
                        $existing->category_ids = $categoryIds;
                        $hasChanges = true;
                    }
                    if ($existing->is_adult !== ($isAdult ? 1 : 0)) {
                        $existing->is_adult = $isAdult ? 1 : 0;
                        $hasChanges = true;
                    }
                    if ($existing->labels !== $labels) {
                        $existing->labels = $labels;
                        $hasChanges = true;
                    }
                    if ($existing->data !== $streamDataJson) {
                        $existing->data = $streamDataJson;
                        $hasChanges = true;
                    }
                    
                    if ($hasChanges) {
                        $existing->save();
                        $stats['updated']++;
                    }
                    unset($existing);
                } else {
                    // Insert new
                    $stream = new LiveStream();
                    $stream->source_id = $this->source->id;
                    $stream->stream_id = $streamId;
                    $stream->name = $streamData['name'] ?? null;
                    $stream->category_id = $categoryId;
                    $stream->category_ids = $categoryIds;
                    $stream->is_adult = $isAdult ? 1 : 0;
                    $stream->labels = $labels;
                    $stream->data = $streamDataJson;
                    $stream->save();
                    $stats['added']++;
                    unset($stream);
                }
                
                unset($streamData, $existingStreams, $labels, $categoryIds, $streamDataJson);
            }

            unset($streams);

            // Load existing stream IDs and delete those not in fetched list
            $dbStreamIds = LiveStream::getIdsBySource($this->source->id);
            foreach ($dbStreamIds as $id => $true) {
                if (!isset($fetchedStreamIds[$id])) {
                    $stream = new LiveStream();
                    $stream->id = $id;
                    $stream->delete();
                    $stats['deleted']++;
                }
            }

            unset($dbStreamIds, $fetchedStreamIds);

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

            $streams = $this->client->streamVodStreams();
            $fetchedStreamIds = [];

            foreach ($streams as $streamData) {
                $streamId = $streamData['stream_id'] ?? $streamData['num'] ?? null;
                
                // Skip if stream_id is missing
                if (!$streamId) {
                    $this->logger->warning('VOD stream missing stream_id', [
                        'num' => $streamData['num'] ?? null,
                        'name' => $streamData['name'] ?? 'Unknown',
                    ]);
                    unset($streamData);
                    continue;
                }
                
                $fetchedStreamIds[$streamId] = true;

                // Handle category_id - if null, use Unknown category
                $categoryId = null;
                if (array_key_exists('category_id', $streamData)) {
                    $categoryId = $streamData['category_id'];
                }

                // If category_id is null, get/create Unknown category
                if ($categoryId === null) {
                    $unknownCatId = Category::getOrCreateUnknownCategory($this->source->id, 'vod');
                    $categoryId = $unknownCatId;
                    $this->logger->info('VOD stream assigned to Unknown category', [
                        'stream_id' => $streamId,
                        'name' => $streamData['name'] ?? 'Unknown',
                    ]);
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
                $streamDataJson = json_encode($streamData);

                if (!empty($existingStreams)) {
                    // Update existing - only if values changed
                    $existing = $existingStreams[0];
                    $hasChanges = false;

                    if ($existing->name !== ($streamData['name'] ?? null)) {
                        $existing->name = $streamData['name'] ?? null;
                        $hasChanges = true;
                    }
                    if ($existing->category_id !== $categoryId) {
                        $existing->category_id = $categoryId;
                        $hasChanges = true;
                    }
                    if ($existing->category_ids !== $categoryIds) {
                        $existing->category_ids = $categoryIds;
                        $hasChanges = true;
                    }
                    if ($existing->is_adult !== ($isAdult ? 1 : 0)) {
                        $existing->is_adult = $isAdult ? 1 : 0;
                        $hasChanges = true;
                    }
                    if ($existing->labels !== $labels) {
                        $existing->labels = $labels;
                        $hasChanges = true;
                    }
                    if ($existing->data !== $streamDataJson) {
                        $existing->data = $streamDataJson;
                        $hasChanges = true;
                    }
                    
                    if ($hasChanges) {
                        $existing->save();
                        $stats['updated']++;
                    }
                    unset($existing);
                } else {
                    $stream = new VodStream();
                    $stream->source_id = $this->source->id;
                    $stream->stream_id = $streamId;
                    $stream->name = $streamData['name'] ?? null;
                    $stream->category_id = $categoryId;
                    $stream->category_ids = $categoryIds;
                    $stream->is_adult = $isAdult ? 1 : 0;
                    $stream->labels = $labels;
                    $stream->data = $streamDataJson;
                    $stream->save();
                    $stats['added']++;
                    unset($stream);
                }

                unset($streamData, $existingStreams, $labels, $categoryIds, $streamDataJson, $categoryId);
            }

            unset($streams);

            // Load existing stream IDs and delete those not in fetched list
            $dbStreamIds = VodStream::getIdsBySource($this->source->id);
            foreach ($dbStreamIds as $id => $true) {
                if (!isset($fetchedStreamIds[$id])) {
                    $stream = new VodStream();
                    $stream->id = $id;
                    $stream->delete();
                    $stats['deleted']++;
                }
            }

            unset($dbStreamIds, $fetchedStreamIds);

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

            $seriesList = $this->client->streamSeries();
            $fetchedStreamIds = [];

            foreach ($seriesList as $streamData) {
                $streamId = $streamData['series_id'] ?? $streamData['num'] ?? null;
                
                // Skip if stream_id is missing
                if (!$streamId) {
                    $this->logger->warning('Series missing stream_id', [
                        'num' => $streamData['num'] ?? null,
                        'name' => $streamData['name'] ?? 'Unknown',
                    ]);
                    unset($streamData);
                    continue;
                }
                
                $fetchedStreamIds[$streamId] = true;

                // Handle category_id - if null, use Unknown category
                $categoryId = null;
                if (array_key_exists('category_id', $streamData)) {
                    $categoryId = $streamData['category_id'];
                }

                // If category_id is null, get/create Unknown category
                if ($categoryId === null) {
                    $unknownCatId = Category::getOrCreateUnknownCategory($this->source->id, 'series');
                    $categoryId = $unknownCatId;
                    $this->logger->info('Series assigned to Unknown category', [
                        'stream_id' => $streamId,
                        'name' => $streamData['name'] ?? 'Unknown',
                    ]);
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
                $streamDataJson = json_encode($streamData);

                if (!empty($existingStreams)) {
                    // Update existing - only if values changed
                    $existing = $existingStreams[0];
                    $hasChanges = false;

                    if ($existing->name !== ($streamData['name'] ?? null)) {
                        $existing->name = $streamData['name'] ?? null;
                        $hasChanges = true;
                    }
                    if ($existing->category_id !== $categoryId) {
                        $existing->category_id = $categoryId;
                        $hasChanges = true;
                    }
                    if ($existing->category_ids !== $categoryIds) {
                        $existing->category_ids = $categoryIds;
                        $hasChanges = true;
                    }
                    if ($existing->is_adult !== ($isAdult ? 1 : 0)) {
                        $existing->is_adult = $isAdult ? 1 : 0;
                        $hasChanges = true;
                    }
                    if ($existing->labels !== $labels) {
                        $existing->labels = $labels;
                        $hasChanges = true;
                    }
                    if ($existing->data !== $streamDataJson) {
                        $existing->data = $streamDataJson;
                        $hasChanges = true;
                    }
                    
                    if ($hasChanges) {
                        $existing->save();
                        $stats['updated']++;
                    }
                    unset($existing);
                } else {
                    $stream = new Series();
                    $stream->source_id = $this->source->id;
                    $stream->stream_id = $streamId;
                    $stream->name = $streamData['name'] ?? null;
                    $stream->category_id = $categoryId;
                    $stream->category_ids = $categoryIds;
                    $stream->is_adult = $isAdult ? 1 : 0;
                    $stream->labels = $labels;
                    $stream->data = $streamDataJson;
                    $stream->save();
                    $stats['added']++;
                    unset($stream);
                }

                unset($streamData, $existingStreams, $labels, $categoryIds, $streamDataJson, $categoryId);
            }

            unset($seriesList);

            // Load existing stream IDs and delete those not in fetched list
            $dbStreamIds = Series::getIdsBySource($this->source->id);
            foreach ($dbStreamIds as $id => $true) {
                if (!isset($fetchedStreamIds[$id])) {
                    $stream = new Series();
                    $stream->id = $id;
                    $stream->delete();
                    $stats['deleted']++;
                }
            }

            unset($dbStreamIds, $fetchedStreamIds);

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
