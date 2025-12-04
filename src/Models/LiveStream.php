<?php

declare(strict_types=1);

namespace App\Models;

use RuntimeException;

/**
 * LiveStream Model
 *
 * Represents live TV streams
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
class LiveStream extends BaseStreamModel
{
    protected string $table = 'live_streams';

    /**
     * Extract labels from stream name with optional stream type
     *
     * @param string $text
     * @param string $streamType Stream type (live, movie, series) - defaults to 'live'
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

}
