#!/usr/bin/env php
<?php

declare(strict_types=1);

/**
 * Sync CLI Script
 *
 * Manually trigger synchronization tasks for a specific source
 *
 * Usage:
 *   php bin/sync.php --source-id=1
 *   php bin/sync.php --source-id=1 --task-type=live_streams
 *   php bin/sync.php --source-id=1 --task-type=all --force --verbose
 */

require_once __DIR__ . '/../bootstrap.php';

use App\Models\Source;
use App\Services\Xtream\XtreamClient;
use App\Services\SyncService;

// Parse command-line arguments
$options = getopt('', [
    'source-id:',
    'task-type::',
    'force',
    'verbose',
    'help',
]);

// Show help
if (isset($options['help']) || empty($options['source-id'])) {
    echo "Sync CLI Script\n";
    echo "===============\n\n";
    echo "Usage:\n";
    echo "  php bin/sync.php --source-id=ID [OPTIONS]\n\n";
    echo "Required:\n";
    echo "  --source-id=ID          Source ID to sync\n\n";
    echo "Options:\n";
    echo "  --task-type=TYPE        Specific task to run:\n";
    echo "                          live_categories, live_streams,\n";
    echo "                          vod_categories, vod_streams,\n";
    echo "                          series_categories, series, all\n";
    echo "                          (default: all)\n";
    echo "  --force                 Bypass sync lock and interval\n";
    echo "  --verbose               Show detailed output\n";
    echo "  --help                  Show this help message\n\n";
    echo "Examples:\n";
    echo "  php bin/sync.php --source-id=1\n";
    echo "  php bin/sync.php --source-id=1 --task-type=live_streams\n";
    echo "  php bin/sync.php --source-id=1 --force --verbose\n\n";
    exit(0);
}

$sourceId = (int) $options['source-id'];
$taskType = $options['task-type'] ?? 'all';
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

function logInfo(string $message, bool $verbose = false): void
{
    global $verbose as $isVerbose;
    if ($verbose && !$isVerbose) {
        return;
    }
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
    logInfo("Starting sync for source ID: {$sourceId}");

    // Load source
    $source = Source::find($sourceId);

    if ($source === null) {
        logError("Source with ID {$sourceId} not found");
        exit(1);
    }

    if (!$source->is_active) {
        logError("Source '{$source->name}' is not active");
        exit(1);
    }

    logInfo("Source: {$source->name} ({$source->url})");

    // Check sync interval (unless forced)
    if (!$force && !$source->isSyncDue()) {
        logWarning("Sync not due yet. Next sync: {$source->getNextSyncTime()}");
        logInfo("Use --force to bypass sync interval");
        exit(0);
    }

    // Create XtreamClient
    logInfo("Connecting to Xtream API...", true);
    $client = new XtreamClient($source);

    // Authenticate
    try {
        $authInfo = $client->authenticate();
        logSuccess("Connected to Xtream API");
        logInfo("Server: {$authInfo['server_info']['server_protocol']}", true);
    } catch (Exception $e) {
        logError("Authentication failed: " . $e->getMessage());
        exit(1);
    }

    // Create SyncService
    $syncService = new SyncService($source, $client);

    // Define available tasks
    $tasks = [
        'live_categories' => 'syncLiveCategories',
        'live_streams' => 'syncLiveStreams',
        'vod_categories' => 'syncVodCategories',
        'vod_streams' => 'syncVodStreams',
        'series_categories' => 'syncSeriesCategories',
        'series' => 'syncSeries',
    ];

    // Determine which tasks to run
    $tasksToRun = [];
    if ($taskType === 'all' || empty($taskType)) {
        $tasksToRun = $tasks;
    } elseif (isset($tasks[$taskType])) {
        $tasksToRun[$taskType] = $tasks[$taskType];
    } else {
        logError("Invalid task type: {$taskType}");
        logInfo("Valid types: " . implode(', ', array_keys($tasks)) . ", all");
        exit(1);
    }

    // Run tasks
    $totalStats = ['added' => 0, 'updated' => 0, 'deleted' => 0, 'errors' => 0];

    foreach ($tasksToRun as $taskName => $method) {
        echo "\n" . str_repeat('=', 60) . "\n";
        logInfo("Running task: " . colorize($taskName, 'blue'));
        echo str_repeat('=', 60) . "\n";

        try {
            $stats = $syncService->$method();

            if (isset($stats['error'])) {
                logError($stats['error']);
                $totalStats['errors']++;
            } else {
                logSuccess("Task completed: {$taskName}");
                echo "  Added: " . colorize((string)$stats['added'], 'green') . "\n";
                echo "  Updated: " . colorize((string)$stats['updated'], 'yellow') . "\n";
                echo "  Deleted: " . colorize((string)$stats['deleted'], 'red') . "\n";

                $totalStats['added'] += $stats['added'];
                $totalStats['updated'] += $stats['updated'];
                $totalStats['deleted'] += $stats['deleted'];
            }
        } catch (Exception $e) {
            logError("Task failed: " . $e->getMessage());
            logInfo("Stack trace: " . $e->getTraceAsString(), true);
            $totalStats['errors']++;
        }
    }

    // Update source sync time
    $source->updateNextSyncTime();
    $source->updateSyncStatus('idle');

    // Summary
    echo "\n" . str_repeat('=', 60) . "\n";
    logInfo(colorize("SYNC SUMMARY", 'blue'));
    echo str_repeat('=', 60) . "\n";
    echo "Total Added: " . colorize((string)$totalStats['added'], 'green') . "\n";
    echo "Total Updated: " . colorize((string)$totalStats['updated'], 'yellow') . "\n";
    echo "Total Deleted: " . colorize((string)$totalStats['deleted'], 'red') . "\n";
    echo "Errors: " . colorize((string)$totalStats['errors'], 'red') . "\n";

    if ($totalStats['errors'] > 0) {
        logWarning("Sync completed with errors");
        exit(1);
    }

    logSuccess("Sync completed successfully");
    exit(0);

} catch (Exception $e) {
    logError("Fatal error: " . $e->getMessage());
    logInfo("Stack trace: " . $e->getTraceAsString(), true);
    exit(1);
}
