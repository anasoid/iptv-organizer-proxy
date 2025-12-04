<?php

declare(strict_types=1);

namespace App\Models;

use RuntimeException;

/**
 * Category Model
 *
 * Represents stream categories (live, vod, series)
 *
 * @property int $id
 * @property int $source_id
 * @property int|string $category_id
 * @property string $category_name
 * @property string $category_type
 * @property int|null $parent_id
 * @property string|null $labels
 * @property string $created_at
 */
class Category extends BaseModel
{
    protected string $table = 'categories';
    protected array $fillable = [
        'source_id',
        'category_id',
        'category_name',
        'category_type',
        'parent_id',
        'labels',
    ];
    protected array $dates = ['created_at'];

    /**
     * Get categories by source and type
     *
     * @param int $sourceId
     * @param string $type (live, vod, series)
     * @return array
     */
    public static function getBySourceAndType(int $sourceId, string $type): array
    {
        return static::findAll([
            'source_id' => $sourceId,
            'category_type' => $type,
        ]);
    }

    /**
     * Get only category IDs for a source and type (memory efficient)
     *
     * @param int $sourceId
     * @param string $categoryType Category type (live, vod, series)
     * @return array Associative array of category IDs (id => true)
     */
    public static function getIdsBySourceAndType(int $sourceId, string $categoryType): array
    {
        $instance = new static();
        $stmt = $instance->db->prepare("SELECT category_id FROM {$instance->table} WHERE source_id = ? AND category_type = ?");
        $stmt->execute([$sourceId, $categoryType]);
        
        $ids = [];
        foreach ($stmt->fetchAll(\PDO::FETCH_COLUMN, 0) as $categoryId) {
            $ids[$categoryId] = true;
        }
        return $ids;
    }

    /**
     * Extract labels from category name
     *
     * @param string $name
     * @return string Comma-separated labels
     */
    public static function extractLabels(string $name): string
    {
        $labels = [];

        // Extract text between brackets [ ]
        if (preg_match_all('/\[([^\]]+)\]/', $name, $matches)) {
            foreach ($matches[1] as $match) {
                $labels[] = trim($match);
            }
        }

        // Split by '-' delimiter
        $parts = explode('-', $name);
        foreach ($parts as $part) {
            // Remove brackets from the part
            $part = preg_replace('/\[[^\]]+\]/', '', $part);
            $part = trim($part);
            if (!empty($part) && !in_array($part, $labels)) {
                $labels[] = $part;
            }
        }

        // Split by '|' delimiter
        $parts = explode('|', $name);
        foreach ($parts as $part) {
            // Remove brackets from the part
            $part = preg_replace('/\[[^\]]+\]/', '', $part);
            $part = trim($part);
            if (!empty($part) && !in_array($part, $labels)) {
                $labels[] = $part;
            }
        }

        // Remove duplicates and empty values
        $labels = array_filter($labels);
        $labels = array_unique($labels);

        return implode(',', $labels);
    }

    /**
     * Find category by source, category_id, and type
     *
     * @param int $sourceId
     * @param int $categoryId
     * @param string $type
     * @return static|null
     */
    public static function findBySourceCategory(int $sourceId, int $categoryId, string $type): ?static
    {
        $instance = new static();
        $stmt = $instance->db->prepare(
            "SELECT * FROM {$instance->table}
             WHERE source_id = ? AND category_id = ? AND category_type = ?
             LIMIT 1"
        );
        $stmt->execute([$sourceId, $categoryId, $type]);
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

        // Category ID required
        if (!isset($this->attributes['category_id'])) {
            throw new RuntimeException("Category ID is required");
        }

        // Category name required
        if (empty($this->attributes['category_name'])) {
            throw new RuntimeException("Category name is required");
        }

        // Category type required and valid
        $validTypes = ['live', 'vod', 'series'];
        if (empty($this->attributes['category_type'])) {
            throw new RuntimeException("Category type is required");
        }

        if (!in_array($this->attributes['category_type'], $validTypes)) {
            throw new RuntimeException("Invalid category type. Must be: " . implode(', ', $validTypes));
        }

        return true;
    }

    /**
     * Get or create category
     *
     * @param int $sourceId
     * @param int $categoryId
     * @param string $type
     * @param string $name
     * @param int|null $parentId
     * @return static
     */
    public static function getOrCreate(
        int $sourceId,
        int $categoryId,
        string $type,
        string $name,
        ?int $parentId = null
    ): static {
        $existing = static::findBySourceCategory($sourceId, $categoryId, $type);

        if ($existing) {
            // Update name and labels if changed
            if ($existing->category_name !== $name) {
                $existing->category_name = $name;
                $existing->labels = static::extractLabels($name);
                $existing->save();
            }
            return $existing;
        }

        // Create new
        $instance = new static();
        $instance->source_id = $sourceId;
        $instance->category_id = $categoryId;
        $instance->category_type = $type;
        $instance->category_name = $name;
        $instance->parent_id = $parentId;
        $instance->labels = static::extractLabels($name);

        if (!$instance->save()) {
            throw new RuntimeException("Failed to create category");
        }

        return $instance;
    }
}
