<?php

declare(strict_types=1);

namespace App\Models;

use RuntimeException;

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

}
