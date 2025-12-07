<?php

declare(strict_types=1);

namespace App\Models;

use RuntimeException;

/**
 * BaseStreamModel
 *
 * Base class for all stream models (LiveStream, VodStream, Series)
 * Provides common functionality for stream handling
 *
 * @property int $id
 * @property int $source_id
 * @property int|string $stream_id
 * @property string $name
 * @property string|null $category_id
 * @property string|null $category_ids
 * @property int $is_adult
 * @property string|null $labels
 * @property string|null $data
 * @property string $created_at
 * @property string $updated_at
 */
abstract class BaseStreamModel extends BaseModel
{
    protected array $fillable = [
        'source_id',
        'stream_id',
        'name',
        'category_id',
        'category_ids',
        'is_adult',
        'labels',
        'data',
    ];

    /**
     * Get streams by source
     *
     * @param int $sourceId
     * @param int|null $limit
     * @param int $offset
     * @return array
     */
    public static function getBySource(int $sourceId, ?int $limit = null, int $offset = 0): array
    {
        return static::findAll(['source_id' => $sourceId], [], $limit, $offset);
    }

    /**
     * Get streams by source and category
     *
     * @param int $sourceId
     * @param int $categoryId
     * @param int|null $limit
     * @param int $offset
     * @return array
     */
    public static function getByCategory(int $sourceId, int $categoryId, ?int $limit = null, int $offset = 0): array
    {
        return static::findAll([
            'source_id' => $sourceId,
            'category_id' => $categoryId,
        ], [], $limit, $offset);
    }

    /**
     * Get only stream IDs for a source (memory efficient)
     *
     * @param int $sourceId
     * @return array Associative array of stream IDs (id => true)
     */
    public static function getIdsBySource(int $sourceId): array
    {
        $instance = new static();
        $stmt = $instance->db->prepare("SELECT stream_id FROM {$instance->table} WHERE source_id = ?");
        $stmt->execute([$sourceId]);

        $ids = [];
        foreach ($stmt->fetchAll(\PDO::FETCH_COLUMN, 0) as $streamId) {
            $ids[$streamId] = true;
        }
        return $ids;
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
        if (empty($this->attributes['source_id'])) {
            throw new RuntimeException("Source ID is required");
        }

        if (!isset($this->attributes['stream_id'])) {
            throw new RuntimeException("Stream ID is required");
        }

        if (empty($this->attributes['name'])) {
            throw new RuntimeException("Stream name is required");
        }

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
            $existing->name = $streamData['name'] ?? $existing->name;
            $existing->category_id = $streamData['category_id'] ?? $existing->category_id;
            $existing->category_ids = isset($streamData['category_ids']) ? json_encode($streamData['category_ids']) : $existing->category_ids;
            $existing->is_adult = $streamData['is_adult'] ?? $existing->is_adult;
            $existing->labels = static::extractLabels($streamData['name'] ?? $existing->name);
            $existing->data = json_encode($streamData);
            $existing->save();

            return $existing;
        }

        $instance = new static();
        $instance->source_id = $sourceId;
        $instance->stream_id = $streamId;
        $instance->name = $streamData['name'] ?? 'Unknown';
        $instance->category_id = $streamData['category_id'] ?? 0;
        $instance->category_ids = isset($streamData['category_ids']) ? json_encode($streamData['category_ids']) : null;
        $instance->is_adult = $streamData['is_adult'] ?? 0;
        $instance->labels = static::extractLabels($streamData['name'] ?? 'Unknown');
        $instance->data = json_encode($streamData);

        if (!$instance->save()) {
            throw new RuntimeException("Failed to create stream");
        }

        return $instance;
    }

    /**
     * Extract labels from stream name
     * Implemented by subclasses with optional stream type parameter
     *
     * @param string $text
     * @return string Comma-separated labels
     */
    abstract public static function extractLabels(string $text): string;

}
