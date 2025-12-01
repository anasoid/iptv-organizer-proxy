<?php

declare(strict_types=1);

namespace App\Services;

use App\Models\Filter;

/**
 * Filter Service
 *
 * Applies YAML-based filtering rules to streams and categories
 */
class FilterService
{
    private ?Filter $filter;
    private bool $hideAdultContent;

    /**
     * Constructor
     *
     * @param Filter|null $filter Filter configuration
     * @param bool $hideAdultContent Whether to hide adult content
     */
    public function __construct(?Filter $filter = null, bool $hideAdultContent = false)
    {
        $this->filter = $filter;
        $this->hideAdultContent = $hideAdultContent;
    }

    /**
     * Generate virtual favoris categories
     *
     * @return array Array of favoris categories with IDs starting at 100000
     */
    public function generateFavorisCategories(): array
    {
        if ($this->filter === null) {
            return [];
        }

        $config = $this->filter->parseYaml();
        $favoris = $config['favoris'] ?? [];

        $categories = [];
        $baseId = 100000;

        foreach ($favoris as $index => $favorisItem) {
            // Parse favoris item (can be string or array)
            if (is_string($favorisItem)) {
                // Simple format: "Sports HD"
                $categories[] = [
                    'category_id' => (string) ($baseId + $index),
                    'category_name' => $favorisItem,
                    'parent_id' => 0,
                ];
            } elseif (is_array($favorisItem)) {
                // Complex format: {name: "Sports HD", labels: ["sports", "HD"]}
                $categories[] = [
                    'category_id' => (string) ($baseId + $index),
                    'category_name' => $favorisItem['name'] ?? "Favoris " . ($index + 1),
                    'parent_id' => 0,
                ];
            }
        }

        return $categories;
    }

    /**
     * Apply filter to streams
     *
     * @param array $streams Array of stream objects
     * @param int|null $categoryId Optional category filter
     * @return array Filtered streams
     */
    public function applyToStreams(array $streams, ?int $categoryId = null): array
    {
        if (empty($streams)) {
            return [];
        }

        // Filter by favoris category if ID >= 100000
        if ($categoryId !== null && $categoryId >= 100000) {
            $streams = $this->filterByFavorisCategory($streams, $categoryId);
        }

        // Apply filter rules if filter is set
        if ($this->filter !== null) {
            $streams = $this->applyFilterRules($streams);
        }

        // Hide adult content if configured
        if ($this->hideAdultContent) {
            $streams = $this->filterAdultContent($streams);
        }

        return $streams;
    }

    /**
     * Filter streams by favoris category
     *
     * @param array $streams
     * @param int $categoryId Favoris category ID (>= 100000)
     * @return array
     */
    private function filterByFavorisCategory(array $streams, int $categoryId): array
    {
        if ($this->filter === null) {
            return $streams;
        }

        $config = $this->filter->parseYaml();
        $favoris = $config['favoris'] ?? [];

        $favorisIndex = $categoryId - 100000;

        if (!isset($favoris[$favorisIndex])) {
            return [];
        }

        $favorisItem = $favoris[$favorisIndex];

        // Get matching criteria
        $matchLabels = [];
        $matchName = null;

        if (is_string($favorisItem)) {
            $matchName = strtolower($favorisItem);
        } elseif (is_array($favorisItem)) {
            $matchName = isset($favorisItem['name']) ? strtolower($favorisItem['name']) : null;
            $matchLabels = $favorisItem['labels'] ?? [];
        }

        // Filter streams that match the favoris criteria
        return array_filter($streams, function ($stream) use ($matchLabels, $matchName) {
            $streamName = strtolower($stream->name ?? '');
            $streamLabels = !empty($stream->labels) ? explode(',', strtolower($stream->labels)) : [];

            // Match by name if specified
            if ($matchName !== null && strpos($streamName, $matchName) !== false) {
                return true;
            }

            // Match by labels if specified
            if (!empty($matchLabels)) {
                foreach ($matchLabels as $label) {
                    $label = strtolower(trim($label));
                    if (in_array($label, $streamLabels)) {
                        return true;
                    }
                }
            }

            return false;
        });
    }

    /**
     * Apply include/exclude filter rules to streams
     *
     * @param array $streams
     * @return array
     */
    private function applyFilterRules(array $streams): array
    {
        $config = $this->filter->parseYaml();
        $rules = $config['rules'] ?? [];

        $includeRules = [];
        $excludeRules = [];

        // Parse rules into include and exclude lists
        foreach ($rules as $rule) {
            if (is_string($rule)) {
                // Simple format: starts with "!" for exclude, otherwise include
                if (strpos($rule, '!') === 0) {
                    $excludeRules[] = strtolower(trim(substr($rule, 1)));
                } else {
                    $includeRules[] = strtolower(trim($rule));
                }
            } elseif (is_array($rule)) {
                // Complex format: {type: "include|exclude", pattern: "..."}
                $type = $rule['type'] ?? 'include';
                $pattern = $rule['pattern'] ?? $rule['name'] ?? '';

                if ($type === 'exclude') {
                    $excludeRules[] = strtolower(trim($pattern));
                } else {
                    $includeRules[] = strtolower(trim($pattern));
                }
            }
        }

        // Apply filters
        return array_filter($streams, function ($stream) use ($includeRules, $excludeRules) {
            $streamName = strtolower($stream->name ?? '');
            $streamLabels = !empty($stream->labels) ? explode(',', strtolower($stream->labels)) : [];

            // Check exclude rules first (they take priority)
            foreach ($excludeRules as $excludePattern) {
                if (strpos($streamName, $excludePattern) !== false) {
                    return false;
                }

                // Also check labels
                if (in_array($excludePattern, $streamLabels)) {
                    return false;
                }
            }

            // If include rules exist, stream must match at least one
            if (!empty($includeRules)) {
                $matched = false;

                foreach ($includeRules as $includePattern) {
                    if (strpos($streamName, $includePattern) !== false) {
                        $matched = true;
                        break;
                    }

                    // Also check labels
                    if (in_array($includePattern, $streamLabels)) {
                        $matched = true;
                        break;
                    }
                }

                return $matched;
            }

            // No include rules, so include by default (unless excluded above)
            return true;
        });
    }

    /**
     * Filter out adult content streams
     *
     * @param array $streams
     * @return array
     */
    private function filterAdultContent(array $streams): array
    {
        return array_filter($streams, function ($stream) {
            return !($stream->is_adult ?? false);
        });
    }

    /**
     * Check if a stream matches filter criteria
     *
     * @param object $stream Stream object
     * @return bool
     */
    public function matchesFilter($stream): bool
    {
        $result = $this->applyToStreams([$stream]);
        return !empty($result);
    }
}
