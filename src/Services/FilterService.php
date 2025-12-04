<?php

declare(strict_types=1);

namespace App\Services;

use App\Models\Filter;
use App\Models\Category;
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
    private object|null $filter;
    private bool $hideAdultContent;
    private ?array $parsedFilter = null;

    /**
     * Constructor
     *
     * @param object|null $filter The filter model (null = no filter applied)
     * @param bool $hideAdultContent Whether to hide adult content
     */
    public function __construct(object|null $filter = null, bool $hideAdultContent = false)
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
            // Parse rules from filter_config
            $config = Yaml::parse($this->filter->filter_config);

            if (!is_array($config)) {
                throw new RuntimeException('Filter configuration must be a YAML object');
            }

            $rules = $config['rules'] ?? [];

            // Parse favoris: try from filter_config first, then from separate favoris field
            $favoris = [];
            
            // Check if favoris is in filter_config
            if (!empty($config['favoris'])) {
                $favoris = $config['favoris'];
            } elseif (!empty($this->filter->favoris)) {
                // Otherwise try to parse from separate favoris field
                try {
                    $favorisConfig = Yaml::parse($this->filter->favoris);
                    if (is_array($favorisConfig)) {
                        // Handle both direct array and wrapped in 'favoris' key
                        $favoris = $favorisConfig['favoris'] ?? $favorisConfig;
                    }
                } catch (\Exception $e) {
                    // If favoris parsing fails, log but don't crash
                    throw new RuntimeException("Failed to parse favoris YAML: " . $e->getMessage());
                }
            }

            $this->parsedFilter = [
                'rules' => $rules,
                'favoris' => $favoris,
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
     * Matching Rules:
     * - by_name: Wildcard patterns (case-insensitive), matches if ANY pattern matches (OR logic)
     * - by_labels: Exact match (case-insensitive), matches if stream has ALL labels (AND logic)
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

        $hasChannelCriteria = !empty($match['channels']['by_name']) || !empty($match['channels']['by_labels']);
        $hasCategoryCriteria = !empty($match['categories']['by_name']) || !empty($match['categories']['by_labels']);

        // Evaluate channel criteria
        $channelMatches = false;
        if ($hasChannelCriteria) {
            $channelMatches = $this->matchesChannelCriteria(
                $stream['name'] ?? '',
                $stream['labels'] ?? '',
                $match['channels']
            );
        }

        // Evaluate category criteria
        $categoryMatches = false;
        if ($hasCategoryCriteria) {
            $categoryMatches = $this->matchesCategoryCriteria(
                $stream['category_name'] ?? '',
                $stream['category_labels'] ?? '',
                $match['categories']
            );
        }

        // If both channel and category criteria exist, both must match (AND)
        // If only one type exists, that one must match
        if ($hasChannelCriteria && $hasCategoryCriteria) {
            return $channelMatches && $categoryMatches;
        } elseif ($hasChannelCriteria) {
            return $channelMatches;
        } elseif ($hasCategoryCriteria) {
            return $categoryMatches;
        }

        return false;
    }

    /**
     * Check if stream matches channel criteria
     *
     * Matching Rules:
     * - by_name: Wildcard patterns (case-insensitive), ANY pattern matches (OR logic)
     * - by_labels: Exact match (case-insensitive), ALL labels must exist (AND logic)
     *
     * @param string $channelName Channel/stream name
     * @param string $channelLabelsStr Comma-separated labels
     * @param array $criteria Criteria with by_name and by_labels
     * @return bool True if matches
     */
    private function matchesChannelCriteria(string $channelName, string $channelLabelsStr, array $criteria): bool
    {
        $channelName = strtolower($channelName);
        $channelLabels = $this->parseLabels($channelLabelsStr);

        // Check by_name: ANY pattern matches (OR logic)
        $nameMatches = false;
        if (!empty($criteria['by_name'])) {
            foreach ($criteria['by_name'] as $pattern) {
                if ($this->matchesPattern($channelName, $pattern)) {
                    $nameMatches = true;
                    break;
                }
            }
        } else {
            $nameMatches = true; // No name criteria = matches
        }

        // Check by_labels: ANY label must exist (OR logic)
        $labelsMatch = false;
        if (!empty($criteria['by_labels'])) {
            foreach ($criteria['by_labels'] as $label) {
                if (in_array(strtolower($label), $channelLabels, true)) {
                    $labelsMatch = true;
                    break;
                }
            }
        } else {
            $labelsMatch = true; // No label criteria = matches
        }

        // Both criteria must match (AND)
        return $nameMatches && $labelsMatch;
    }

    /**
     * Check if category matches criteria
     *
     * Matching Rules:
     * - by_name: Wildcard patterns (case-insensitive), ANY pattern matches (OR logic)
     * - by_labels: Exact match (case-insensitive), ALL labels must exist (AND logic)
     *
     * @param string $categoryName Category name
     * @param string $categoryLabelsStr Comma-separated labels
     * @param array $criteria Criteria with by_name and by_labels
     * @return bool True if matches
     */
    private function matchesCategoryCriteria(string $categoryName, string $categoryLabelsStr, array $criteria): bool
    {
        $categoryName = strtolower($categoryName);
        $categoryLabels = $this->parseLabels($categoryLabelsStr);

        // Check by_name: ANY pattern matches (OR logic)
        $nameMatches = false;
        if (!empty($criteria['by_name'])) {
            foreach ($criteria['by_name'] as $pattern) {
                if ($this->matchesPattern($categoryName, $pattern)) {
                    $nameMatches = true;
                    break;
                }
            }
        } else {
            $nameMatches = true; // No name criteria = matches
        }

        // Check by_labels: ANY label must exist (OR logic)
        $labelsMatch = false;
        if (!empty($criteria['by_labels'])) {
            foreach ($criteria['by_labels'] as $label) {
                if (in_array(strtolower($label), $categoryLabels, true)) {
                    $labelsMatch = true;
                    break;
                }
            }
        } else {
            $labelsMatch = true; // No label criteria = matches
        }

        // Both criteria must match (AND)
        return $nameMatches && $labelsMatch;
    }

    /**
     * Match a string against a wildcard pattern (case-insensitive)
     *
     * Supports wildcards:
     * - * matches any number of characters
     * - ? matches exactly one character
     *
     * @param string $text Text to match
     * @param string $pattern Wildcard pattern
     * @return bool True if matches
     */
    private function matchesPattern(string $text, string $pattern): bool
    {
        // If pattern contains wildcards, use fnmatch
        if (strpos($pattern, '*') !== false || strpos($pattern, '?') !== false) {
            return fnmatch(strtolower($pattern), strtolower($text), FNM_CASEFOLD);
        }
        
        // Otherwise, use substring matching (case-insensitive)
        return stripos($text, $pattern) !== false;
    }

    /**
     * Apply filters to streams with priority order:
     * 1. Adult content filtering (if enabled) - FIRST PRIORITY
     * 2. Include/exclude rules (if filter assigned)
     * 3. Favoris category filtering (if categoryId >= 100000)
     *
     * @param array $streams Array of streams (can be plain arrays or model objects)
     * @param int|null $categoryId Optional category ID for favoris filtering
     * @return array Filtered streams
     */
    public function applyToStreams(array $streams, ?int $categoryId = null): array
    {
        // Batch load all categories for efficient lookup
        $categoryCache = [];
        if (!empty($streams)) {
            // Get first stream to determine source_id and stream type
            $firstStream = current($streams);
            $sourceId = is_array($firstStream) ? ($firstStream['source_id'] ?? null) : ($firstStream->source_id ?? null);
            // Detect stream type from the model class name
            $streamType = null;
            if (!is_array($firstStream)) {
                $className = (new \ReflectionClass($firstStream))->getShortName();
                if ($className === 'VodStream') {
                    $streamType = 'vod';
                } elseif ($className === 'Series') {
                    $streamType = 'series';
                } elseif ($className === 'LiveStream') {
                    $streamType = 'live';
                }
            }

            // Only load categories from database if we can determine stream type (model objects)
            // Plain arrays already have category information included
            if ($streamType !== null && $sourceId) {
                // Load categories for this source AND type to avoid conflicts between types
                $allCategories = Category::getBySourceAndType($sourceId, $streamType);
                foreach ($allCategories as $cat) {
                    // Index by category_id (not database id) for fast lookup
                    $key = $sourceId . '_' . $cat->category_id;
                    $categoryCache[$key] = $cat;
                }
            }
        }
        
        // Convert model objects to arrays for filtering
        $streamArrays = array_map(function($stream) use ($categoryCache) {
            if (is_array($stream)) {
                return $stream;
            }

            // Get category information if stream has a category (using cache)
            $categoryName = '';
            $categoryLabels = '';
            if ($stream->category_id) {
                $key = $stream->source_id . '_' . $stream->category_id;
                if (isset($categoryCache[$key])) {
                    $category = $categoryCache[$key];
                    $categoryName = $category->category_name ?? '';
                    $categoryLabels = $category->labels ?? '';
                    // Ensure labels are extracted if null
                    if (empty($categoryLabels)) {
                        $categoryLabels = Category::extractLabels($categoryName);
                    }
                }
            }

            // Convert model object to array
            return [
                'id' => $stream->id ?? null,
                'stream_id' => $stream->stream_id ?? null,
                'name' => $stream->name ?? '',
                'labels' => $stream->labels ?? '',
                'category_id' => $stream->category_id ?? null,
                'category_ids' => $stream->category_ids ?? null,
                'category_name' => $categoryName,
                'category_labels' => $categoryLabels,
                'is_adult' => (int) ($stream->is_adult ?? 0),
                'num' => $stream->num ?? null,
            ];
        }, $streams);

        // Handle favoris categories (ID >= 100000)
        if ($categoryId !== null && $categoryId >= 100000) {
            return $this->filterByFavorisCategory($streamArrays, $categoryId);
        }

        // Apply adult content filter first (highest priority)
        $filtered = $this->filterAdultContent($streamArrays);

        // Apply include/exclude rules if filter is assigned
        if ($this->filter !== null) {
            $filtered = $this->applyFilterRules($filtered);
        }

        return $filtered;
    }

    /**
     * Apply include/exclude rules to streams in order
     *
     * Rules processing order:
     * 1. Filter adult content first (if enabled)
     * 2. Process rules IN ORDER:
     *    - If type is "include": if stream matches → ACCEPT
     *    - If type is "exclude": if stream matches → REJECT
     *    - If stream matches a rule, stop processing
     * 3. If stream doesn't match ANY rule:
     *    - If there are include rules → REJECT (ignored)
     *    - If only exclude rules → ACCEPT
     *
     * Rules structure:
     * [
     *   {
     *     name: "rule name",
     *     type: "include" or "exclude",
     *     match: { categories: {...}, channels: {...} }
     *   }
     * ]
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

        // Check if there are any include rules
        $hasIncludeRules = false;
        foreach ($rules as $rule) {
            if (is_array($rule) && ($rule['type'] ?? 'include') === 'include') {
                $hasIncludeRules = true;
                break;
            }
        }

        // Filter streams respecting rule priority: exclude > include
        return array_filter($streams, function (array $stream) use ($rules, $hasIncludeRules) {
            $matchedInclude = false;
            $matchedExclude = false;
            
            // Check all rules to find any matches
            foreach ($rules as $rule) {
                if (!is_array($rule)) {
                    continue;
                }

                $type = $rule['type'] ?? 'include';
                $match = $rule['match'] ?? [];

                // Check if stream matches this rule
                if ($this->matchStream($stream, $match)) {
                    if ($type === 'include') {
                        $matchedInclude = true;
                    } elseif ($type === 'exclude') {
                        $matchedExclude = true;
                    }
                }
            }

            // Exclude has priority over include
            if ($matchedExclude) {
                return false;
            }

            // If there are include rules, stream must match at least one
            // If there are only exclude rules, stream is shown by default
            if ($hasIncludeRules) {
                return $matchedInclude;
            }

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
     * Filter categories using rules that have category match criteria
     *
     * Only applies rules with match.categories (ignores channel-only rules)
     * Uses same include/exclude logic as stream filtering
     *
     * @param array $categories Array of categories
     * @return array Filtered categories
     */
    public function filterCategories(array $categories, ?string $type = null): array
    {
        if ($this->filter === null) {
            return $categories;
        }

        $config = $this->parseFilter();
        $rules = $config['rules'] ?? [];

        if (empty($rules)) {
            return $categories;
        }

        // Filter rules to only those matching the category stream type
        $typeRules = [];
        foreach ($rules as $rule) {
            if (!is_array($rule)) {
                continue;
            }
            
            // Skip if rule has stream type requirement that doesn't match
            if ($type !== null && !empty($rule['match']['stream_type'])) {
                $ruleTypes = (array) $rule['match']['stream_type'];
                if (!in_array($type, $ruleTypes)) {
                    continue;
                }
            }
            
            // Only include rules that have category criteria
            if (!empty($rule['match']['categories'])) {
                $typeRules[] = $rule;
            }
        }

        // If no matching rules, return all categories
        if (empty($typeRules)) {
            return $categories;
        }

        // Check if there are any include rules
        $hasIncludeRules = false;
        foreach ($typeRules as $rule) {
            if (($rule['type'] ?? 'include') === 'include') {
                $hasIncludeRules = true;
                break;
            }
        }

        // Filter categories respecting rule order
        return array_filter($categories, function ($category) use ($typeRules, $hasIncludeRules) {
            // Ensure labels are extracted if null
            $categoryLabels = $category->labels;
            if (empty($categoryLabels)) {
                $categoryLabels = Category::extractLabels($category->category_name ?? '');
            }
            
            // Process rules in order
            foreach ($typeRules as $rule) {
                $ruleType = $rule['type'] ?? 'include';
                $match = $rule['match'] ?: [];

                // Check if category matches this rule
                if ($this->matchesCategoryCriteria(
                    $category->category_name ?? '',
                    $categoryLabels,
                    $match['categories'] ?? []
                )) {
                    // If matches and type is include → ACCEPT
                    if ($ruleType === 'include') {
                        return true;
                    }
                    // If matches and type is exclude → REJECT
                    if ($ruleType === 'exclude') {
                        return false;
                    }
                }
            }

            // Category didn't match any rule → KEEP
            // If there are include rules, reject as "ignored"
            // If only exclude rules, accept (show)
            // If no category rules, accept (show all)
            return !$hasIncludeRules;
        });
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

    /**
     * Apply filters to streams and track rejection reasons
     *
     * Returns array with:
     * - accepted: [] (filtered streams)
     * - rejected: {
     *     by_adult_content: [stream_ids],
     *     by_rule: {
     *       rule_name: [stream_ids],
     *       ...
     *     },
     *     ignored: [stream_ids]  (didn't match any rule when include rules exist)
     *   }
     *
     * @param array $streams Array of streams
     * @param int|null $categoryId Optional category ID
     * @return array Result with accepted and rejected streams with reasons
     */
    public function applyWithRejectionTracking(array $streams, ?int $categoryId = null): array
    {
        // Handle favoris categories
        if ($categoryId !== null && $categoryId >= 100000) {
            return [
                'accepted' => $this->filterByFavorisCategory($streams, $categoryId),
                'rejected' => [
                    'by_adult_content' => [],
                    'by_rule' => [],
                    'ignored' => [],
                ],
            ];
        }

        $result = [
            'accepted' => [],
            'rejected' => [
                'by_adult_content' => [],
                'by_rule' => [],
                'ignored' => [],
            ],
        ];

        $config = $this->parseFilter();
        $rules = $config['rules'] ?? [];

        // Check if there are any include rules
        $hasIncludeRules = false;
        foreach ($rules as $rule) {
            if (is_array($rule) && ($rule['type'] ?? 'include') === 'include') {
                $hasIncludeRules = true;
                break;
            }
        }

        // Process each stream
        foreach ($streams as $stream) {
            $streamId = $stream['id'] ?? null;

            // Check adult content first
            if ($this->hideAdultContent && ($stream['is_adult'] ?? false)) {
                if ($streamId) {
                    $result['rejected']['by_adult_content'][] = $streamId;
                }
                continue;
            }

            // If no filter assigned, accept stream
            if ($this->filter === null) {
                $result['accepted'][] = $stream;
                continue;
            }

            // Process rules in order
            $matched = false;
            foreach ($rules as $rule) {
                if (!is_array($rule)) {
                    continue;
                }

                $type = $rule['type'] ?? 'include';
                $ruleName = $rule['name'] ?? 'Unknown Rule';
                $match = $rule['match'] ?? [];

                // Check if stream matches this rule
                if ($this->matchStream($stream, $match)) {
                    $matched = true;

                    if ($type === 'include') {
                        // Accept stream
                        $result['accepted'][] = $stream;
                    } else {
                        // Reject by this rule
                        if ($streamId) {
                            if (!isset($result['rejected']['by_rule'][$ruleName])) {
                                $result['rejected']['by_rule'][$ruleName] = [];
                            }
                            $result['rejected']['by_rule'][$ruleName][] = $streamId;
                        }
                    }
                    break; // Stop processing rules
                }
            }

            // If no rule matched
            if (!$matched) {
                if ($hasIncludeRules) {
                    // Rejected as ignored (didn't match any include rule)
                    if ($streamId) {
                        $result['rejected']['ignored'][] = $streamId;
                    }
                } else {
                    // Accept (only exclude rules and didn't match any)
                    $result['accepted'][] = $stream;
                }
            }
        }

        return $result;
    }
}
