<?php

declare(strict_types=1);

namespace App\Models;

use RuntimeException;

/**
 * SyncLog Model
 *
 * Tracks synchronization operations with upstream sources
 *
 * @property int $id
 * @property int $source_id
 * @property string $sync_type
 * @property string $started_at
 * @property string|null $completed_at
 * @property int|null $duration_seconds
 * @property string $status
 * @property int $items_added
 * @property int $items_updated
 * @property int $items_deleted
 * @property string|null $error_message
 */
class SyncLog extends BaseModel
{
    protected string $table = 'sync_logs';
    protected array $fillable = [
        'source_id',
        'sync_type',
        'started_at',
        'completed_at',
        'duration_seconds',
        'status',
        'items_added',
        'items_updated',
        'items_deleted',
        'error_message',
    ];
    protected array $dates = [];

    /**
     * Valid sync types
     */
    const SYNC_TYPES = [
        'live_categories',
        'live_streams',
        'vod_categories',
        'vod_streams',
        'series_categories',
        'series',
    ];

    /**
     * Valid statuses
     */
    const STATUS_RUNNING = 'running';
    const STATUS_COMPLETED = 'completed';
    const STATUS_FAILED = 'failed';

    /**
     * Log sync start
     *
     * @param int $sourceId
     * @param string $syncType
     * @return static
     */
    public static function logSyncStart(int $sourceId, string $syncType): static
    {
        $instance = new static();
        $instance->source_id = $sourceId;
        $instance->sync_type = $syncType;
        $instance->started_at = $instance->getCurrentTimestamp();
        $instance->status = static::STATUS_RUNNING;
        $instance->items_added = 0;
        $instance->items_updated = 0;
        $instance->items_deleted = 0;

        if (!$instance->save()) {
            throw new RuntimeException("Failed to create sync log");
        }

        return $instance;
    }

    /**
     * Log sync completion
     *
     * @param string $status (completed or failed)
     * @param array $stats ['added' => int, 'updated' => int, 'deleted' => int]
     * @param string|null $errorMessage
     * @return bool
     */
    public function logSyncComplete(string $status, array $stats = [], ?string $errorMessage = null): bool
    {
        $validStatuses = [static::STATUS_COMPLETED, static::STATUS_FAILED];
        if (!in_array($status, $validStatuses)) {
            throw new RuntimeException("Invalid status: {$status}");
        }

        // Calculate duration in seconds
        $startTime = strtotime($this->started_at);
        $endTime = time();
        $duration = max(1, $endTime - $startTime); // At least 1 second

        $this->completed_at = $this->getCurrentTimestamp();
        $this->duration_seconds = $duration;
        $this->status = $status;
        $this->items_added = $stats['added'] ?? 0;
        $this->items_updated = $stats['updated'] ?? 0;
        $this->items_deleted = $stats['deleted'] ?? 0;
        $this->error_message = $errorMessage;

        return $this->save();
    }

    /**
     * Get latest sync for source and type
     *
     * @param int $sourceId
     * @param string $syncType
     * @return static|null
     */
    public static function getLatest(int $sourceId, string $syncType): ?static
    {
        $instance = new static();
        $stmt = $instance->db->prepare(
            "SELECT * FROM {$instance->table}
             WHERE source_id = ? AND sync_type = ?
             ORDER BY started_at DESC
             LIMIT 1"
        );
        $stmt->execute([$sourceId, $syncType]);
        $data = $stmt->fetch();

        if (!$data) {
            return null;
        }

        $instance->attributes = $data;
        $instance->exists = true;

        return $instance;
    }

    /**
     * Get sync history for source
     *
     * @param int $sourceId
     * @param string|null $syncType
     * @param int $limit
     * @return array
     */
    public static function getHistory(int $sourceId, ?string $syncType = null, int $limit = 50): array
    {
        $conditions = ['source_id' => $sourceId];
        if ($syncType !== null) {
            $conditions['sync_type'] = $syncType;
        }

        return static::findAll(
            $conditions,
            ['started_at' => 'DESC'],
            $limit
        );
    }

    /**
     * Check if sync is currently running for source and type
     *
     * @param int $sourceId
     * @param string $syncType
     * @return bool
     */
    public static function isSyncRunning(int $sourceId, string $syncType): bool
    {
        $instance = new static();
        $stmt = $instance->db->prepare(
            "SELECT COUNT(*) FROM {$instance->table}
             WHERE source_id = ? AND sync_type = ? AND status = ?
             LIMIT 1"
        );
        $stmt->execute([$sourceId, $syncType, static::STATUS_RUNNING]);

        return (int) $stmt->fetchColumn() > 0;
    }

    /**
     * Get sync statistics for source
     *
     * @param int $sourceId
     * @param string|null $syncType
     * @return array
     */
    public static function getStats(int $sourceId, ?string $syncType = null): array
    {
        $instance = new static();

        $sql = "SELECT
                    COUNT(*) as total_syncs,
                    SUM(CASE WHEN status = 'completed' THEN 1 ELSE 0 END) as completed_syncs,
                    SUM(CASE WHEN status = 'failed' THEN 1 ELSE 0 END) as failed_syncs,
                    SUM(items_added) as total_added,
                    SUM(items_updated) as total_updated,
                    SUM(items_deleted) as total_deleted
                FROM {$instance->table}
                WHERE source_id = ?";

        $params = [$sourceId];

        if ($syncType !== null) {
            $sql .= " AND sync_type = ?";
            $params[] = $syncType;
        }

        $stmt = $instance->db->prepare($sql);
        $stmt->execute($params);

        return $stmt->fetch() ?: [];
    }

    /**
     * Validate model data
     *
     * @return bool
     */
    protected function validate(): bool
    {
        if (empty($this->attributes['source_id'])) {
            throw new RuntimeException("Source ID is required");
        }

        if (empty($this->attributes['sync_type'])) {
            throw new RuntimeException("Sync type is required");
        }

        if (!in_array($this->attributes['sync_type'], static::SYNC_TYPES)) {
            throw new RuntimeException("Invalid sync type: " . $this->attributes['sync_type']);
        }

        if (empty($this->attributes['started_at'])) {
            throw new RuntimeException("Started at is required");
        }

        if (!empty($this->attributes['status'])) {
            $validStatuses = [static::STATUS_RUNNING, static::STATUS_COMPLETED, static::STATUS_FAILED];
            if (!in_array($this->attributes['status'], $validStatuses)) {
                throw new RuntimeException("Invalid status: " . $this->attributes['status']);
            }
        }

        return true;
    }
}
