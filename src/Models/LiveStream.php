<?php

declare(strict_types=1);

namespace App\Models;

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
            $extension = $data['container_extension'] ?? 'm3u8';
            $streamUrl = rtrim($proxyUrl, '/') . "/live/{$username}/{$password}/{$this->stream_id}.{$extension}";
        }

        // Build Xtream API response format
        $result = [
            'num' => (int) $this->stream_id,
            'name' => $this->name,
            'stream_type' => $data['stream_type'] ?? 'live',
            'stream_id' => (int) $this->stream_id,
            'stream_icon' => $data['stream_icon'] ?? '',
            'epg_channel_id' => $data['epg_channel_id'] ?? null,
            'added' => $data['added'] ?? null,
            'category_id' => (string) $this->category_id,
            'custom_sid' => $data['custom_sid'] ?? null,
            'tv_archive' => (int) ($data['tv_archive'] ?? 0),
            'direct_source' => $data['direct_source'] ?? '',
            'tv_archive_duration' => (int) ($data['tv_archive_duration'] ?? 0),
        ];

        // Add stream URL if generated
        if ($streamUrl) {
            $result['stream_url'] = $streamUrl;
        }

        return $result;
    }
}
