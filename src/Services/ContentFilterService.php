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
     */
    public function getAllowedCategories(string $type): array
    {
        if (!$this->client) {
            return [];
        }

        $allCategories = Category::getBySourceAndType($this->client->source_id, $type);

        if (!$this->filterService) {
            return $allCategories;
        }

        return $this->filterService->filterCategories($allCategories, $type);
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
     */
    public function getAllowedStreams(string $type): array
    {
        if (!$this->client) {
            return [];
        }

        $allStreams = match ($type) {
            'live' => LiveStream::getBySource($this->client->source_id, false),
            'vod' => VodStream::getBySource($this->client->source_id, false),
            default => [],
        };

        if (!$this->filterService) {
            return $allStreams;
        }

        $filtered = $this->filterService->applyToStreams($allStreams);

        // Convert to IDs for comparison
        $filteredIds = array_map(fn($s) => is_array($s) ? $s['id'] : $s->id, $filtered);

        // Return model objects that are in the filtered list
        return array_filter($allStreams, fn($s) => in_array($s->id, $filteredIds));
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
            'live' => LiveStream::getBySource($this->client->source_id, false),
            'vod' => VodStream::getBySource($this->client->source_id, false),
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
     */
    public function getAllowedSeries(): array
    {
        if (!$this->client) {
            return [];
        }

        $allSeries = Series::getBySource($this->client->source_id, false);

        if (!$this->filterService) {
            return $allSeries;
        }

        $filtered = $this->filterService->applyToStreams($allSeries);

        // Convert to IDs for comparison
        $filteredIds = array_map(fn($s) => is_array($s) ? $s['id'] : $s->id, $filtered);

        // Return model objects that are in the filtered list
        return array_filter($allSeries, fn($s) => in_array($s->id, $filteredIds));
    }

    /**
     * Get blocked series
     */
    public function getBlockedSeries(): array
    {
        if (!$this->client) {
            return [];
        }

        $allSeries = Series::getBySource($this->client->source_id, false);

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
     * Get favoris (virtual categories) generated from filter configuration
     */
    public function getFavorisCategories(): array
    {
        if (!$this->filterService) {
            return [];
        }
        return $this->filterService->generateFavorisCategories();
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
