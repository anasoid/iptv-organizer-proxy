<?php

declare(strict_types=1);

namespace App\Models;

use RuntimeException;

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

}
