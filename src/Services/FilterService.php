<?php

declare(strict_types=1);

namespace App\Services;

use App\Models\Filter;
use Symfony\Component\Yaml\Yaml;
use RuntimeException;

/**
 * Filter Service
 *
 * Applies YAML-based filtering with include/exclude rules and favoris virtual categories
 *
 * Filter structure has two separate sections:
 * - rules: Include/exclude rules for filtering streams
 * - favoris: Virtual category definitions for creating favorite groups
 */
class FilterService
{
    private ?object $filter;
    private bool $hideAdultContent;
    private ?array $parsedFilter = null;

    /**
     * Constructor
     *
     * @param Filter|object|null $filter The filter model (null = no filter applied)
     * @param bool $hideAdultContent Whether to hide adult content
     */
    public function __construct(?object $filter = null, bool $hideAdultContent = false)
    {
        $this->filter = $filter;
        $this->hideAdultContent = $hideAdultContent;
    }

    /**
     * Parse filter YAML configuration using symfony/yaml
     *
     * @return array Parsed filter with 'rules' and 'favoris' keys
     * @throws RuntimeException
     */
    private function parseFilter(): array
    {
        if ($this->parsedFilter !== null) {
            return $this->parsedFilter;
        }

        if ($this->filter === null) {
            return ['rules' => [], 'favoris' => []];
        }

        try {
            $config = Yaml::parse($this->filter->filter_config);

            if (!is_array($config)) {
                throw new RuntimeException('Filter configuration must be a YAML object');
            }

            $this->parsedFilter = [
                'rules' => $config['rules'] ?? [],
                'favoris' => $config['favoris'] ?? [],
            ];

            return $this->parsedFilter;
        } catch (\Exception $e) {
            throw new RuntimeException("Failed to parse filter YAML: " . $e->getMessage());
        }
    }

    /**
     * Parse comma-separated labels into lowercase array
     *
     * @param string $labelString Comma-separated labels
     * @return array Array of lowercase labels
     */
    private function parseLabels(string $labelString): array
    {
        if (empty($labelString)) {
            return [];
        }

        return array_map(
            fn($label) => strtolower(trim($label)),
            explode(',', $labelString)
        );
    }

    /**
     * Check if a stream matches given match criteria
     *
     * Match structure:
     * {
     *   categories: { by_name: [...], by_labels: [...] },
     *   channels: { by_name: [...], by_labels: [...] }
     * }
     *
     * @param array $stream Stream data
     * @param array $match Match criteria
     * @return bool True if stream matches criteria
     */
    private function matchStream(array $stream, array $match): bool
    {
        $match = array_merge(
            ['categories' => ['by_name' => [], 'by_labels' => []], 'channels' => ['by_name' => [], 'by_labels' => []]],
            $match
        );

        // Check stream/channel by name
        if (!empty($match['channels']['by_name'])) {
            $streamName = strtolower($stream['name'] ?? '');
            foreach ($match['channels']['by_name'] as $pattern) {
                if (stripos($streamName, $pattern) !== false) {
                    return true;
                }
            }
        }

        // Check stream/channel by labels
        if (!empty($match['channels']['by_labels'])) {
            $streamLabels = $this->parseLabels($stream['labels'] ?? '');
            foreach ($match['channels']['by_labels'] as $label) {
                if (in_array(strtolower($label), $streamLabels, true)) {
                    return true;
                }
            }
        }

        // Check category by name
        if (!empty($match['categories']['by_name'])) {
            $categoryName = strtolower($stream['category_name'] ?? '');
            foreach ($match['categories']['by_name'] as $pattern) {
                if (stripos($categoryName, $pattern) !== false) {
                    return true;
                }
            }
        }

        // Check category by labels
        if (!empty($match['categories']['by_labels'])) {
            $categoryLabels = $this->parseLabels($stream['category_labels'] ?? '');
            foreach ($match['categories']['by_labels'] as $label) {
                if (in_array(strtolower($label), $categoryLabels, true)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Apply filters to streams with priority order:
     * 1. Adult content filtering (if enabled) - FIRST PRIORITY
     * 2. Include/exclude rules (if filter assigned)
     * 3. Favoris category filtering (if categoryId >= 100000)
     *
     * @param array $streams Array of streams
     * @param int|null $categoryId Optional category ID for favoris filtering
     * @return array Filtered streams
     */
    public function applyToStreams(array $streams, ?int $categoryId = null): array
    {
        // Handle favoris categories (ID >= 100000)
        if ($categoryId !== null && $categoryId >= 100000) {
            return $this->filterByFavorisCategory($streams, $categoryId);
        }

        // Apply adult content filter first (highest priority)
        $filtered = $this->filterAdultContent($streams);

        // Apply include/exclude rules if filter is assigned
        if ($this->filter !== null) {
            $filtered = $this->applyFilterRules($filtered);
        }

        return $filtered;
    }

    /**
     * Apply include/exclude rules to streams
     *
     * Rules structure:
     * {
     *   name: "rule name",
     *   type: "include" or "exclude",
     *   match: { categories: {...}, channels: {...} }
     * }
     *
     * Exclude rules have priority over include rules
     *
     * @param array $streams Array of streams
     * @return array Filtered streams
     */
    private function applyFilterRules(array $streams): array
    {
        $config = $this->parseFilter();
        $rules = $config['rules'] ?? [];

        if (empty($rules)) {
            return $streams;
        }

        // Separate rules by type
        $excludeRules = [];
        $includeRules = [];

        foreach ($rules as $rule) {
            if (is_array($rule)) {
                $type = $rule['type'] ?? 'include';
                $match = $rule['match'] ?? [];

                if ($type === 'exclude') {
                    $excludeRules[] = $match;
                } else {
                    $includeRules[] = $match;
                }
            }
        }

        // Filter streams
        return array_filter($streams, function (array $stream) use ($excludeRules, $includeRules) {
            // Check exclude rules first (they have priority)
            foreach ($excludeRules as $excludeMatch) {
                if ($this->matchStream($stream, $excludeMatch)) {
                    return false; // Reject stream
                }
            }

            // If include rules exist, stream must match at least one
            if (!empty($includeRules)) {
                foreach ($includeRules as $includeMatch) {
                    if ($this->matchStream($stream, $includeMatch)) {
                        return true; // Accept stream
                    }
                }
                return false; // Didn't match any include rule
            }

            // No include rules, accept non-excluded streams
            return true;
        });
    }

    /**
     * Filter out adult content streams
     *
     * @param array $streams Array of streams
     * @return array Filtered streams
     */
    private function filterAdultContent(array $streams): array
    {
        if (!$this->hideAdultContent) {
            return $streams;
        }

        return array_filter(
            $streams,
            fn($stream) => !($stream['is_adult'] ?? false)
        );
    }

    /**
     * Generate virtual categories from favoris configuration
     *
     * Each favoris gets an ID starting from 100000
     *
     * Favoris structure:
     * {
     *   name: "favoris name",
     *   target_group: "virtual category name",
     *   match: { categories: {...}, channels: {...} }
     * }
     *
     * @return array Array of virtual category objects
     */
    public function generateFavorisCategories(): array
    {
        if ($this->filter === null) {
            return [];
        }

        $config = $this->parseFilter();
        $favoris = $config['favoris'] ?? [];

        if (empty($favoris)) {
            return [];
        }

        $categories = [];

        foreach ($favoris as $index => $favRule) {
            if (!is_array($favRule)) {
                continue;
            }

            $categoryId = 100000 + $index;
            $categoryName = $favRule['target_group'] ?? $favRule['name'] ?? '';

            if (empty($categoryName)) {
                continue;
            }

            $categories[] = [
                'category_id' => $categoryId,
                'category_name' => $categoryName,
                'parent_id' => 0,
            ];
        }

        return $categories;
    }

    /**
     * Filter streams by a specific favoris category
     *
     * @param array $streams Array of streams
     * @param int $favorisCategoryId Favoris category ID (100000+)
     * @return array Filtered streams matching the favoris criteria
     */
    public function filterByFavorisCategory(array $streams, int $favorisCategoryId): array
    {
        if ($this->filter === null) {
            return [];
        }

        $config = $this->parseFilter();
        $favoris = $config['favoris'] ?? [];

        // Calculate index from category ID
        $index = $favorisCategoryId - 100000;

        if ($index < 0 || $index >= count($favoris)) {
            return []; // Invalid favoris category ID
        }

        $favRule = $favoris[$index];

        if (!is_array($favRule)) {
            return [];
        }

        $match = $favRule['match'] ?? [];

        // Filter streams matching the favoris criteria
        $filtered = array_filter(
            $streams,
            fn($stream) => $this->matchStream($stream, $match)
        );

        return array_values($filtered); // Re-index array
    }

    /**
     * Check if filter is assigned
     *
     * @return bool
     */
    public function hasFilter(): bool
    {
        return $this->filter !== null;
    }

    /**
     * Get the assigned filter model
     *
     * @return object|null
     */
    public function getFilter(): ?object
    {
        return $this->filter;
    }
}
