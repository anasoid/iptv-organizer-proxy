<?php

declare(strict_types=1);

namespace App\Models;

/**
 * Series Model
 *
 * Represents TV series content
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
class Series extends BaseStreamModel
{
    protected string $table = 'series';

    /**
     * Extract labels from series name
     *
     * @param string $text
     * @return string Comma-separated labels
     */
    public static function extractLabels(string $text): string
    {
        return LiveStream::extractLabels($text, 'series');
    }

    /**
     * Convert series to Xtream API format
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

        // Build Xtream API response format for series
        $result = [
            'num' => (int) $this->stream_id,
            'name' => $this->name,
            'series_id' => (int) $this->stream_id,
            'cover' => $data['cover'] ?? '',
            'plot' => $data['plot'] ?? '',
            'cast' => $data['cast'] ?? '',
            'director' => $data['director'] ?? '',
            'genre' => $data['genre'] ?? '',
            'release_date' => $data['release_date'] ?? '',
            'last_modified' => $data['last_modified'] ?? null,
            'rating' => $data['rating'] ?? '',
            'rating_5based' => (float) ($data['rating_5based'] ?? 0),
            'backdrop_path' => $data['backdrop_path'] ?? [],
            'youtube_trailer' => $data['youtube_trailer'] ?? '',
            'episode_run_time' => $data['episode_run_time'] ?? '',
            'category_id' => (string) $this->category_id,
        ];

        return $result;
    }
}
