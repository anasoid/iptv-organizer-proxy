<?php

declare(strict_types=1);

namespace App\Services;

/**
 * Label Extraction Engine
 *
 * Extracts labels from channel/category names for filtering and organization
 */
class LabelExtractor
{
    /**
     * Extract labels from text
     *
     * @param string $text Channel or category name
     * @param string $streamType Type of stream (live, movie, series)
     * @return string Comma-separated labels
     */
    public static function extractLabels(string $text, string $streamType = 'live'): string
    {
        $labels = [];

        // Extract text between brackets as separate labels
        // Example: "ESPN [HD] [4K]" -> extracts "HD", "4K"
        if (preg_match_all('/\[([^\]]+)\]/', $text, $matches)) {
            foreach ($matches[1] as $match) {
                $label = trim($match);
                if (!empty($label)) {
                    $labels[] = $label;
                }
            }
            // Remove brackets and their contents from main text
            $text = preg_replace('/\[[^\]]+\]/', '', $text);
        }

        // Split by pipe delimiter
        // Example: "ESPN | Sports" -> "ESPN", "Sports"
        $parts = explode('|', $text);
        foreach ($parts as $part) {
            $subParts = explode('-', $part);
            foreach ($subParts as $subPart) {
                $label = trim($subPart);
                if (!empty($label)) {
                    $labels[] = $label;
                }
            }
        }

        // Add stream type as a label
        $labels[] = $streamType;

        // Remove duplicates and filter empty values
        $labels = array_unique(array_filter($labels, function ($label) {
            return !empty(trim($label));
        }));

        // Convert to comma-separated string
        return implode(',', $labels);
    }

    /**
     * Extract labels as array
     *
     * @param string $text Channel or category name
     * @param string $streamType Type of stream (live, movie, series)
     * @return array Array of labels
     */
    public static function extractLabelsArray(string $text, string $streamType = 'live'): array
    {
        $labelsString = self::extractLabels($text, $streamType);
        return explode(',', $labelsString);
    }

    /**
     * Check if text contains adult content indicators
     *
     * @param string $text Channel or category name
     * @return bool True if adult content detected
     */
    public static function isAdultContent(string $text): bool
    {
        $adultKeywords = [
            'xxx', 'adult', 'porn', 'sex', 'erotic', 'playboy',
            '+18', '18+', 'xxx', 'hot', 'blue'
        ];

        $textLower = strtolower($text);

        foreach ($adultKeywords as $keyword) {
            if (strpos($textLower, $keyword) !== false) {
                return true;
            }
        }

        return false;
    }
}
