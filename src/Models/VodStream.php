<?php

declare(strict_types=1);

namespace App\Models;

/**
 * VodStream Model
 *
 * Represents video-on-demand content
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
class VodStream extends BaseStreamModel
{
    protected string $table = 'vod_streams';

    /**
     * Extract labels from stream name
     *
     * @param string $text
     * @return string Comma-separated labels
     */
    public static function extractLabels(string $text): string
    {
        return LiveStream::extractLabels($text, 'movie');
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
            $extension = $data['container_extension'] ?? 'mp4';
            $streamUrl = rtrim($proxyUrl, '/') . "/movie/{$username}/{$password}/{$this->stream_id}.{$extension}";
        }

        // Build Xtream API response format for VOD
        $result = [
            'num' => (int) $this->stream_id,
            'name' => $this->name,
            'stream_type' => 'movie',
            'stream_id' => (int) $this->stream_id,
            'stream_icon' => $data['stream_icon'] ?? '',
            'rating' => $data['rating'] ?? '',
            'rating_5based' => (float) ($data['rating_5based'] ?? 0),
            'added' => $data['added'] ?? null,
            'category_id' => (string) $this->category_id,
            'container_extension' => $data['container_extension'] ?? 'mp4',
            'custom_sid' => $data['custom_sid'] ?? null,
            'direct_source' => $data['direct_source'] ?? '',
        ];

        // Add stream URL if generated
        if ($streamUrl) {
            $result['stream_url'] = $streamUrl;
        }

        return $result;
    }
}
