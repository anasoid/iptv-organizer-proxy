#!/usr/bin/env php
<?php

declare(strict_types=1);

/**
 * Sync All Sources Script
 *
 * Synchronize all active sources automatically
 * Useful for cron jobs or scheduled tasks
 *
 * Usage:
 *   php bin/sync-all-sources.php
 *   php bin/sync-all-sources.php --verbose
 *   php bin/sync-all-sources.php --force
 */

require_once __DIR__ . '/../bootstrap.php';

use App\Models\Source;

// Parse command-line arguments
$options = getopt('', [
    'force',
    'verbose',
    'help',
]);

// Show help
if (isset($options['help'])) {
    echo "Sync All Sources Script\n";
    echo "=======================\n\n";
    echo "Synchronize all active sources automatically.\n\n";
    echo "Usage:\n";
    echo "  php bin/sync-all-sources.php [OPTIONS]\n\n";
    echo "Options:\n";
    echo "  --force                 Bypass sync lock and interval\n";
    echo "  --verbose               Show detailed output\n";
    echo "  --help                  Show this help message\n\n";
    echo "Examples:\n";
    echo "  php bin/sync-all-sources.php\n";
    echo "  php bin/sync-all-sources.php --force --verbose\n\n";
    exit(0);
}

$force = isset($options['force']);
$verbose = isset($options['verbose']);

// Colors for output
function colorize(string $text, string $color): string
{
    $colors = [
        'green' => "\033[32m",
        'red' => "\033[31m",
        'yellow' => "\033[33m",
        'blue' => "\033[34m",
        'reset' => "\033[0m",
    ];

    return ($colors[$color] ?? '') . $text . $colors['reset'];
}

function logInfo(string $message): void
{
    echo "[" . date('Y-m-d H:i:s') . "] " . $message . "\n";
}

function logSuccess(string $message): void
{
    echo colorize("[SUCCESS] ", 'green') . $message . "\n";
}

function logError(string $message): void
{
    echo colorize("[ERROR] ", 'red') . $message . "\n";
}

function logWarning(string $message): void
{
    echo colorize("[WARNING] ", 'yellow') . $message . "\n";
}

// Main execution
try {
    logInfo("Starting sync for all active sources");

    // Get all active sources
    $sources = Source::getActive();

    if (empty($sources)) {
        logWarning("No active sources found");
        exit(0);
    }

    logInfo("Found " . count($sources) . " active source(s)");

    $successCount = 0;
    $errorCount = 0;
    $skippedCount = 0;

    foreach ($sources as $source) {
        echo "\n" . str_repeat('=', 80) . "\n";
        logInfo("Processing source: " . colorize($source->name, 'blue') . " (ID: {$source->id})");
        echo str_repeat('=', 80) . "\n";

        // Check if sync is due
        if (!$force && !$source->isSyncDue()) {
            logWarning("Sync not due yet. Next sync: {$source->getNextSyncTime()}");
            $skippedCount++;
            continue;
        }

        // Build command
        $command = 'php ' . __DIR__ . '/sync.php --source-id=' . $source->id;

        if ($force) {
            $command .= ' --force';
        }

        if ($verbose) {
            $command .= ' --verbose';
        }

        // Execute sync command
        $output = [];
        $returnCode = 0;

        exec($command . ' 2>&1', $output, $returnCode);

        // Display output if verbose
        if ($verbose) {
            foreach ($output as $line) {
                echo "  " . $line . "\n";
            }
        }

        // Check result
        if ($returnCode === 0) {
            logSuccess("Source synced successfully: {$source->name}");
            $successCount++;
        } else {
            logError("Source sync failed: {$source->name}");
            if (!$verbose) {
                echo "  Last lines of output:\n";
                $lastLines = array_slice($output, -5);
                foreach ($lastLines as $line) {
                    echo "  " . $line . "\n";
                }
            }
            $errorCount++;
        }
    }

    // Summary
    echo "\n" . str_repeat('=', 80) . "\n";
    logInfo(colorize("SYNC ALL SOURCES SUMMARY", 'blue'));
    echo str_repeat('=', 80) . "\n";
    echo "Total Sources: " . count($sources) . "\n";
    echo "Successful: " . colorize((string)$successCount, 'green') . "\n";
    echo "Failed: " . colorize((string)$errorCount, 'red') . "\n";
    echo "Skipped: " . colorize((string)$skippedCount, 'yellow') . "\n";

    if ($errorCount > 0) {
        logWarning("Some sources failed to sync");
        exit(1);
    }

    logSuccess("All sources synced successfully");
    exit(0);

} catch (Exception $e) {
    logError("Fatal error: " . $e->getMessage());
    exit(1);
}
