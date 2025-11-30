<?php

declare(strict_types=1);

namespace App\Models;

use RuntimeException;

/**
 * LiveStream Model
 *
 * Represents live TV streams
 */
class LiveStream extends BaseModel
{
    protected string $table = 'live_streams';
    protected array $fillable = [
        'source_id',
        'stream_id',
        'name',
        'category_id',
        'category_ids',
        'is_adult',
        'labels',
        'is_active',
        'data',
    ];

    /**
     * Get streams by source
     *
     * @param int $sourceId
     * @param bool $activeOnly
     * @return array
     */
    public static function getBySource(int $sourceId, bool $activeOnly = true): array
    {
        $conditions = ['source_id' => $sourceId];
        if ($activeOnly) {
            $conditions['is_active'] = 1;
        }

        return static::findAll($conditions);
    }

    /**
     * Get streams by source and category
     *
     * @param int $sourceId
     * @param int $categoryId
     * @return array
     */
    public static function getByCategory(int $sourceId, int $categoryId): array
    {
        return static::findAll([
            'source_id' => $sourceId,
            'category_id' => $categoryId,
            'is_active' => 1,
        ]);
    }

    /**
     * Extract labels from stream name
     *
     * @param string $text
     * @param string $streamType (live, movie, series)
     * @return string Comma-separated labels
     */
    public static function extractLabels(string $text, string $streamType = 'live'): string
    {
        $labels = [];

        // Extract text between brackets [ ]
        if (preg_match_all('/\[([^\]]+)\]/', $text, $matches)) {
            foreach ($matches[1] as $match) {
                $labels[] = trim($match);
            }
        }

        // Split by '-' delimiter
        $parts = explode('-', $text);
        foreach ($parts as $part) {
            // Remove brackets from the part
            $part = preg_replace('/\[[^\]]+\]/', '', $part);
            $part = trim($part);
            if (!empty($part) && !in_array($part, $labels)) {
                $labels[] = $part;
            }
        }

        // Split by '|' delimiter
        $parts = explode('|', $text);
        foreach ($parts as $part) {
            // Remove brackets from the part
            $part = preg_replace('/\[[^\]]+\]/', '', $part);
            $part = trim($part);
            if (!empty($part) && !in_array($part, $labels)) {
                $labels[] = $part;
            }
        }

        // Add stream type as a label
        $labels[] = $streamType;

        // Remove duplicates and empty values
        $labels = array_filter($labels);
        $labels = array_unique($labels);

        return implode(',', $labels);
    }

    /**
     * Convert stream to Xtream API format
     *
     * @param string|null $proxyUrl Base proxy URL for stream URLs
     * @param string|null $username Client username
     * @param string|null $password Client password
     * @return array
     */
    public function toXtreamFormat(?string $proxyUrl = null, ?string $username = null, ?string $password = null): array
    {
        // Parse stored JSON data
        $data = [];
        if (!empty($this->data)) {
            $data = json_decode($this->data, true) ?? [];
        }

        // Build stream URL if proxy URL provided
        $streamUrl = null;
        if ($proxyUrl && $username && $password) {
            $extension = $data['container_extension'] ?? 'm3u8';
            $streamUrl = rtrim($proxyUrl, '/') . "/live/{$username}/{$password}/{$this->stream_id}.{$extension}";
        }

        // Build Xtream API response format
        $result = [
            'num' => (int) $this->stream_id,
            'name' => $this->name,
            'stream_type' => $data['stream_type'] ?? 'live',
            'stream_id' => (int) $this->stream_id,
            'stream_icon' => $data['stream_icon'] ?? '',
            'epg_channel_id' => $data['epg_channel_id'] ?? null,
            'added' => $data['added'] ?? null,
            'category_id' => (string) $this->category_id,
            'custom_sid' => $data['custom_sid'] ?? null,
            'tv_archive' => (int) ($data['tv_archive'] ?? 0),
            'direct_source' => $data['direct_source'] ?? '',
            'tv_archive_duration' => (int) ($data['tv_archive_duration'] ?? 0),
        ];

        // Add stream URL if generated
        if ($streamUrl) {
            $result['stream_url'] = $streamUrl;
        }

        return $result;
    }

    /**
     * Find stream by source and stream_id
     *
     * @param int $sourceId
     * @param int $streamId
     * @return static|null
     */
    public static function findBySourceStream(int $sourceId, int $streamId): ?static
    {
        $instance = new static();
        $stmt = $instance->db->prepare(
            "SELECT * FROM {$instance->table}
             WHERE source_id = ? AND stream_id = ?
             LIMIT 1"
        );
        $stmt->execute([$sourceId, $streamId]);
        $data = $stmt->fetch();

        if (!$data) {
            return null;
        }

        $instance->attributes = $data;
        $instance->exists = true;

        return $instance;
    }

    /**
     * Validate model data
     *
     * @return bool
     */
    protected function validate(): bool
    {
        // Source ID required
        if (empty($this->attributes['source_id'])) {
            throw new RuntimeException("Source ID is required");
        }

        // Stream ID required
        if (!isset($this->attributes['stream_id'])) {
            throw new RuntimeException("Stream ID is required");
        }

        // Name required
        if (empty($this->attributes['name'])) {
            throw new RuntimeException("Stream name is required");
        }

        // Category ID required
        if (!isset($this->attributes['category_id'])) {
            throw new RuntimeException("Category ID is required");
        }

        return true;
    }

    /**
     * Get or create stream
     *
     * @param int $sourceId
     * @param int $streamId
     * @param array $streamData
     * @return static
     */
    public static function getOrCreate(int $sourceId, int $streamId, array $streamData): static
    {
        $existing = static::findBySourceStream($sourceId, $streamId);

        if ($existing) {
            // Update stream data
            $existing->name = $streamData['name'] ?? $existing->name;
            $existing->category_id = $streamData['category_id'] ?? $existing->category_id;
            $existing->category_ids = isset($streamData['category_ids']) ? json_encode($streamData['category_ids']) : $existing->category_ids;
            $existing->is_adult = $streamData['is_adult'] ?? $existing->is_adult;
            $existing->labels = static::extractLabels($streamData['name'] ?? $existing->name, 'live');
            $existing->data = json_encode($streamData);
            $existing->is_active = 1;
            $existing->save();

            return $existing;
        }

        // Create new
        $instance = new static();
        $instance->source_id = $sourceId;
        $instance->stream_id = $streamId;
        $instance->name = $streamData['name'] ?? 'Unknown';
        $instance->category_id = $streamData['category_id'] ?? 0;
        $instance->category_ids = isset($streamData['category_ids']) ? json_encode($streamData['category_ids']) : null;
        $instance->is_adult = $streamData['is_adult'] ?? 0;
        $instance->labels = static::extractLabels($streamData['name'] ?? 'Unknown', 'live');
        $instance->is_active = 1;
        $instance->data = json_encode($streamData);

        if (!$instance->save()) {
            throw new RuntimeException("Failed to create stream");
        }

        return $instance;
    }
}
