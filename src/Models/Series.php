<?php

declare(strict_types=1);

namespace App\Models;

use RuntimeException;

/**
 * Series Model
 *
 * Represents TV series content
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
