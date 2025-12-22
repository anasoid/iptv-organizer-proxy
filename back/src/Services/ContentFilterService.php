<?php

declare(strict_types=1);

namespace App\Services;

use App\Models\Client;
use App\Models\Category;
use App\Models\LiveStream;
use App\Models\VodStream;
use App\Models\Series;
use App\Models\Filter;

/**
 * Content Filtering Service
 *
 * Centralizes all filtering logic for use across the application
 * (backoffice, Xtream API, etc.)
 *
 * Handles filtering of:
 * - Categories (live, vod, series)
 * - Streams (live, vod)
 * - Series
 */
class ContentFilterService
{
    private ?Client $client;
    private ?FilterService $filterService;

    public function __construct(?Client $client = null)
    {
        $this->client = $client;
        $this->filterService = null;

        if ($client && $client->filter_id) {
            $filter = Filter::find($client->filter_id);
            if ($filter) {
                $this->filterService = new FilterService($filter, (bool) $client->hide_adult_content);
            }
        }
    }

    /**
     * Get allowed categories by type
     *
     * Applies filtering in this order:
     * 1. Items with allow_deny='allow' are always included (bypass filters)
     * 2. Items with allow_deny='deny' are always excluded
     * 3. Items with allow_deny=null are processed through filter rules
     */
    public function getAllowedCategories(string $type, ?int $limit = null, int $offset = 0): array
    {
        if (!$this->client) {
            return [];
        }

        $allCategories = Category::getBySourceAndType($this->client->source_id, $type, $limit, $offset);

        // Separate items by allow_deny field
        $separated = $this->separateByAllowDeny($allCategories);

        // Always excluded items are removed
        $allowed = $separated['allowed'];
        $toFilter = $separated['filtered'];

        // Apply filter rules to items without override
        if (!$this->filterService || empty($toFilter)) {
            $filtered = [];
        } else {
            $filtered = $this->filterService->filterCategories($toFilter, $type);
        }

        // Combine: explicitly allowed items + filter-passed items
        return array_merge($allowed, $filtered);
    }

    /**
     * Get blocked categories by type
     */
    public function getBlockedCategories(string $type): array
    {
        if (!$this->client) {
            return [];
        }

        $allCategories = Category::getBySourceAndType($this->client->source_id, $type);

        if (!$this->filterService) {
            return [];
        }

        $allowedCategories = $this->filterService->filterCategories($allCategories, $type);
        $allowedIds = array_map(fn($c) => $c->category_id, $allowedCategories);

        return array_filter($allCategories, fn($c) => !in_array($c->category_id, $allowedIds));
    }

    /**
     * Get allowed streams by type (live, vod)
     *
     * Applies filtering in this order:
     * 1. Items with allow_deny='allow' are always included (bypass filters)
     * 2. Items with allow_deny='deny' are always excluded
     * 3. Items with allow_deny=null are processed through filter rules
     */
    public function getAllowedStreams(string $type): array
    {
        if (!$this->client) {
            return [];
        }

        $allStreams = match ($type) {
            'live' => LiveStream::getBySource($this->client->source_id),
            'vod' => VodStream::getBySource($this->client->source_id),
            default => [],
        };

        // Separate items by allow_deny field
        $separated = $this->separateByAllowDeny($allStreams);

        // Always included items
        $allowed = $separated['allowed'];
        $toFilter = $separated['filtered'];

        // Apply filter rules to items without override
        if (!$this->filterService || empty($toFilter)) {
            $filtered = [];
        } else {
            $filterResult = $this->filterService->applyToStreams($toFilter);

            // Convert to IDs for comparison
            $filteredIds = array_map(fn($s) => is_array($s) ? $s['id'] : $s->id, $filterResult);

            // Keep only the filtered items
            $filtered = array_filter($toFilter, fn($s) => in_array($s->id, $filteredIds));
        }

        // Combine: explicitly allowed items + filter-passed items
        return array_merge($allowed, $filtered);
    }

    /**
     * Get blocked streams by type (live, vod)
     */
    public function getBlockedStreams(string $type): array
    {
        if (!$this->client) {
            return [];
        }

        $allStreams = match ($type) {
            'live' => LiveStream::getBySource($this->client->source_id),
            'vod' => VodStream::getBySource($this->client->source_id),
            default => [],
        };

        if (!$this->filterService) {
            return [];
        }

        $filtered = $this->filterService->applyToStreams($allStreams);

        // Convert to IDs for comparison
        $filteredIds = array_map(fn($s) => is_array($s) ? $s['id'] : $s->id, $filtered);

        // Return model objects that are NOT in the filtered list
        return array_filter($allStreams, fn($s) => !in_array($s->id, $filteredIds));
    }

    /**
     * Get allowed series
     *
     * Applies filtering in this order:
     * 1. Items with allow_deny='allow' are always included (bypass filters)
     * 2. Items with allow_deny='deny' are always excluded
     * 3. Items with allow_deny=null are processed through filter rules
     */
    public function getAllowedSeries(): array
    {
        if (!$this->client) {
            return [];
        }

        $allSeries = Series::getBySource($this->client->source_id);

        // Separate items by allow_deny field
        $separated = $this->separateByAllowDeny($allSeries);

        // Always included items
        $allowed = $separated['allowed'];
        $toFilter = $separated['filtered'];

        // Apply filter rules to items without override
        if (!$this->filterService || empty($toFilter)) {
            $filtered = [];
        } else {
            $filterResult = $this->filterService->applyToStreams($toFilter);

            // Convert to IDs for comparison
            $filteredIds = array_map(fn($s) => is_array($s) ? $s['id'] : $s->id, $filterResult);

            // Keep only the filtered items
            $filtered = array_filter($toFilter, fn($s) => in_array($s->id, $filteredIds));
        }

        // Combine: explicitly allowed items + filter-passed items
        return array_merge($allowed, $filtered);
    }

    /**
     * Get blocked series
     */
    public function getBlockedSeries(): array
    {
        if (!$this->client) {
            return [];
        }

        $allSeries = Series::getBySource($this->client->source_id);

        if (!$this->filterService) {
            return [];
        }

        $filtered = $this->filterService->applyToStreams($allSeries);

        // Convert to IDs for comparison
        $filteredIds = array_map(fn($s) => is_array($s) ? $s['id'] : $s->id, $filtered);

        // Return model objects that are NOT in the filtered list
        return array_filter($allSeries, fn($s) => !in_array($s->id, $filteredIds));
    }

    /**
     * Check if client has an active filter
     */
    public function hasFilter(): bool
    {
        return $this->filterService !== null;
    }

    /**
     * Get the filter if assigned
     */
    public function getFilter(): ?Filter
    {
        if (!$this->client || !$this->client->filter_id) {
            return null;
        }
        return Filter::find($this->client->filter_id);
    }

    /**
     * Separate items by allow_deny field for proper filtering
     *
     * Returns array with three keys:
     * - allowed: Items with allow_deny='allow' (bypass all filters)
     * - filtered: Items with allow_deny=null (go through filter rules)
     * - denied: Items with allow_deny='deny' (always excluded)
     *
     * @param array $items Items to filter (categories or streams)
     * @return array Separated items with keys: allowed, filtered, denied
     */
    private function separateByAllowDeny(array $items): array
    {
        $result = [
            'allowed' => [],
            'filtered' => [],
            'denied' => [],
        ];

        if (empty($items)) {
            return $result;
        }

        foreach ($items as $item) {
            // Get allow_deny field value
            $allowDeny = null;
            if (is_array($item)) {
                $allowDeny = $item['allow_deny'] ?? null;
            } elseif (is_object($item)) {
                $allowDeny = $item->getAttribute('allow_deny') ?? $item->allow_deny ?? null;
            }

            // Categorize by allow_deny value
            if ($allowDeny === 'allow') {
                $result['allowed'][] = $item;
            } elseif ($allowDeny === 'deny') {
                $result['denied'][] = $item;
            } else {
                $result['filtered'][] = $item;
            }
        }

        return $result;
    }

    /**
     * Format category for export
     */
    public static function formatCategory($category): array
    {
        return [
            'category_id' => (string) $category->category_id,
            'category_name' => $category->category_name,
            'parent_id' => (int) ($category->parent_id ?? 0),
        ];
    }

    /**
     * Format stream for export
     */
    public static function formatStream($stream): array
    {
        return [
            'id' => $stream->id,
            'name' => $stream->name,
            'num' => $stream->num ?? null,
            'category_id' => $stream->category_id,
        ];
    }

    /**
     * Format series for export
     */
    public static function formatSeries($series): array
    {
        return [
            'id' => $series->id,
            'name' => $series->name,
            'num' => $series->num ?? null,
            'category_id' => $series->category_id,
        ];
    }
}
