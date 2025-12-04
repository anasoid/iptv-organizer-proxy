<?php

declare(strict_types=1);

namespace App\Models;

use RuntimeException;

/**
 * VodStream Model
 *
 * Represents video-on-demand content
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
