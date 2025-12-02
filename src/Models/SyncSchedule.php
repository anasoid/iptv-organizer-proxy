<?php

declare(strict_types=1);

namespace App\Models;

use DateTime;
use RuntimeException;

/**
 * SyncSchedule Model
 *
 * Tracks sync scheduling per source and task type
 *
 * @property int $id
 * @property int $source_id
 * @property string $task_type
 * @property string $next_sync
 * @property string|null $last_sync
 * @property int $sync_interval
 * @property string $created_at
 * @property string $updated_at
 */
class SyncSchedule extends BaseModel
{
    protected string $table = 'sync_schedule';
    protected array $fillable = [
        'source_id',
        'task_type',
        'next_sync',
        'last_sync',
        'sync_interval',
    ];

    /**
     * Valid task types
     */
    const TASK_TYPES = [
        'live_categories',
        'live_streams',
        'vod_categories',
        'vod_streams',
        'series_categories',
        'series',
    ];

    /**
     * Get or create schedule entry for source and task type
     *
     * @param int $sourceId
     * @param string $taskType
     * @param int $syncInterval Default sync interval in seconds
     * @return static
     */
    public static function getOrCreate(int $sourceId, string $taskType, int $syncInterval = 3600): static
    {
        $instance = new static();
        $stmt = $instance->db->prepare(
            "SELECT * FROM {$instance->table} WHERE source_id = ? AND task_type = ? LIMIT 1"
        );
        $stmt->execute([$sourceId, $taskType]);
        $data = $stmt->fetch();

        if ($data) {
            $instance->attributes = $data;
            $instance->exists = true;
            return $instance;
        }

        // Create new entry
        $instance->source_id = $sourceId;
        $instance->task_type = $taskType;
        $instance->sync_interval = $syncInterval;
        $instance->next_sync = $instance->getCurrentTimestamp();
        $instance->last_sync = null;

        if (!$instance->save()) {
            throw new RuntimeException("Failed to create sync schedule");
        }

        return $instance;
    }

    /**
     * Check if sync is due for this schedule
     *
     * @return bool
     */
    public function isSyncDue(): bool
    {
        $now = new DateTime();
        $nextSync = new DateTime($this->next_sync);

        return $now >= $nextSync;
    }

    /**
     * Update next sync time after completion
     *
     * @return bool
     */
    public function updateNextSync(): bool
    {
        $interval = (int) ($this->sync_interval ?? 3600);
        $nextSync = new DateTime();
        $nextSync->modify("+{$interval} seconds");

        $this->last_sync = $this->getCurrentTimestamp();
        $this->next_sync = $nextSync->format('Y-m-d H:i:s');

        return $this->save();
    }

    /**
     * Get all pending syncs (due for execution)
     *
     * @return array
     */
    public static function getPending(): array
    {
        $instance = new static();
        $now = $instance->getCurrentTimestamp();

        $stmt = $instance->db->prepare(
            "SELECT * FROM {$instance->table} WHERE next_sync <= ? ORDER BY next_sync ASC"
        );
        $stmt->execute([$now]);

        $results = [];
        while ($data = $stmt->fetch()) {
            $schedule = new static();
            $schedule->attributes = $data;
            $schedule->exists = true;
            $results[] = $schedule;
        }

        return $results;
    }

    /**
     * Get schedule for specific source and task type
     *
     * @param int $sourceId
     * @param string $taskType
     * @return static|null
     */
    public static function findBySourceAndTask(int $sourceId, string $taskType): ?static
    {
        $instance = new static();
        $stmt = $instance->db->prepare(
            "SELECT * FROM {$instance->table} WHERE source_id = ? AND task_type = ? LIMIT 1"
        );
        $stmt->execute([$sourceId, $taskType]);
        $data = $stmt->fetch();

        if (!$data) {
            return null;
        }

        $instance->attributes = $data;
        $instance->exists = true;

        return $instance;
    }

    /**
     * Get all schedules for a source
     *
     * @param int $sourceId
     * @return array
     */
    public static function findBySource(int $sourceId): array
    {
        $instance = new static();
        return static::findAll(['source_id' => $sourceId]);
    }

    /**
     * Initialize schedules for a new source
     *
     * @param int $sourceId
     * @param int $syncInterval Default sync interval in seconds
     * @return void
     */
    public static function initializeForSource(int $sourceId, int $syncInterval = 3600): void
    {
        foreach (static::TASK_TYPES as $taskType) {
            static::getOrCreate($sourceId, $taskType, $syncInterval);
        }
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

        if (empty($this->attributes['task_type'])) {
            throw new RuntimeException("Task type is required");
        }

        if (!in_array($this->attributes['task_type'], static::TASK_TYPES)) {
            throw new RuntimeException("Invalid task type: " . $this->attributes['task_type']);
        }

        if (empty($this->attributes['next_sync'])) {
            throw new RuntimeException("Next sync time is required");
        }

        if (isset($this->attributes['sync_interval'])) {
            $interval = (int) $this->attributes['sync_interval'];
            if ($interval < 60) {
                throw new RuntimeException("Sync interval must be at least 60 seconds");
            }
        }

        return true;
    }
}
