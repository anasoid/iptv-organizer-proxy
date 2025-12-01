<?php

declare(strict_types=1);

namespace App\Models;

use DateTime;
use RuntimeException;

/**
 * Source Model
 *
 * Represents an upstream IPTV source server
 *
 * @property int $id
 * @property string $name
 * @property string $url
 * @property string $username
 * @property string $password
 * @property int $sync_interval
 * @property string|null $last_sync
 * @property string|null $next_sync
 * @property string $sync_status
 * @property int $is_active
 * @property string $created_at
 * @property string $updated_at
 */
class Source extends BaseModel
{
    protected string $table = 'sources';
    protected array $fillable = [
        'name',
        'url',
        'username',
        'password',
        'sync_interval',
        'last_sync',
        'next_sync',
        'sync_status',
        'is_active',
    ];

    /**
     * Test connection to source server
     *
     * @return bool
     */
    public function testConnection(): bool
    {
        // This will be implemented in Task 4 (XtreamClient)
        // For now, just validate URL format
        if (!filter_var($this->url, FILTER_VALIDATE_URL)) {
            return false;
        }

        return true;
    }

    /**
     * Update sync status
     *
     * @param string $status (idle, syncing, error)
     * @return bool
     */
    public function updateSyncStatus(string $status): bool
    {
        $validStatuses = ['idle', 'syncing', 'error'];

        if (!in_array($status, $validStatuses)) {
            throw new RuntimeException("Invalid sync status: {$status}");
        }

        $this->sync_status = $status;
        return $this->save();
    }

    /**
     * Get next sync time
     *
     * @return string|null ISO 8601 datetime string
     */
    public function getNextSyncTime(): ?string
    {
        return $this->next_sync;
    }

    /**
     * Calculate and update next sync time
     *
     * @return bool
     */
    public function updateNextSyncTime(): bool
    {
        $interval = (int) ($this->sync_interval ?? 3600);
        $nextSync = new DateTime();
        $nextSync->modify("+{$interval} seconds");

        $this->last_sync = $this->getCurrentTimestamp();
        $this->next_sync = $nextSync->format('Y-m-d H:i:s');

        return $this->save();
    }

    /**
     * Check if sync is due
     *
     * @return bool
     */
    public function isSyncDue(): bool
    {
        if (empty($this->next_sync)) {
            return true;
        }

        $now = new DateTime();
        $nextSync = new DateTime($this->next_sync);

        return $now >= $nextSync;
    }

    /**
     * Get all clients for this source
     *
     * @return array
     */
    public function clients(): array
    {
        return Client::findAll(['source_id' => $this->id]);
    }

    /**
     * Get sync logs for this source
     *
     * @param int $limit
     * @return array
     */
    public function syncLogs(int $limit = 50): array
    {
        return SyncLog::findAll(
            ['source_id' => $this->id],
            ['started_at' => 'DESC'],
            $limit
        );
    }

    /**
     * Validate model data
     *
     * @return bool
     */
    protected function validate(): bool
    {
        // Name required
        if (empty($this->attributes['name'])) {
            throw new RuntimeException("Source name is required");
        }

        // URL required and valid format
        if (empty($this->attributes['url'])) {
            throw new RuntimeException("Source URL is required");
        }

        if (!filter_var($this->attributes['url'], FILTER_VALIDATE_URL)) {
            throw new RuntimeException("Invalid URL format");
        }

        // Username and password required
        if (empty($this->attributes['username'])) {
            throw new RuntimeException("Source username is required");
        }

        if (empty($this->attributes['password'])) {
            throw new RuntimeException("Source password is required");
        }

        // Sync interval must be positive
        if (isset($this->attributes['sync_interval'])) {
            $interval = (int) $this->attributes['sync_interval'];
            if ($interval < 60) {
                throw new RuntimeException("Sync interval must be at least 60 seconds");
            }
        }

        // Validate sync_status ENUM
        if (isset($this->attributes['sync_status'])) {
            $validStatuses = ['idle', 'syncing', 'error'];
            if (!in_array($this->attributes['sync_status'], $validStatuses)) {
                throw new RuntimeException("Invalid sync status");
            }
        }

        return true;
    }

    /**
     * Get all active sources
     *
     * @return array
     */
    public static function getActive(): array
    {
        return static::findAll(['is_active' => 1]);
    }

    /**
     * Activate source
     *
     * @return bool
     */
    public function activate(): bool
    {
        $this->is_active = 1;
        return $this->save();
    }

    /**
     * Deactivate source
     *
     * @return bool
     */
    public function deactivate(): bool
    {
        $this->is_active = 0;
        return $this->save();
    }
}
